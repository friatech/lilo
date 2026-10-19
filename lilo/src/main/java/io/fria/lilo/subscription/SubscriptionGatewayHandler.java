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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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
  private static final String INVALID_PAYLOAD = "Subscribe payload is not a valid GraphQL request";
  private static final String MISSING_QUERY = "Subscribe payload has no query";
  private static final String EXECUTION_FAILED = "Subscription could not be executed";
  private static final String NOT_A_SUBSCRIPTION = "Operation did not produce a subscription";
  private static final String TOO_MANY_REQUESTS = "Too many concurrent subscription requests";
  private static final String TOO_MANY_OPERATIONS = "Too many subscriptions on this connection";

  /** Default cap on the operations one session may have registered at the same time. */
  private static final int DEFAULT_MAX_OPERATIONS_PER_SESSION = 50;

  private static final int POOL_CORE_SIZE = Math.max(4, Runtime.getRuntime().availableProcessors());
  private static final int POOL_MAX_SIZE = 4 * POOL_CORE_SIZE;
  private static final int POOL_QUEUE_CAPACITY = 256;
  private static final long POOL_KEEP_ALIVE_SECONDS = 60;

  private static final Logger LOG = LoggerFactory.getLogger(SubscriptionGatewayHandler.class);
  private static final AtomicInteger THREAD_COUNT = new AtomicInteger();

  private final @NotNull Lilo lilo;
  private final @NotNull Executor executor;
  private final int maxOperationsPerSession;

  /** session id -> the session's operations and in-flight stitches */
  private final @NotNull Map<String, SessionState> sessionOperations = new ConcurrentHashMap<>();

  /** session id -> what the client sent at connection_init; present once the session is open */
  private final @NotNull Map<String, SubscriptionConnectionContext> connections =
      new ConcurrentHashMap<>();

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
    this(lilo, newDefaultExecutor());
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

    this.lilo = Objects.requireNonNull(lilo);
    this.executor = Objects.requireNonNull(executor);
    this.maxOperationsPerSession = maxOperationsPerSession;
  }

  private static @NotNull Executor newDefaultExecutor() {
    final ThreadPoolExecutor pool =
        new ThreadPoolExecutor(
            POOL_CORE_SIZE,
            POOL_MAX_SIZE,
            POOL_KEEP_ALIVE_SECONDS,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(POOL_QUEUE_CAPACITY),
            SubscriptionGatewayHandler::newThread);
    pool.allowCoreThreadTimeOut(true);
    return pool;
  }

  private static @NotNull String safe(final @Nullable String text) {
    return SubscriptionSourceException.bound(String.valueOf(text));
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
      this.handleConnectionInit(session, request);
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

  /** Returns whether a connection context is held for the session. */
  boolean tracksConnection(final @NotNull String sessionId) {
    return this.connections.containsKey(sessionId);
  }

  private void handleConnectionInit(
      final @NotNull WebSocketSessionWrapper session, final @NotNull SubscriptionMessage request) {

    final SubscriptionConnectionContext context;
    try {
      context = SubscriptionConnectionContext.of(request.getPayload(), session.getAttributes());
    } catch (final RuntimeException e) {
      // e.g. attributes the transport cannot read; the values are not logged, they may be secret
      LOG.debug("Could not read the connection context of session {}", session.getId());
      this.closeSession(session);
      return;
    }

    if (this.connections.putIfAbsent(session.getId(), context) != null) {
      // graphql-transport-ws: a second connection_init closes the connection (4429 Too many
      // initialisation requests)
      this.closeSession(session);
      return;
    }

    if (!session.isOpen()) {
      // Closed meanwhile: its close may already have run, so do not keep the context
      this.connections.remove(session.getId(), context);
      return;
    }

    final var response =
        SubscriptionMessage.builder().type(connection_ack).payload(new HashMap<>()).build();
    session.send(JsonUtils.toStr(response));
  }

  private @NotNull CompletableFuture<Void> handleSubscribe(
      final @NotNull WebSocketSessionWrapper session, final @NotNull SubscriptionMessage request) {

    final SubscriptionConnectionContext context = this.connections.get(session.getId());

    if (context == null) {
      // graphql-transport-ws: a subscribe before connection_init closes the connection (4401
      // Unauthorized)
      this.closeSession(session);
      return DONE;
    }

    final String operationId = request.getId();

    if (operationId == null) {
      // graphql-transport-ws: a subscribe must carry an id
      this.closeSession(session);
      return DONE;
    }

    final SessionState active = this.sessionOperations.get(session.getId());

    if (active != null && active.operations.containsKey(operationId)) {
      // graphql-transport-ws: a subscribe with an id that is already active closes the
      // connection (4409 Subscriber already exists)
      this.closeSession(session);
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

    final SessionState state =
        this.sessionOperations.computeIfAbsent(session.getId(), id -> new SessionState());
    final Map<String, Operation> operations = state.operations;
    final Operation operation = new Operation();

    // A session's messages arrive one after the other, so plain checks are enough here. A stitch
    // still running for an operation the client already completed holds a pool thread, so it
    // counts as well: otherwise subscribe followed by complete would bypass the cap. Every
    // in-flight stitch belongs to a registered operation unless that was completed, so the larger
    // of the two numbers is the session's real usage; their sum would count most operations twice.
    if (Math.max(operations.size(), state.inFlight.get()) >= this.maxOperationsPerSession) {
      trySendError(session, operationId, errors(TOO_MANY_OPERATIONS));
      return DONE;
    }

    if (operations.putIfAbsent(operationId, operation) != null) {
      // Lost a race against a concurrent subscribe with the same id
      this.closeSession(session);
      return DONE;
    }

    if (this.isDetached(session, state)) {
      // The session was closed meanwhile. Its close may already have drained this map before the
      // operation was added, so remove it here rather than rely on closeSession finding it.
      operations.remove(operationId, operation);
      this.closeSession(session);
      return DONE;
    }

    final CompletableFuture<ExecutionResult> stitch = new CompletableFuture<>();
    // Counted before submitting; released exactly once, when the stitch finished or never started
    state.inFlight.incrementAndGet();
    try {
      // Offloaded: with a cached schema the stitch runs the source's retriever inline
      this.executor.execute(() -> this.stitch(executionInput, stitch));
    } catch (final RuntimeException e) {
      state.inFlight.decrementAndGet();
      final boolean rejected = e instanceof RejectedExecutionException;
      LOG.warn(
          "Subscription {} not started: the stitching executor {}",
          safe(operationId),
          rejected ? "is saturated" : "failed: " + safe(e.toString()));
      LOG.debug("Stitching executor failure", e);
      if (operations.remove(operationId, operation)) {
        trySendError(session, operationId, errors(rejected ? TOO_MANY_REQUESTS : EXECUTION_FAILED));
      }
      return DONE;
    }

    return stitch.handle(
        (result, throwable) -> {
          state.inFlight.decrementAndGet();
          try {
            this.start(session, state, operation, operationId, result, throwable);
          } catch (final RuntimeException e) {
            LOG.warn(
                "Subscription {} could not be started: {}", safe(operationId), safe(e.toString()));
            LOG.debug("Subscription start failure", e);
            // Never leave a half started operation registered
            if (operations.remove(operationId, operation)) {
              operation.cancel();
              trySendError(session, operationId, errors(EXECUTION_FAILED));
            }
          }
          return null;
        });
  }

  /**
   * Runs on the executor: starts the stitch and passes its outcome on. The future returned by
   * whenComplete is ignored: its callback only completes {@code outcome} and cannot fail.
   */
  @SuppressWarnings("FutureReturnValueIgnored")
  private void stitch(
      final @NotNull ExecutionInput executionInput,
      final @NotNull CompletableFuture<ExecutionResult> outcome) {
    try {
      this.lilo
          .stitchAsync(executionInput)
          .whenComplete(
              (result, throwable) -> {
                if (throwable == null) {
                  outcome.complete(result);
                } else {
                  outcome.completeExceptionally(throwable);
                }
              });
    } catch (final Throwable e) {
      // Any failure, an Error included, must complete the outcome: otherwise the operation's
      // placeholder and its in-flight slot are never released. The outcome's handler logs it.
      outcome.completeExceptionally(e);

      if (e instanceof VirtualMachineError) {
        // Not swallowed: the JVM is in trouble, so let the executor thread see it
        throw (VirtualMachineError) e;
      }
    }
  }

  /** Runs when stitching a subscribe finished: starts forwarding or reports why it cannot. */
  private void start(
      final @NotNull WebSocketSessionWrapper session,
      final @NotNull SessionState state,
      final @NotNull Operation operation,
      final @NotNull String operationId,
      final @Nullable ExecutionResult result,
      final @Nullable Throwable throwable) {

    final Map<String, Operation> operations = state.operations;

    if (throwable != null || result == null) {
      // The failure may be triggered by the client's query: no stack trace at warn
      LOG.warn(
          "Subscription {} could not be executed: {}",
          safe(operationId),
          safe(String.valueOf(throwable)));
      LOG.debug("Subscription execution failure", throwable);
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
                      LOG.debug("Could not send complete for {}", safe(operationId), sendFailure);
                    } finally {
                      operation.cancel();
                    }
                  }
                });

    operation.setSubscription(subscription);

    if (this.isDetached(session, state)) {
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
      trySendError(session, operationId, errors);
    }
  }

  private static void trySendError(
      final @NotNull WebSocketSessionWrapper session,
      final @NotNull String operationId,
      final @NotNull List<Map<String, Object>> errors) {
    try {
      sendError(session, operationId, errors);
    } catch (final RuntimeException e) {
      LOG.debug("Could not send error for {}", safe(operationId), e);
    }
  }

  /** Whether the session closed or its operation map is no longer the live one. */
  private boolean isDetached(
      final @NotNull WebSocketSessionWrapper session, final @NotNull SessionState state) {
    return !session.isOpen() || this.sessionOperations.get(session.getId()) != state;
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

    this.connections.remove(session.getId());
    final SessionState state = this.sessionOperations.remove(session.getId());

    if (state == null) {
      return;
    }

    final Map<String, Operation> operations = state.operations;

    for (final String operationId : operations.keySet()) {
      final Operation operation = operations.remove(operationId);
      if (operation != null) {
        operation.cancel();
      }
    }
  }

  private void cancelOperation(final @NotNull String sessionId, final @NotNull String operationId) {

    final SessionState state = this.sessionOperations.get(sessionId);

    if (state == null) {
      return;
    }

    // Whoever removes the operation owns its cleanup, so a late complete or close is a no-op
    final Operation operation = state.operations.remove(operationId);

    if (operation != null) {
      operation.cancel();
    }
  }

  /** Per-session state: the registered operations and the stitches still running for them. */
  private static final class SessionState {

    /** operation id -> operation */
    private final @NotNull Map<String, Operation> operations = new ConcurrentHashMap<>();

    /** Stitches submitted and not yet finished, including those of completed operations. */
    private final @NotNull AtomicInteger inFlight = new AtomicInteger();
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
