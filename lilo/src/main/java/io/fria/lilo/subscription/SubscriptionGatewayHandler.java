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
import static io.fria.lilo.subscription.SubscriptionMessageType.error;
import static io.fria.lilo.subscription.SubscriptionMessageType.next;
import static io.fria.lilo.subscription.SubscriptionMessageType.ping;
import static io.fria.lilo.subscription.SubscriptionMessageType.pong;
import static io.fria.lilo.subscription.SubscriptionMessageType.subscribe;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import graphql.execution.reactive.SubscriptionPublisher;
import io.fria.lilo.GraphQLRequest;
import io.fria.lilo.JsonUtils;
import io.fria.lilo.Lilo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;

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
 */
public final class SubscriptionGatewayHandler {

  private static final CompletableFuture<Void> DONE = CompletableFuture.completedFuture(null);
  private static final String INVALID_PAYLOAD = "Subscribe payload is not a valid GraphQL request";
  private static final String MISSING_QUERY = "Subscribe payload has no query";
  private static final String EXECUTION_FAILED = "Subscription could not be executed";
  private static final String NOT_A_SUBSCRIPTION = "Operation did not produce a subscription";

  private static final Logger LOG = LoggerFactory.getLogger(SubscriptionGatewayHandler.class);
  private static final AtomicInteger THREAD_COUNT = new AtomicInteger();

  private final @NotNull Lilo lilo;
  private final @NotNull Executor executor;

  /** session id -> (operation id -> operation) */
  private final @NotNull Map<String, Map<String, Operation>> sessionOperations =
      new ConcurrentHashMap<>();

  /**
   * Constructs a handler instance using lilo. Subscriptions are stitched on a cached pool of daemon
   * threads named {@code lilo-subscription-N} that the handler owns, so a source that is slow to
   * connect never blocks the websocket thread that delivered the {@code subscribe}.
   *
   * @param lilo global lilo instance
   */
  public SubscriptionGatewayHandler(final @NotNull Lilo lilo) {
    this(lilo, Executors.newCachedThreadPool(SubscriptionGatewayHandler::newThread));
  }

  /**
   * Constructs a handler instance using lilo that stitches subscriptions on the given executor. The
   * stitch runs the source's {@link SubscriptionRetriever}, which may block while it connects to
   * the source, so the executor should not be the websocket thread or a small shared pool.
   *
   * @param lilo global lilo instance
   * @param executor executor that runs the stitching of each {@code subscribe}
   */
  public SubscriptionGatewayHandler(final @NotNull Lilo lilo, final @NotNull Executor executor) {
    this.lilo = Objects.requireNonNull(lilo);
    this.executor = Objects.requireNonNull(executor);
  }

  private static @NotNull Thread newThread(final @NotNull Runnable runnable) {
    final Thread thread =
        new Thread(runnable, "lilo-subscription-" + THREAD_COUNT.incrementAndGet());
    thread.setDaemon(true);
    return thread;
  }

  private static @NotNull List<Map<String, Object>> errors(final @NotNull String message) {
    return List.of(Map.of("message", message));
  }

  private static @NotNull List<Map<String, Object>> errorsOf(final @NotNull Throwable throwable) {
    if (throwable instanceof SubscriptionSourceException) {
      return ((SubscriptionSourceException) throwable).getErrors();
    }
    return SubscriptionSourceException.genericErrors();
  }

  private static @Nullable SubscriptionSourcePublisher upstreamOf(final @Nullable Object data) {

    if (!(data instanceof SubscriptionPublisher)) {
      return null;
    }

    final Object upstream = ((SubscriptionPublisher) data).getUpstreamPublisher();
    return upstream instanceof SubscriptionSourcePublisher
        ? (SubscriptionSourcePublisher) upstream
        : null;
  }

  private static void sendError(
      final @NotNull WebSocketSessionWrapper session,
      final @NotNull String operationId,
      final @NotNull List<Map<String, Object>> errors) {

    final SubscriptionMessage errorMessage =
        SubscriptionMessage.builder().id(operationId).type(error).payload(errors).build();
    session.send(JsonUtils.toStr(errorMessage));
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
    this.closeSession(session);
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
      this.closeSession(session);
      return DONE;
    }

    if (request == null || request.getType() == null) {
      this.closeSession(session);
      return DONE;
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
      return this.handleSubscribe(session, request);
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

    return DONE;
  }

  /** Returns whether any state is held for the session. */
  boolean tracksSession(final @NotNull String sessionId) {
    return this.sessionOperations.containsKey(sessionId);
  }

  private @NotNull CompletableFuture<Void> handleSubscribe(
      final @NotNull WebSocketSessionWrapper session, final @NotNull SubscriptionMessage request) {

    final String operationId = request.getId();

    if (operationId == null) {
      // graphql-transport-ws: a subscribe must carry an id
      this.closeSession(session);
      return DONE;
    }

    final Map<String, Operation> active = this.sessionOperations.get(session.getId());

    if (active != null && active.containsKey(operationId)) {
      // graphql-transport-ws: a subscribe with an id that is already active closes the
      // connection (4409 Subscriber already exists)
      this.closeSession(session);
      return DONE;
    }

    // Validated before any per-session state is created, so a rejected request leaves none
    final Object payload = request.getPayload();

    if (!(payload instanceof Map)) {
      sendError(session, operationId, errors(INVALID_PAYLOAD));
      return DONE;
    }

    if (!(((Map<?, ?>) payload).get("query") instanceof String)) {
      sendError(session, operationId, errors(MISSING_QUERY));
      return DONE;
    }

    final ExecutionInput executionInput;
    try {
      final GraphQLRequest graphQLRequest =
          JsonUtils.toObj(JsonUtils.toStr(payload), GraphQLRequest.class).orElse(null);

      if (graphQLRequest == null) {
        sendError(session, operationId, errors(INVALID_PAYLOAD));
        return DONE;
      }

      executionInput = graphQLRequest.toExecutionInput();
    } catch (final RuntimeException e) {
      // e.g. a blank query or variables of the wrong shape
      sendError(session, operationId, errors(INVALID_PAYLOAD));
      return DONE;
    }

    final Map<String, Operation> operations =
        this.sessionOperations.computeIfAbsent(session.getId(), id -> new ConcurrentHashMap<>());
    final Operation operation = new Operation();

    if (operations.putIfAbsent(operationId, operation) != null) {
      // Lost a race against a concurrent subscribe with the same id
      this.closeSession(session);
      return DONE;
    }

    if (this.isDetached(session, operations)) {
      // The session was closed meanwhile. Its close may already have drained this map before the
      // operation was added, so remove it here rather than rely on closeSession finding it.
      operations.remove(operationId, operation);
      this.closeSession(session);
      return DONE;
    }

    CompletableFuture<ExecutionResult> stitch;
    try {
      // Offloaded: with a cached schema the stitch runs the source's retriever inline
      stitch =
          DONE.thenComposeAsync(unused -> this.lilo.stitchAsync(executionInput), this.executor);
    } catch (final RuntimeException e) {
      stitch = CompletableFuture.failedFuture(e);
    }

    return stitch.handle(
        (result, throwable) -> {
          try {
            this.start(session, operations, operation, operationId, result, throwable);
          } catch (final RuntimeException e) {
            LOG.warn("Subscription {} could not be started", operationId, e);
            // Never leave a half started operation registered
            if (operations.remove(operationId, operation)) {
              operation.cancel();
              trySendError(session, operationId, errors(EXECUTION_FAILED));
            }
          }
          return null;
        });
  }

  /** Runs when stitching a subscribe finished: starts forwarding or reports why it cannot. */
  private void start(
      final @NotNull WebSocketSessionWrapper session,
      final @NotNull Map<String, Operation> operations,
      final @NotNull Operation operation,
      final @NotNull String operationId,
      final @Nullable ExecutionResult result,
      final @Nullable Throwable throwable) {

    if (throwable != null || result == null) {
      this.fail(session, operations, operation, operationId, errors(EXECUTION_FAILED));
      return;
    }

    final Object data = result.getData();
    final SubscriptionSourcePublisher upstream = upstreamOf(data);

    if (!result.getErrors().isEmpty()) {
      if (upstream != null) {
        upstream.close();
      }

      final List<Map<String, Object>> errors =
          result.getErrors().stream()
              .map(GraphQLError::toSpecification)
              .collect(Collectors.toList());
      this.fail(session, operations, operation, operationId, errors);
      return;
    }

    if (upstream == null) {
      this.fail(session, operations, operation, operationId, errors(NOT_A_SUBSCRIPTION));
      return;
    }

    if (!operation.attach(upstream)) {
      // Cancelled by the client or a session close while stitching: nothing is sent
      upstream.close();
      return;
    }

    final Disposable subscription =
        upstream
            .getFlux()
            .subscribe(
                payload -> {
                  // Nothing follows a client complete, even an event already in flight
                  if (operations.get(operationId) != operation) {
                    return;
                  }

                  final var subscriptionMessage =
                      SubscriptionMessage.builder()
                          .id(operationId)
                          .type(next)
                          .payload(payload)
                          .build();
                  session.send(JsonUtils.toStr(subscriptionMessage));
                },
                e -> {
                  // The source failed this operation. Only report it if it was still active.
                  // This also runs when sending a next to the client failed.
                  if (operations.remove(operationId, operation)) {
                    try {
                      trySendError(session, operationId, errorsOf(e));
                    } finally {
                      // Releases the source in every case
                      operation.cancel();
                    }
                  }
                },
                () -> {
                  // The source finished this operation. Only report it if the operation was still
                  // active, i.e. neither the client nor a session close cancelled it meanwhile.
                  if (operations.remove(operationId, operation)) {
                    final SubscriptionMessage completeMessage = new SubscriptionMessage();
                    completeMessage.setId(operationId);
                    completeMessage.setType(complete);

                    try {
                      session.send(JsonUtils.toStr(completeMessage));
                    } catch (final RuntimeException sendFailure) {
                      LOG.debug("Could not send complete for {}", operationId, sendFailure);
                    } finally {
                      operation.cancel();
                    }
                  }
                });

    operation.setSubscription(subscription);

    if (this.isDetached(session, operations)) {
      // Defensive: registration already checked this, but a stale map must never keep forwarding
      if (operations.remove(operationId, operation)) {
        operation.cancel();
      }
      this.closeSession(session);
    }
  }

  /** Reports an operation that could not start, unless it was cancelled meanwhile. */
  private void fail(
      final @NotNull WebSocketSessionWrapper session,
      final @NotNull Map<String, Operation> operations,
      final @NotNull Operation operation,
      final @NotNull String operationId,
      final @NotNull List<Map<String, Object>> errors) {

    if (operations.remove(operationId, operation)) {
      sendError(session, operationId, errors);
    }
  }

  private static void trySendError(
      final @NotNull WebSocketSessionWrapper session,
      final @NotNull String operationId,
      final @NotNull List<Map<String, Object>> errors) {
    try {
      sendError(session, operationId, errors);
    } catch (final RuntimeException e) {
      LOG.debug("Could not send error for {}", operationId, e);
    }
  }

  /** Whether the session closed or its operation map is no longer the live one. */
  private boolean isDetached(
      final @NotNull WebSocketSessionWrapper session,
      final @NotNull Map<String, Operation> operations) {
    return !session.isOpen() || this.sessionOperations.get(session.getId()) != operations;
  }

  private void closeSession(final @NotNull WebSocketSessionWrapper session) {

    try {
      if (session.isOpen()) {
        session.close();
      }
    } catch (final RuntimeException e) {
      // The operations below must be released all the same
      LOG.debug("Could not close session {}", session.getId(), e);
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

  /**
   * One subscription operation. It is registered before stitching, without an upstream; the
   * upstream and the gateway's subscription to it are attached once stitching finishes. A cancel
   * may land at any point: before the upstream is attached (the attach then fails and the caller
   * closes the upstream), or after (the upstream is closed here).
   */
  private static final class Operation {

    private @Nullable SubscriptionSourcePublisher upstream;
    private @Nullable Disposable subscription;
    private boolean cancelled;

    /**
     * Attaches the upstream the stitch produced.
     *
     * @return false if the operation was cancelled meanwhile; the caller then owns the upstream
     */
    private synchronized boolean attach(final @NotNull SubscriptionSourcePublisher source) {
      if (this.cancelled) {
        return false;
      }
      this.upstream = source;
      return true;
    }

    private void setSubscription(final @NotNull Disposable disposable) {
      final boolean dispose;
      synchronized (this) {
        dispose = this.cancelled;
        if (!dispose) {
          this.subscription = disposable;
        }
      }
      if (dispose) {
        disposable.dispose();
      }
    }

    /** Stops forwarding to the client, then tells the source the operation is over. */
    private void cancel() {
      final Disposable currentSubscription;
      final SubscriptionSourcePublisher currentUpstream;
      synchronized (this) {
        if (this.cancelled) {
          return;
        }
        this.cancelled = true;
        currentSubscription = this.subscription;
        currentUpstream = this.upstream;
      }
      // Neither step may throw: closeSession cancels the session's other operations afterwards
      try {
        if (currentSubscription != null) {
          currentSubscription.dispose();
        }
      } catch (final RuntimeException e) {
        LOG.warn("Could not stop forwarding a subscription", e);
      }
      try {
        if (currentUpstream != null) {
          currentUpstream.close();
        }
      } catch (final RuntimeException e) {
        LOG.warn("Could not close a subscription source", e);
      }
    }
  }
}
