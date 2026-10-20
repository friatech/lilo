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

import static io.fria.lilo.JsonUtils.toObj;

import graphql.ExecutionResult;
import graphql.GraphQLError;
import graphql.execution.DataFetcherResult;
import io.fria.lilo.error.LiloSourceDataFetcherException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * How a remote source runs its query retriever; chosen once, when the source is created, from the
 * kind of the retriever.
 */
interface RemoteQueryFlow {

  static @NotNull RemoteQueryFlow of(final @NotNull QueryRetriever<?> retriever) {
    return retriever instanceof AsyncQueryRetriever
        ? new Async((AsyncQueryRetriever) retriever)
        : new Sync(retriever);
  }

  @NotNull
  CompletableFuture<ExecutionResult> execute(
      @NotNull SchemaSource source,
      @NotNull LiloContext liloContext,
      @NotNull GraphQLQuery query,
      @Nullable Object localContext);

  @NotNull
  CompletableFuture<Object> fetchData(
      @NotNull SchemaSource source,
      @NotNull LiloContext liloContext,
      @NotNull GraphQLQuery query,
      @Nullable Object localContext);

  private static @Nullable Object extractData(final @NotNull ExecutionResult graphQLResult) {

    final List<GraphQLError> errors = graphQLResult.getErrors();
    final boolean hasErrors = errors != null && !errors.isEmpty();
    final Map<String, Object> data = graphQLResult.getData();

    if (data == null) {
      if (hasErrors) {
        throw new LiloSourceDataFetcherException(errors);
      }

      throw new IllegalArgumentException("Remote source returned a response without data");
    }

    final Object value = data.values().iterator().next();

    if (hasErrors) {
      // Partial result: keep the remote data and report the remote errors alongside it
      return DataFetcherResult.newResult().data(value).errors(errors).build();
    }

    return value;
  }

  private static @NotNull ExecutionResult toExecutionResult(final @NotNull String queryResult) {

    final Optional<GraphQLResult> graphQLResultOptional = toObj(queryResult, GraphQLResult.class);

    if (graphQLResultOptional.isEmpty()) {
      throw new IllegalArgumentException("DataFetcher caught an empty response");
    }

    return graphQLResultOptional.get();
  }

  final class Async implements RemoteQueryFlow {

    private final @NotNull AsyncQueryRetriever retriever;

    Async(final @NotNull AsyncQueryRetriever retriever) {
      this.retriever = retriever;
    }

    @Override
    public @NotNull CompletableFuture<ExecutionResult> execute(
        final @NotNull SchemaSource source,
        final @NotNull LiloContext liloContext,
        final @NotNull GraphQLQuery query,
        final @Nullable Object localContext) {

      final var queryResult = this.retriever.get(liloContext, source, query, localContext);
      return queryResult.thenApply(RemoteQueryFlow::toExecutionResult);
    }

    @Override
    public @NotNull CompletableFuture<Object> fetchData(
        final @NotNull SchemaSource source,
        final @NotNull LiloContext liloContext,
        final @NotNull GraphQLQuery query,
        final @Nullable Object localContext) {

      final var graphQLResultFuture = this.execute(source, liloContext, query, localContext);

      return graphQLResultFuture.thenApply(RemoteQueryFlow::extractData);
    }
  }

  final class Sync implements RemoteQueryFlow {

    // Cast on use, so a retriever of an unknown kind fails when it is first needed
    private final @NotNull QueryRetriever<?> retriever;

    Sync(final @NotNull QueryRetriever<?> retriever) {
      this.retriever = retriever;
    }

    @Override
    public @NotNull CompletableFuture<ExecutionResult> execute(
        final @NotNull SchemaSource source,
        final @NotNull LiloContext liloContext,
        final @NotNull GraphQLQuery query,
        final @Nullable Object localContext) {

      final SyncQueryRetriever queryRetriever = (SyncQueryRetriever) this.retriever;
      final var queryResult = queryRetriever.get(liloContext, source, query, localContext);
      return CompletableFuture.supplyAsync(() -> toExecutionResult(queryResult));
    }

    @Override
    public @NotNull CompletableFuture<Object> fetchData(
        final @NotNull SchemaSource source,
        final @NotNull LiloContext liloContext,
        final @NotNull GraphQLQuery query,
        final @Nullable Object localContext) {

      return CompletableFuture.supplyAsync(
          () -> {
            final var graphQLResultFuture = this.execute(source, liloContext, query, localContext);

            return waitForData(graphQLResultFuture);
          });
    }

    private static @Nullable Object waitForData(
        final @NotNull CompletableFuture<ExecutionResult> graphQLResultFuture) {
      final ExecutionResult graphQLResult;

      try {
        graphQLResult = graphQLResultFuture.get();
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      } catch (final ExecutionException e) {
        throw new RuntimeException(e);
      }

      return extractData(graphQLResult);
    }
  }
}
