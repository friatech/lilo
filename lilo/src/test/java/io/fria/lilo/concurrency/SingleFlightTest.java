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
package io.fria.lilo.concurrency;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static io.fria.lilo.TestUtils.createGraphQL;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.idl.RuntimeWiring;
import io.fria.lilo.AsyncIntrospectionRetriever;
import io.fria.lilo.Lilo;
import io.fria.lilo.LiloContext;
import io.fria.lilo.RemoteSchemaSource;
import io.fria.lilo.SchemaSource;
import io.fria.lilo.SyncIntrospectionRetriever;
import io.fria.lilo.TestUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SingleFlightTest {

  private static final int CONCURRENCY = 20;
  private static final long TIMEOUT_SECONDS = 30;
  private static final String QUERY = "{greeting1}";
  private static final Map<String, Object> EXPECTED = Map.of("greeting1", "Hello greeting1");

  private static final RuntimeWiring WIRING =
      RuntimeWiring.newRuntimeWiring()
          .type(newTypeWiring("Query").dataFetcher("greeting1", env -> "Hello greeting1"))
          .build();

  private ExecutorService executor;
  private GraphQL remote;

  @BeforeEach
  void setUp() {
    this.executor = Executors.newFixedThreadPool(CONCURRENCY);
    this.remote = createGraphQL("/greetings/greeting1.graphqls", WIRING);
  }

  @AfterEach
  void tearDown() throws InterruptedException {
    this.executor.shutdownNow();
    Assertions.assertTrue(this.executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS));
  }

  private Lilo lilo(final @NotNull SyncIntrospectionRetriever introspectionRetriever) {
    return Lilo.builder()
        .addSource(
            RemoteSchemaSource.create(
                "project1", introspectionRetriever, new TestUtils.TestQueryRetriever(this.remote)))
        .build();
  }

  private Lilo lilo(final @NotNull AsyncIntrospectionRetriever introspectionRetriever) {
    return Lilo.builder()
        .addSource(
            RemoteSchemaSource.create(
                "project1", introspectionRetriever, new TestUtils.TestQueryRetriever(this.remote)))
        .build();
  }

  /** Fires {@link #CONCURRENCY} stitch calls released together and waits for all of them. */
  private void stitchConcurrently(final @NotNull Lilo lilo) throws Exception {

    final CountDownLatch ready = new CountDownLatch(CONCURRENCY);
    final CountDownLatch start = new CountDownLatch(1);
    final List<Future<ExecutionResult>> results = new ArrayList<>();

    for (int i = 0; i < CONCURRENCY; i++) {
      results.add(
          this.executor.submit(
              () -> {
                ready.countDown();
                Assertions.assertTrue(start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
                return lilo.stitch(QUERY);
              }));
    }

    Assertions.assertTrue(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    start.countDown();

    for (final Future<ExecutionResult> result : results) {
      final ExecutionResult executionResult = result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      Assertions.assertTrue(executionResult.getErrors().isEmpty());
      Assertions.assertEquals(EXPECTED, executionResult.getData());
    }
  }

  @Test
  void twentyConcurrentColdRequestsIntrospectOnce() throws Exception {

    final var retriever = new CountingIntrospectionRetriever(this.remote, 200);
    final Lilo lilo = this.lilo(retriever);

    this.stitchConcurrently(lilo);

    Assertions.assertEquals(1, retriever.calls.get());
  }

  @Test
  void twentyConcurrentColdAsyncRequestsIntrospectOnce() throws Exception {

    final var retriever = new CountingAsyncIntrospectionRetriever(this.remote, 200);
    final Lilo lilo = this.lilo(retriever);

    final List<CompletableFuture<ExecutionResult>> results = new ArrayList<>();

    for (int i = 0; i < CONCURRENCY; i++) {
      results.add(lilo.stitchAsync(ExecutionInput.newExecutionInput(QUERY).build()));
    }

    for (final CompletableFuture<ExecutionResult> result : results) {
      Assertions.assertEquals(EXPECTED, result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).getData());
    }

    Assertions.assertEquals(1, retriever.calls.get());
  }

  @Test
  void invalidateTriggersExactlyOneReload() throws Exception {

    final var retriever = new CountingIntrospectionRetriever(this.remote, 200);
    final Lilo lilo = this.lilo(retriever);

    Assertions.assertEquals(EXPECTED, lilo.stitch(QUERY).getData());
    Assertions.assertEquals(1, retriever.calls.get());

    lilo.getContext().invalidateAll();
    this.stitchConcurrently(lilo);

    Assertions.assertEquals(2, retriever.calls.get());
  }

  @Test
  void invalidateDuringLoadForcesAnotherReload() throws Exception {

    final CountDownLatch entered = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);
    final var retriever = new CountingIntrospectionRetriever(this.remote, 0);
    retriever.firstCallEntered = entered;
    retriever.firstCallRelease = release;
    final Lilo lilo = this.lilo(retriever);

    final Future<ExecutionResult> inFlight = this.executor.submit(() -> lilo.stitch(QUERY));

    Assertions.assertTrue(entered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    // The introspection request has been sent, so whatever it returns may already be stale
    lilo.getContext().invalidateAll();
    release.countDown();

    Assertions.assertEquals(EXPECTED, inFlight.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).getData());
    Assertions.assertEquals(1, retriever.calls.get());

    // The invalidation must not be lost: the next request introspects again
    Assertions.assertEquals(EXPECTED, lilo.stitch(QUERY).getData());
    Assertions.assertEquals(2, retriever.calls.get());

    // And then the context is warm again
    Assertions.assertEquals(EXPECTED, lilo.stitch(QUERY).getData());
    Assertions.assertEquals(2, retriever.calls.get());
  }

  @Test
  void failedLoadIsRetriedByTheNextRequest() {

    final var retriever = new CountingIntrospectionRetriever(this.remote, 0);
    retriever.failFirstCall = true;
    final Lilo lilo = this.lilo(retriever);

    Assertions.assertThrows(RuntimeException.class, () -> lilo.stitch(QUERY));
    Assertions.assertEquals(1, retriever.calls.get());

    Assertions.assertEquals(EXPECTED, lilo.stitch(QUERY).getData());
    Assertions.assertEquals(2, retriever.calls.get());

    Assertions.assertEquals(EXPECTED, lilo.stitch(QUERY).getData());
    Assertions.assertEquals(2, retriever.calls.get());
  }

  @Test
  void errorThrownSynchronouslyByAsyncRetrieverDoesNotBlockLaterLoads() throws Exception {

    final AtomicInteger calls = new AtomicInteger();
    final var delegate = new TestUtils.TestIntrospectionRetriever(this.remote);
    final AsyncIntrospectionRetriever retriever =
        (liloContext, schemaSource, query, localContext) -> {
          if (calls.incrementAndGet() == 1) {
            throw new AssertionError("boom");
          }

          return CompletableFuture.completedFuture(
              delegate.get(liloContext, schemaSource, query, localContext));
        };
    final Lilo lilo = this.lilo(retriever);

    final Future<ExecutionResult> first = this.executor.submit(() -> lilo.stitch(QUERY));
    final ExecutionException thrown =
        Assertions.assertThrows(
            ExecutionException.class, () -> first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    Throwable root = thrown;
    while (root.getCause() != null) {
      root = root.getCause();
    }
    Assertions.assertInstanceOf(AssertionError.class, root);
    Assertions.assertEquals("boom", root.getMessage());

    // The failed load must not stay in flight: the next request loads again and succeeds
    final Future<ExecutionResult> second = this.executor.submit(() -> lilo.stitch(QUERY));
    Assertions.assertEquals(EXPECTED, second.get(5, TimeUnit.SECONDS).getData());
    Assertions.assertEquals(2, calls.get());
  }

  @Test
  void warmPathReturnsCompletedFuture() {

    final var retriever = new CountingIntrospectionRetriever(this.remote, 0);
    final Lilo lilo = this.lilo(retriever);
    final LiloContext context = lilo.getContext();

    final GraphQL loaded = context.getMergedGraphQL();
    final CompletableFuture<GraphQL> warm = context.getGraphQLAsync();

    Assertions.assertTrue(warm.isDone());
    Assertions.assertSame(loaded, warm.join());
  }

  private static void sleep(final long millis) {
    try {
      Thread.sleep(millis);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static void await(final @NotNull CountDownLatch latch) {
    try {
      if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out waiting for the test to release the call");
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static final class CountingIntrospectionRetriever implements SyncIntrospectionRetriever {

    private final AtomicInteger calls = new AtomicInteger();
    private final TestUtils.TestIntrospectionRetriever delegate;
    private final long delayMillis;
    private volatile boolean failFirstCall;
    private volatile @Nullable CountDownLatch firstCallEntered;
    private volatile @Nullable CountDownLatch firstCallRelease;

    CountingIntrospectionRetriever(final @NotNull GraphQL graphQL, final long delayMillis) {
      this.delegate = new TestUtils.TestIntrospectionRetriever(graphQL);
      this.delayMillis = delayMillis;
    }

    @Override
    public @NotNull String get(
        final @NotNull LiloContext liloContext,
        final @NotNull SchemaSource schemaSource,
        final @NotNull String query,
        final @Nullable Object localContext) {

      final int call = this.calls.incrementAndGet();

      if (call == 1) {
        final CountDownLatch entered = this.firstCallEntered;
        final CountDownLatch release = this.firstCallRelease;

        if (entered != null && release != null) {
          entered.countDown();
          await(release);
        }

        if (this.failFirstCall) {
          throw new IllegalStateException("introspection failed");
        }
      }

      sleep(this.delayMillis);

      return this.delegate.get(liloContext, schemaSource, query, localContext);
    }
  }

  private static final class CountingAsyncIntrospectionRetriever
      implements AsyncIntrospectionRetriever {

    private final AtomicInteger calls = new AtomicInteger();
    private final TestUtils.TestIntrospectionRetriever delegate;
    private final long delayMillis;

    CountingAsyncIntrospectionRetriever(final @NotNull GraphQL graphQL, final long delayMillis) {
      this.delegate = new TestUtils.TestIntrospectionRetriever(graphQL);
      this.delayMillis = delayMillis;
    }

    @Override
    public @NotNull CompletableFuture<String> get(
        final @NotNull LiloContext liloContext,
        final @NotNull SchemaSource schemaSource,
        final @NotNull String query,
        final @Nullable Object localContext) {

      this.calls.incrementAndGet();

      return CompletableFuture.supplyAsync(
          () -> {
            sleep(this.delayMillis);
            return this.delegate.get(liloContext, schemaSource, query, localContext);
          });
    }
  }
}
