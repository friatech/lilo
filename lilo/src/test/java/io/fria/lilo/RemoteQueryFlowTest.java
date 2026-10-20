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
package io.fria.lilo;

import graphql.ExecutionResult;
import graphql.execution.DataFetcherResult;
import io.fria.lilo.error.LiloSourceDataFetcherException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RemoteQueryFlowTest {

  private static final LiloContext CONTEXT = Mockito.mock(LiloContext.class);
  private static final SchemaSource SOURCE = Mockito.mock(SchemaSource.class);
  private static final GraphQLQuery QUERY = Mockito.mock(GraphQLQuery.class);

  private static RemoteQueryFlow sync(final Supplier<String> response) {
    return RemoteQueryFlow.of(
        (SyncQueryRetriever) (liloContext, schemaSource, query, localContext) -> response.get());
  }

  private static RemoteQueryFlow async(final CompletableFuture<String> response) {
    return RemoteQueryFlow.of(
        (AsyncQueryRetriever) (liloContext, schemaSource, query, localContext) -> response);
  }

  private static Object fetch(final RemoteQueryFlow flow) {
    return flow.fetchData(SOURCE, CONTEXT, QUERY, null).join();
  }

  private static Throwable fetchFailure(final RemoteQueryFlow flow) {
    return Assertions.assertThrows(CompletionException.class, () -> fetch(flow)).getCause();
  }

  @Test
  void ofPicksTheFlowByRetrieverKind() {

    Assertions.assertInstanceOf(RemoteQueryFlow.Sync.class, sync(() -> "{}"));
    Assertions.assertInstanceOf(
        RemoteQueryFlow.Async.class, async(CompletableFuture.completedFuture("{}")));
  }

  @Test
  void retrieverOfUnknownKindFailsOnUse() {

    final RemoteQueryFlow flow =
        RemoteQueryFlow.of(
            (QueryRetriever<Object>) (liloContext, schemaSource, query, localContext) -> "x");

    Assertions.assertThrows(
        ClassCastException.class, () -> flow.execute(SOURCE, CONTEXT, QUERY, null));
  }

  @Test
  void retrieverReceivesTheSourceQueryAndLocalContext() {

    final AtomicReference<Object[]> arguments = new AtomicReference<>();
    final Object localContext = new Object();
    final RemoteQueryFlow flow =
        RemoteQueryFlow.of(
            (SyncQueryRetriever)
                (liloContext, schemaSource, query, local) -> {
                  arguments.set(new Object[] {liloContext, schemaSource, query, local});
                  return "{\"data\":{\"a\":1}}";
                });

    flow.execute(SOURCE, CONTEXT, QUERY, localContext).join();

    Assertions.assertArrayEquals(
        new Object[] {CONTEXT, SOURCE, QUERY, localContext}, arguments.get());
  }

  @Test
  void syncExecuteCallsTheRetrieverOnTheCallerThread() {

    final AtomicReference<Thread> thread = new AtomicReference<>();
    final RemoteQueryFlow flow =
        sync(
            () -> {
              thread.set(Thread.currentThread());
              return "{\"data\":{\"a\":1}}";
            });

    final ExecutionResult result = flow.execute(SOURCE, CONTEXT, QUERY, null).join();

    Assertions.assertSame(Thread.currentThread(), thread.get());
    Assertions.assertEquals(Map.of("a", 1), result.getData());
  }

  @Test
  void syncFetchDataCallsTheRetrieverOffTheCallerThread() {

    final AtomicReference<Thread> thread = new AtomicReference<>();
    final RemoteQueryFlow flow =
        sync(
            () -> {
              thread.set(Thread.currentThread());
              return "{\"data\":{\"a\":1}}";
            });

    Assertions.assertEquals(1, fetch(flow));
    Assertions.assertNotSame(Thread.currentThread(), thread.get());
  }

  @Test
  void asyncFlowCompletesWithTheRetrieverFuture() {

    final CompletableFuture<String> response = new CompletableFuture<>();
    final CompletableFuture<Object> data = async(response).fetchData(SOURCE, CONTEXT, QUERY, null);

    Assertions.assertFalse(data.isDone());

    response.complete("{\"data\":{\"a\":{\"b\":2}}}");

    Assertions.assertEquals(Map.of("b", 2), data.join());
  }

  @Test
  void nullRootValueIsReturnedAsNull() {

    Assertions.assertNull(fetch(sync(() -> "{\"data\":{\"a\":null}}")));
    Assertions.assertNull(
        fetch(async(CompletableFuture.completedFuture("{\"data\":{\"a\":null}}"))));
  }

  @Test
  void dataWithErrorsIsAPartialResult() {

    final Object data =
        fetch(sync(() -> "{\"data\":{\"a\":1},\"errors\":[{\"message\":\"partly failed\"}]}"));

    final DataFetcherResult<?> result = Assertions.assertInstanceOf(DataFetcherResult.class, data);
    Assertions.assertEquals(1, result.getData());
    Assertions.assertEquals(1, result.getErrors().size());
    Assertions.assertEquals("partly failed", result.getErrors().get(0).getMessage());
  }

  @Test
  void errorsWithoutDataFailWithTheRemoteErrors() {

    final String response = "{\"errors\":[{\"message\":\"failed\"}]}";

    for (final RemoteQueryFlow flow :
        new RemoteQueryFlow[] {
          sync(() -> response), async(CompletableFuture.completedFuture(response))
        }) {
      final Throwable cause = fetchFailure(flow);
      Assertions.assertInstanceOf(LiloSourceDataFetcherException.class, cause);
    }
  }

  @Test
  void responseWithoutDataOrErrorsFails() {

    for (final RemoteQueryFlow flow :
        new RemoteQueryFlow[] {sync(() -> "{}"), async(CompletableFuture.completedFuture("{}"))}) {
      final Throwable cause = fetchFailure(flow);
      Assertions.assertInstanceOf(IllegalArgumentException.class, cause);
      Assertions.assertEquals("Remote source returned a response without data", cause.getMessage());
    }
  }

  @Test
  void emptyResponseFails() {

    final Throwable cause = fetchFailure(async(CompletableFuture.completedFuture("null")));

    Assertions.assertInstanceOf(IllegalArgumentException.class, cause);
    Assertions.assertEquals("DataFetcher caught an empty response", cause.getMessage());
  }

  @Test
  void malformedResponseIsWrappedOnlyByTheSyncFlow() {

    // The sync flow waits for its own parsing future, so the failure arrives wrapped once more
    final Throwable syncCause = fetchFailure(sync(() -> "not json"));
    Assertions.assertEquals(RuntimeException.class, syncCause.getClass());
    Assertions.assertInstanceOf(ExecutionException.class, syncCause.getCause());
    Assertions.assertEquals(
        "Deserialization exception", syncCause.getCause().getCause().getMessage());

    final Throwable asyncCause = fetchFailure(async(CompletableFuture.completedFuture("not json")));
    Assertions.assertInstanceOf(IllegalArgumentException.class, asyncCause);
    Assertions.assertEquals("Deserialization exception", asyncCause.getMessage());
  }

  @Test
  void syncRetrieverFailureFailsTheFetch() {

    final Throwable cause =
        fetchFailure(
            sync(
                () -> {
                  throw new IllegalStateException("boom");
                }));

    Assertions.assertInstanceOf(IllegalStateException.class, cause);
    Assertions.assertEquals("boom", cause.getMessage());
  }
}
