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

import static io.fria.lilo.subscription.GatewayMessages.EXECUTION_FAILED;
import static io.fria.lilo.subscription.GatewayMessages.NOT_A_SUBSCRIPTION;
import static io.fria.lilo.subscription.GatewayMessages.TOO_MANY_REQUESTS;
import static io.fria.lilo.subscription.GatewayMessages.errors;
import static io.fria.lilo.subscription.GatewayMessages.errorsOf;
import static io.fria.lilo.subscription.GatewayMessages.logFailure;
import static io.fria.lilo.subscription.GatewayMessages.safe;
import static io.fria.lilo.subscription.GatewayMessages.sendNext;
import static io.fria.lilo.subscription.GatewayMessages.trySendComplete;
import static io.fria.lilo.subscription.GatewayMessages.trySendError;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import graphql.execution.reactive.SubscriptionPublisher;
import io.fria.lilo.Lilo;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
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
 * Stitches the registered operations of {@link SubscriptionGatewayHandler} on its executor, then
 * forwards what each upstream emits to the operation's client, or reports why it cannot.
 */
final class SubscriptionStitcher {

  private static final CompletableFuture<Void> DONE = CompletableFuture.completedFuture(null);

  private static final int POOL_CORE_SIZE = Math.max(4, Runtime.getRuntime().availableProcessors());
  private static final int POOL_MAX_SIZE = 4 * POOL_CORE_SIZE;
  private static final int POOL_QUEUE_CAPACITY = 256;
  private static final long POOL_KEEP_ALIVE_SECONDS = 60;

  // Logged under the handler's name: the gateway's classes are one component to an operator
  private static final Logger LOG = LoggerFactory.getLogger(SubscriptionGatewayHandler.class);
  private static final AtomicInteger THREAD_COUNT = new AtomicInteger();

  private final @NotNull Lilo lilo;
  private final @NotNull Executor executor;
  private final @NotNull GatewaySessionRegistry registry;

  SubscriptionStitcher(
      final @NotNull Lilo lilo,
      final @NotNull Executor executor,
      final @NotNull GatewaySessionRegistry registry) {
    this.lilo = lilo;
    this.executor = executor;
    this.registry = registry;
  }

  /** The bounded pool of daemon threads a handler owns unless it is given an executor. */
  static @NotNull Executor newDefaultExecutor() {
    final ThreadPoolExecutor pool =
        new ThreadPoolExecutor(
            POOL_CORE_SIZE,
            POOL_MAX_SIZE,
            POOL_KEEP_ALIVE_SECONDS,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(POOL_QUEUE_CAPACITY),
            SubscriptionStitcher::newThread);
    pool.allowCoreThreadTimeOut(true);
    return pool;
  }

  private static @NotNull Thread newThread(final @NotNull Runnable runnable) {
    final Thread thread =
        new Thread(runnable, "lilo-subscription-" + THREAD_COUNT.incrementAndGet());
    thread.setDaemon(true);
    return thread;
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

  /**
   * Stitches a registered operation on the executor and starts it once the stitch finished.
   *
   * @return completes once the operation is running or was rejected
   */
  @NotNull
  CompletableFuture<Void> submit(
      final @NotNull GatewayOperation operation, final @NotNull ExecutionInput executionInput) {

    final AtomicInteger inFlight = operation.state().inFlight;
    final CompletableFuture<ExecutionResult> stitch = new CompletableFuture<>();
    // Counted before submitting; released exactly once, when the stitch finished or never started
    inFlight.incrementAndGet();
    try {
      // Offloaded: with a cached schema the stitch runs the source's retriever inline
      this.executor.execute(() -> this.stitch(executionInput, stitch));
    } catch (final RuntimeException e) {
      inFlight.decrementAndGet();
      notSubmitted(operation, e);
      return DONE;
    }

    return stitch.handle(
        (result, throwable) -> {
          inFlight.decrementAndGet();
          this.tryStart(operation, result, throwable);
          return null;
        });
  }

  /** Reports a stitch the executor did not take, unless the operation was cancelled meanwhile. */
  private static void notSubmitted(
      final @NotNull GatewayOperation operation, final @NotNull RuntimeException e) {

    final String operationId = operation.id();
    final boolean rejected = e instanceof RejectedExecutionException;
    LOG.warn(
        "Subscription {} not started: the stitching executor {}",
        safe(operationId),
        rejected ? "is saturated" : "failed: " + safe(e.toString()));
    LOG.debug("Stitching executor failure", e);
    if (operation.release()) {
      trySendError(
          operation.session(),
          operationId,
          errors(rejected ? TOO_MANY_REQUESTS : EXECUTION_FAILED));
    }
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

  private void tryStart(
      final @NotNull GatewayOperation operation,
      final @Nullable ExecutionResult result,
      final @Nullable Throwable throwable) {
    try {
      this.start(operation, result, throwable);
    } catch (final RuntimeException e) {
      final String operationId = operation.id();
      LOG.warn("Subscription {} could not be started: {}", safe(operationId), safe(e.toString()));
      LOG.debug("Subscription start failure", e);
      // Never leave a half started operation registered
      if (operation.release()) {
        operation.cancel();
        trySendError(operation.session(), operationId, errors(EXECUTION_FAILED));
      }
    }
  }

  /** Runs when stitching a subscribe finished: starts forwarding or reports why it cannot. */
  private void start(
      final @NotNull GatewayOperation operation,
      final @Nullable ExecutionResult result,
      final @Nullable Throwable throwable) {

    final SubscriptionSourcePublisher upstream = upstreamOrFail(operation, result, throwable);

    if (upstream == null) {
      return;
    }

    if (!operation.attach(upstream)) {
      // Cancelled by the client or a session close while stitching: nothing is sent
      upstream.close();
      return;
    }

    operation.setSubscription(forward(operation, upstream));

    if (this.registry.isDetached(operation)) {
      // Defensive: registration already checked this, but a stale map must never keep forwarding
      if (operation.release()) {
        operation.cancel();
      }
      this.registry.closeSession(operation.session());
    }
  }

  /**
   * Classifies the stitch's outcome.
   *
   * @return the upstream to forward, or null if the operation could not start; that is reported
   */
  private static @Nullable SubscriptionSourcePublisher upstreamOrFail(
      final @NotNull GatewayOperation operation,
      final @Nullable ExecutionResult result,
      final @Nullable Throwable throwable) {

    if (throwable != null || result == null) {
      // The failure may be triggered by the client's query: no stack trace at warn
      LOG.warn(
          "Subscription {} could not be executed: {}",
          safe(operation.id()),
          safe(String.valueOf(throwable)));
      LOG.debug("Subscription execution failure", throwable);
      fail(operation, errors(EXECUTION_FAILED));
      return null;
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
      fail(operation, errors);
      return null;
    }

    if (upstream == null) {
      fail(operation, errors(NOT_A_SUBSCRIPTION));
    }

    return upstream;
  }

  /** Subscribes to the upstream and forwards what it emits to the operation's client. */
  private static @NotNull Disposable forward(
      final @NotNull GatewayOperation operation,
      final @NotNull SubscriptionSourcePublisher upstream) {
    return upstream
        .getFlux()
        .subscribe(
            payload -> onNext(operation, payload),
            e -> onError(operation, e),
            () -> onComplete(operation));
  }

  private static void onNext(
      final @NotNull GatewayOperation operation, final @NotNull Object payload) {

    // Nothing follows a client complete, even an event already in flight
    if (!operation.isRegistered()) {
      return;
    }

    sendNext(operation.session(), operation.id(), payload);
  }

  private static void onError(
      final @NotNull GatewayOperation operation, final @NotNull Throwable e) {

    // The source failed this operation. Only report it if it was still active.
    // This also runs when sending a next to the client failed.
    if (operation.release()) {
      logFailure(operation.id(), e);
      try {
        trySendError(operation.session(), operation.id(), errorsOf(e));
      } finally {
        // Releases the source in every case
        operation.cancel();
      }
    }
  }

  private static void onComplete(final @NotNull GatewayOperation operation) {

    // The source finished this operation. Only report it if the operation was still
    // active, i.e. neither the client nor a session close cancelled it meanwhile.
    if (operation.release()) {
      try {
        trySendComplete(operation.session(), operation.id());
      } finally {
        operation.cancel();
      }
    }
  }

  /** Reports an operation that could not start, unless it was cancelled meanwhile. */
  private static void fail(
      final @NotNull GatewayOperation operation, final @NotNull List<Map<String, Object>> errors) {

    if (operation.release()) {
      trySendError(operation.session(), operation.id(), errors);
    }
  }
}
