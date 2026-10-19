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

import static io.fria.lilo.subscription.GatewayMessages.INVALID_PAYLOAD;
import static io.fria.lilo.subscription.GatewayMessages.MISSING_QUERY;
import static io.fria.lilo.subscription.GatewayMessages.errors;
import static io.fria.lilo.subscription.GatewayMessages.sendConnectionAck;
import static io.fria.lilo.subscription.GatewayMessages.sendPong;
import static io.fria.lilo.subscription.GatewayMessages.trySendError;
import static io.fria.lilo.subscription.SubscriptionMessageType.complete;
import static io.fria.lilo.subscription.SubscriptionMessageType.connection_init;
import static io.fria.lilo.subscription.SubscriptionMessageType.ping;
import static io.fria.lilo.subscription.SubscriptionMessageType.pong;
import static io.fria.lilo.subscription.SubscriptionMessageType.subscribe;

import graphql.ExecutionInput;
import io.fria.lilo.GraphQLRequest;
import io.fria.lilo.JsonUtils;
import io.fria.lilo.Lilo;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SubscriptionGatewayHandler manages websocket communication and handles the interaction between
 * Lilo itself.
 *
 * <p>A single websocket session multiplexes many operations, each identified by the id of its
 * {@code subscribe} message. Operations are tracked per session and per operation id; completing
 * one operation leaves the others and the socket untouched. The socket is only closed when the
 * client closes it or violates the protocol.
 *
 * <p>A {@code subscribe} is stitched asynchronously. Its operation is registered before stitching
 * starts, so a {@code complete} or a session close that arrives meanwhile cancels it; the upstream
 * the stitch produces is then closed and nothing is sent for the operation. A subscribe that cannot
 * be started (an invalid payload, a query that fails validation, an operation that is not a
 * subscription) is answered with an {@code error} message for its id and the socket stays open.
 *
 * <p>A session must start with exactly one {@code connection_init}. Its payload and the session's
 * handshake headers are kept as a {@link SubscriptionConnectionContext} for as long as the session
 * lives, and every subscription of the session is stitched with that context as its {@code
 * localContext}, so a {@link SubscriptionRetriever} can forward the client's credentials to the
 * source. A {@code subscribe} before {@code connection_init}, or a second {@code connection_init},
 * closes the session.
 */
public final class SubscriptionGatewayHandler {

  private static final CompletableFuture<Void> DONE = CompletableFuture.completedFuture(null);

  /** Default cap on the operations one session may have registered at the same time. */
  private static final int DEFAULT_MAX_OPERATIONS_PER_SESSION = 50;

  private static final Logger LOG = LoggerFactory.getLogger(SubscriptionGatewayHandler.class);

  private final @NotNull GatewaySessionRegistry registry;
  private final @NotNull SubscriptionStitcher stitcher;

  /**
   * Constructs a handler instance using lilo.
   *
   * <p>Subscriptions are stitched on a bounded pool of daemon threads named {@code
   * lilo-subscription-N} that the handler owns, so a source that is slow to connect never blocks
   * the websocket thread that delivered the {@code subscribe}. The pool has max(4, available
   * processors) core threads, grows to four times that, and queues up to 256 more requests; a
   * subscribe beyond that is answered with an {@code error} message. A session may have at most 50
   * operations at the same time. Both limits keep clients from exhausting threads and memory.
   *
   * @param lilo global lilo instance
   */
  public SubscriptionGatewayHandler(final @NotNull Lilo lilo) {
    this(lilo, SubscriptionStitcher.newDefaultExecutor());
  }

  /**
   * Constructs a handler instance using lilo that stitches subscriptions on the given executor. The
   * stitch runs the source's {@link SubscriptionRetriever}, which may block while it connects to
   * the source, so the executor should not be the websocket thread or a small shared pool. It
   * should be bounded: a subscribe it rejects is answered with an {@code error} message. A session
   * may have at most 50 operations at the same time.
   *
   * @param lilo global lilo instance
   * @param executor executor that runs the stitching of each {@code subscribe}
   */
  public SubscriptionGatewayHandler(final @NotNull Lilo lilo, final @NotNull Executor executor) {
    this(lilo, executor, DEFAULT_MAX_OPERATIONS_PER_SESSION);
  }

  /**
   * Constructs a handler instance using lilo that stitches subscriptions on the given executor and
   * limits the operations of each session. A subscribe beyond the limit is answered with an {@code
   * error} message and the session stays open. The limit keeps one client from exhausting the
   * gateway's and the sources' resources.
   *
   * @param lilo global lilo instance
   * @param executor executor that runs the stitching of each {@code subscribe}; see {@link
   *     #SubscriptionGatewayHandler(Lilo, Executor)}
   * @param maxOperationsPerSession most operations one session may have at the same time (default
   *     50), greater than zero
   */
  public SubscriptionGatewayHandler(
      final @NotNull Lilo lilo,
      final @NotNull Executor executor,
      final int maxOperationsPerSession) {

    if (maxOperationsPerSession <= 0) {
      throw new IllegalArgumentException("maxOperationsPerSession must be greater than zero");
    }

    this.registry = new GatewaySessionRegistry(maxOperationsPerSession);
    this.stitcher =
        new SubscriptionStitcher(
            Objects.requireNonNull(lilo), Objects.requireNonNull(executor), this.registry);
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  public void handleMessage(
      final @NotNull WebSocketSessionWrapper session, final @NotNull String message) {
    // Only tests wait for a subscribe to be registered; the socket thread does not
    this.handle(session, message);
  }

  public void handleSessionClose(final @Nullable WebSocketSessionWrapper session) {

    if (session == null) {
      return;
    }

    // This is when graphql client ungracefully shutdowns
    this.registry.closeSession(session);
  }

  /**
   * Handles a client message.
   *
   * @return completes once the message is handled; for a subscribe, once its operation is running
   *     or was rejected
   */
  @NotNull
  CompletableFuture<Void> handle(
      final @NotNull WebSocketSessionWrapper session, final @NotNull String message) {

    Objects.requireNonNull(session);

    final SubscriptionMessage request;
    try {
      request = JsonUtils.toObj(message, SubscriptionMessage.class).orElse(null);
    } catch (final IllegalArgumentException e) {
      // graphql-transport-ws: an invalid or unknown message closes the connection
      this.registry.closeSession(session);
      return DONE;
    }

    if (request == null || request.getType() == null) {
      this.registry.closeSession(session);
      return DONE;
    }

    final SubscriptionMessageType requestType = request.getType();

    if (connection_init == requestType) {
      this.handleConnectionInit(session, request);
    } else if (ping == requestType) {
      sendPong(session, request.getPayload());
    } else if (subscribe == requestType) {
      return this.handleSubscribe(session, request);
    } else if (complete == requestType) {
      // The client stops a single operation; the connection and other operations stay
      final String operationId = request.getId();
      if (operationId != null) {
        this.registry.cancelOperation(session.getId(), operationId);
      }
    } else if (pong != requestType) {
      // pong is a one-way heartbeat answer; server-to-client only types (connection_ack, next,
      // error) are invalid from a client
      this.registry.closeSession(session);
    }

    return DONE;
  }

  /** Returns whether any state is held for the session. */
  boolean tracksSession(final @NotNull String sessionId) {
    return this.registry.tracksSession(sessionId);
  }

  /** Returns whether a connection context is held for the session. */
  boolean tracksConnection(final @NotNull String sessionId) {
    return this.registry.tracksConnection(sessionId);
  }

  private void handleConnectionInit(
      final @NotNull WebSocketSessionWrapper session, final @NotNull SubscriptionMessage request) {

    final SubscriptionConnectionContext context;
    try {
      context = SubscriptionConnectionContext.of(request.getPayload(), session.getAttributes());
    } catch (final RuntimeException e) {
      // e.g. attributes the transport cannot read; the values are not logged, they may be secret
      LOG.debug("Could not read the connection context of session {}", session.getId());
      this.registry.closeSession(session);
      return;
    }

    if (!this.registry.addConnection(session.getId(), context)) {
      // graphql-transport-ws: a second connection_init closes the connection (4429 Too many
      // initialisation requests)
      this.registry.closeSession(session);
      return;
    }

    if (!session.isOpen()) {
      // Closed meanwhile: its close may already have run, so do not keep the context
      this.registry.removeConnection(session.getId(), context);
      return;
    }

    sendConnectionAck(session);
  }

  private @NotNull CompletableFuture<Void> handleSubscribe(
      final @NotNull WebSocketSessionWrapper session, final @NotNull SubscriptionMessage request) {

    final SubscriptionConnectionContext context = this.registry.connection(session.getId());

    if (context == null) {
      // graphql-transport-ws: a subscribe before connection_init closes the connection (4401
      // Unauthorized)
      this.registry.closeSession(session);
      return DONE;
    }

    final String operationId = request.getId();

    if (operationId == null) {
      // graphql-transport-ws: a subscribe must carry an id
      this.registry.closeSession(session);
      return DONE;
    }

    if (this.registry.hasOperation(session.getId(), operationId)) {
      // graphql-transport-ws: a subscribe with an id that is already active closes the
      // connection (4409 Subscriber already exists)
      this.registry.closeSession(session);
      return DONE;
    }

    // Validated before any per-session state is created, so a rejected request leaves none
    final Object payload = request.getPayload();

    if (!(payload instanceof Map)) {
      trySendError(session, operationId, errors(INVALID_PAYLOAD));
      return DONE;
    }

    if (!(((Map<?, ?>) payload).get("query") instanceof String)) {
      trySendError(session, operationId, errors(MISSING_QUERY));
      return DONE;
    }

    final ExecutionInput executionInput;
    try {
      final GraphQLRequest graphQLRequest =
          JsonUtils.toObj(JsonUtils.toStr(payload), GraphQLRequest.class).orElse(null);

      if (graphQLRequest == null) {
        trySendError(session, operationId, errors(INVALID_PAYLOAD));
        return DONE;
      }

      // The context reaches the source's SubscriptionRetriever as its localContext
      executionInput = graphQLRequest.toExecutionInput(context);
    } catch (final RuntimeException e) {
      // e.g. a blank query or variables of the wrong shape
      trySendError(session, operationId, errors(INVALID_PAYLOAD));
      return DONE;
    }

    final GatewayOperation operation = this.registry.register(session, operationId);

    if (operation == null) {
      return DONE;
    }

    return this.stitcher.submit(operation, executionInput);
  }
}
