/*
 * Copyright 2022-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fria.lilo.subscription;

import static io.fria.lilo.subscription.SubscriptionMessageType.complete;
import static io.fria.lilo.subscription.SubscriptionMessageType.connection_ack;
import static io.fria.lilo.subscription.SubscriptionMessageType.connection_init;
import static io.fria.lilo.subscription.SubscriptionMessageType.next;
import static io.fria.lilo.subscription.SubscriptionMessageType.ping;
import static io.fria.lilo.subscription.SubscriptionMessageType.pong;
import static io.fria.lilo.subscription.SubscriptionMessageType.subscribe;

import graphql.execution.reactive.SubscriptionPublisher;
import io.fria.lilo.GraphQLRequest;
import io.fria.lilo.JsonUtils;
import io.fria.lilo.Lilo;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.Disposable;

/**
 * SubscriptionGatewayHandler manages websocket communication and handles the interaction between
 * Lilo itself.
 *
 * <p>A single websocket session multiplexes many operations, each identified by the id of its
 * {@code subscribe} message. Operations are tracked per session and per operation id; completing
 * one operation leaves the others and the socket untouched. The socket is only closed when the
 * client closes it or violates the protocol.
 */
public final class SubscriptionGatewayHandler {

  private final @NotNull Lilo lilo;

  /** session id -> (operation id -> operation) */
  private final @NotNull Map<String, Map<String, Operation>> sessionOperations =
      new ConcurrentHashMap<>();

  /**
   * Constructs a handler instance using lilo
   *
   * @param lilo global lilo instance
   */
  public SubscriptionGatewayHandler(final @NotNull Lilo lilo) {
    this.lilo = Objects.requireNonNull(lilo);
  }

  public void handleMessage(
      final @NotNull WebSocketSessionWrapper session, final @NotNull String message) {

    Objects.requireNonNull(session);

    final SubscriptionMessage request;
    try {
      request = JsonUtils.toObj(message, SubscriptionMessage.class).orElse(null);
    } catch (final IllegalArgumentException e) {
      // graphql-transport-ws: an invalid or unknown message closes the connection
      this.closeSession(session);
      return;
    }

    if (request == null || request.getType() == null) {
      this.closeSession(session);
      return;
    }

    final SubscriptionMessageType requestType = request.getType();

    if (connection_init == requestType) {
      // Emulating new connection
      final var response =
          SubscriptionMessage.builder().type(connection_ack).payload(new HashMap<>()).build();
      session.send(JsonUtils.toStr(response));
    } else if (ping == requestType) {
      // Echo the ping payload back, if any
      final SubscriptionMessage response = new SubscriptionMessage();
      response.setType(pong);
      response.setPayload(request.getPayload());
      session.send(JsonUtils.toStr(response));
    } else if (subscribe == requestType) {
      final String operationId = Objects.requireNonNull(request.getId());
      // Taken before stitching: a close that lands while stitching detaches this map, which the
      // registration in subscribe detects
      final Map<String, Operation> operations =
          this.sessionOperations.computeIfAbsent(session.getId(), id -> new ConcurrentHashMap<>());

      if (operations.containsKey(operationId)) {
        // graphql-transport-ws: a subscribe with an id that is already active closes the
        // connection (4409 Subscriber already exists)
        this.closeSession(session);
        return;
      }

      final GraphQLRequest graphQLRequest = this.payloadToQuery(request.getPayload());
      final Map<String, Object> stitchResult =
          this.lilo.stitch(graphQLRequest.toExecutionInput()).toSpecification();

      this.subscribe(
          session, operations, (SubscriptionPublisher) stitchResult.get("data"), operationId);
    } else if (complete == requestType) {
      // The client stops a single operation; the connection and other operations stay
      final String operationId = request.getId();
      if (operationId != null) {
        this.cancelOperation(session.getId(), operationId);
      }
    } else if (pong != requestType) {
      // pong is a one-way heartbeat answer; server-to-client only types (connection_ack, next,
      // error) are invalid from a client
      this.closeSession(session);
    }
  }

  public void handleSessionClose(final @Nullable WebSocketSessionWrapper session) {

    if (session == null) {
      return;
    }

    // This is when graphql client ungracefully shutdowns
    this.closeSession(session);
  }

  private void closeSession(final @NotNull WebSocketSessionWrapper session) {

    if (session.isOpen()) {
      session.close();
    }

    final Map<String, Operation> operations = this.sessionOperations.remove(session.getId());

    if (operations == null) {
      return;
    }

    for (final String operationId : operations.keySet()) {
      final Operation operation = operations.remove(operationId);
      if (operation != null) {
        operation.cancel();
      }
    }
  }

  private void cancelOperation(final @NotNull String sessionId, final @NotNull String operationId) {

    final Map<String, Operation> operations = this.sessionOperations.get(sessionId);

    if (operations == null) {
      return;
    }

    // Whoever removes the operation owns its cleanup, so a late complete or close is a no-op
    final Operation operation = operations.remove(operationId);

    if (operation != null) {
      operation.cancel();
    }
  }

  private GraphQLRequest payloadToQuery(final Object payload) {

    final Optional<GraphQLRequest> graphQLRequest =
        JsonUtils.toObj(JsonUtils.toStr(payload), GraphQLRequest.class);

    if (graphQLRequest.isEmpty()) {
      return null;
    }

    return graphQLRequest.get();
  }

  private void subscribe(
      final @NotNull WebSocketSessionWrapper session,
      final @NotNull Map<String, Operation> operations,
      final @NotNull SubscriptionPublisher publisher,
      final @NotNull String operationId) {

    final SubscriptionSourcePublisher upstream =
        (SubscriptionSourcePublisher) publisher.getUpstreamPublisher();
    final Operation operation = new Operation(upstream);

    if (operations.putIfAbsent(operationId, operation) != null) {
      // Lost a race against a concurrent subscribe with the same id
      upstream.close();
      this.closeSession(session);
      return;
    }

    final Disposable subscription =
        upstream
            .getFlux()
            .doOnComplete(
                () -> {
                  // The source finished this operation. Only report it if the operation was still
                  // active, i.e. neither the client nor a session close cancelled it meanwhile.
                  if (operations.remove(operationId, operation)) {
                    final SubscriptionMessage completeMessage = new SubscriptionMessage();
                    completeMessage.setId(operationId);
                    completeMessage.setType(complete);

                    session.send(JsonUtils.toStr(completeMessage));
                  }
                })
            .subscribe(
                payload -> {
                  final var subscriptionMessage =
                      SubscriptionMessage.builder()
                          .id(operationId)
                          .type(next)
                          .payload(payload)
                          .build();
                  session.send(JsonUtils.toStr(subscriptionMessage));
                });

    operation.setSubscription(subscription);

    if (!session.isOpen() || this.sessionOperations.get(session.getId()) != operations) {
      // The session was closed while subscribing. Its close may already have removed and drained
      // this map before the operation was added, so cancel the operation here rather than rely on
      // closeSession finding it.
      if (operations.remove(operationId, operation)) {
        operation.cancel();
      }
      this.closeSession(session);
    }
  }

  /** One active subscription operation: its upstream publisher and the gateway's subscription. */
  private static final class Operation {

    private final @NotNull SubscriptionSourcePublisher upstream;
    private volatile @Nullable Disposable subscription;
    private volatile boolean cancelled;

    private Operation(final @NotNull SubscriptionSourcePublisher upstream) {
      this.upstream = upstream;
    }

    private void setSubscription(final @NotNull Disposable subscription) {
      this.subscription = subscription;
      if (this.cancelled) {
        subscription.dispose();
      }
    }

    /** Stops forwarding to the client, then tells the source the operation is over. */
    private void cancel() {
      this.cancelled = true;
      final Disposable current = this.subscription;
      if (current != null) {
        current.dispose();
      }
      this.upstream.close();
    }
  }
}
