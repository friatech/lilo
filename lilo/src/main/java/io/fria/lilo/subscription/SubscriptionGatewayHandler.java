/*
 * Copyright 2023 the original author or authors.
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

/**
 * SubscriptionGatewayHandler manages websocket communication and handles the interaction between
 * Lilo itself.
 */
public class SubscriptionGatewayHandler {

  private final @NotNull Lilo lilo;
  private final @NotNull Map<String, SubscriptionSourcePublisher> sessionPublishers =
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
      final @Nullable WebSocketSessionWrapper session, final @NotNull String message) {

    Objects.requireNonNull(session);

    final Optional<SubscriptionMessage> requestOptional =
        JsonUtils.toObj(message, SubscriptionMessage.class);

    if (requestOptional.isEmpty()) {
      return;
    }

    final SubscriptionMessage request = requestOptional.get();
    final SubscriptionMessageType requestType = request.getType();

    if (connection_init == requestType) {
      // Emulating new connection
      final var response =
          SubscriptionMessage.builder().type(connection_ack).payload(new HashMap<>()).build();
      session.send(JsonUtils.toStr(response));
    } else if (subscribe == requestType) {
      final GraphQLRequest graphQLRequest = this.payloadToQuery(request.getPayload());
      final Map<String, Object> stitchResult =
          this.lilo.stitch(graphQLRequest.toExecutionInput()).toSpecification();

      this.subscribe(
          session,
          (SubscriptionPublisher) stitchResult.get("data"),
          Objects.requireNonNull(request.getId()));
    } else if (complete == requestType) {
      // This is when client requests session close
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

    final String sessionId = session.getId();

    if (session.isOpen()) {
      session.close();
    }

    if (this.sessionPublishers.containsKey(sessionId)) {
      final SubscriptionSourcePublisher publisher = this.sessionPublishers.get(sessionId);
      publisher.close();
      this.sessionPublishers.remove(sessionId);
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
      final @NotNull SubscriptionPublisher publisher,
      final @NotNull String requestId) {

    final SubscriptionSourcePublisher upstream =
        (SubscriptionSourcePublisher) publisher.getUpstreamPublisher();

    this.sessionPublishers.put(session.getId(), upstream);

    upstream
        .getFlux()
        .doOnComplete(
            () -> {
              // When gateway - remote connection disconnects
              final SubscriptionMessage completeMessage = new SubscriptionMessage();
              completeMessage.setId(requestId);
              completeMessage.setType(complete);

              Objects.requireNonNull(session).send(JsonUtils.toStr(completeMessage));

              SubscriptionGatewayHandler.this.closeSession(session);
            })
        .subscribe(
            payload -> {
              final var subscriptionMessage =
                  SubscriptionMessage.builder().id(requestId).type(next).payload(payload).build();
              session.send(JsonUtils.toStr(subscriptionMessage));
            });
  }
}
