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
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * How a {@link RemoteSchemaSource} runs its query retriever; chosen once, when the source is
 * created, from the kind of the retriever.
 */
interface RemoteQueryFlow {

  static @NotNull RemoteQueryFlow of(final @NotNull QueryRetriever<?> retriever) {
    return retriever instanceof AsyncQueryRetriever
        ? new Async((AsyncQueryRetriever) retriever)
        : new Sync(retriever);
  }

  @NotNull
  CompletableFuture<ExecutionResult> execute(
      @NotNull RemoteSchemaSource source,
      @NotNull LiloContext liloContext,
      @NotNull GraphQLQuery query,
      @Nullable Object localContext);

  @NotNull
  CompletableFuture<Object> fetchData(
      @NotNull RemoteSchemaSource source,
      @NotNull LiloContext liloContext,
      @NotNull GraphQLQuery query,
      @Nullable Object localContext);

  final class Async implements RemoteQueryFlow {

    private final @NotNull AsyncQueryRetriever retriever;

    Async(final @NotNull AsyncQueryRetriever retriever) {
      this.retriever = retriever;
    }

    @Override
    public @NotNull CompletableFuture<ExecutionResult> execute(
        final @NotNull RemoteSchemaSource source,
        final @NotNull LiloContext liloContext,
        final @NotNull GraphQLQuery query,
        final @Nullable Object localContext) {

      final var queryResult = this.retriever.get(liloContext, source, query, localContext);
      return queryResult.thenApply(RemoteSchemaSource::toExecutionResult);
    }

    @Override
    public @NotNull CompletableFuture<Object> fetchData(
        final @NotNull RemoteSchemaSource source,
        final @NotNull LiloContext liloContext,
        final @NotNull GraphQLQuery query,
        final @Nullable Object localContext) {

      final var graphQLResultFuture = this.execute(source, liloContext, query, localContext);

      return graphQLResultFuture.thenApply(RemoteSchemaSource::fetchData);
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
        final @NotNull RemoteSchemaSource source,
        final @NotNull LiloContext liloContext,
        final @NotNull GraphQLQuery query,
        final @Nullable Object localContext) {

      final SyncQueryRetriever queryRetriever = (SyncQueryRetriever) this.retriever;
      final var queryResult = queryRetriever.get(liloContext, source, query, localContext);
      return CompletableFuture.supplyAsync(() -> RemoteSchemaSource.toExecutionResult(queryResult));
    }

    @Override
    public @NotNull CompletableFuture<Object> fetchData(
        final @NotNull RemoteSchemaSource source,
        final @NotNull LiloContext liloContext,
        final @NotNull GraphQLQuery query,
        final @Nullable Object localContext) {

      return CompletableFuture.supplyAsync(
          () -> {
            final var graphQLResultFuture = this.execute(source, liloContext, query, localContext);

            return RemoteSchemaSource.fetchData(graphQLResultFuture);
          });
    }
  }
}
