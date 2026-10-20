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

import io.fria.lilo.Lilo;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  /** Default cap on the operations one session may have registered at the same time. */
  private static final int DEFAULT_MAX_OPERATIONS_PER_SESSION = 50;

  private final @NotNull GatewaySessionRegistry registry;
  private final @NotNull GatewayProtocol protocol;

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
    this(lilo, StitchExecutors.newDefault());
  }

  /**
   * Constructs a handler instance using lilo that stitches subscriptions on its own default pool,
   * as {@link #SubscriptionGatewayHandler(Lilo)} does, and limits the operations of each session. A
   * subscribe beyond the limit is answered with an {@code error} message and the session stays
   * open.
   *
   * @param lilo global lilo instance
   * @param maxOperationsPerSession most operations one session may have at the same time (default
   *     50), greater than zero
   */
  public SubscriptionGatewayHandler(final @NotNull Lilo lilo, final int maxOperationsPerSession) {
    this(lilo, StitchExecutors.newDefault(), maxOperationsPerSession);
  }

  /**
   * Creates a stitch pool shaped like the one {@link #SubscriptionGatewayHandler(Lilo)} owns, with
   * the given number of core threads: daemon threads named {@code lilo-subscription-N}, growing to
   * four times the core size, with up to 256 queued requests, and idle threads ending after 60
   * seconds. Pass it to {@link #SubscriptionGatewayHandler(Lilo, Executor)} or {@link
   * #SubscriptionGatewayHandler(Lilo, Executor, int)} to size the pool.
   *
   * @param coreSize core threads of the pool (by default max(4, available processors)), greater
   *     than zero
   * @return a new pool; it is not shut down by the handler, its idle threads end on their own
   */
  public static @NotNull Executor defaultExecutor(final int coreSize) {
    return StitchExecutors.newDefault(coreSize);
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
    final SubscriptionStitcher stitcher =
        new SubscriptionStitcher(
            Objects.requireNonNull(lilo), Objects.requireNonNull(executor), this.registry);
    this.protocol = new GatewayProtocol(this.registry, stitcher);
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
    return this.protocol.handle(session, message);
  }

  /** Returns whether any state is held for the session. */
  boolean tracksSession(final @NotNull String sessionId) {
    return this.registry.tracksSession(sessionId);
  }

  /** Returns whether a connection context is held for the session. */
  boolean tracksConnection(final @NotNull String sessionId) {
    return this.registry.tracksConnection(sessionId);
  }
}
