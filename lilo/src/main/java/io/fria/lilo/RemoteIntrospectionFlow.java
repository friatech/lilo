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

import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * How a {@link RemoteSchemaSource} runs its introspection retriever; chosen once, when the source
 * is created, from the kind of the retriever.
 */
interface RemoteIntrospectionFlow {

  static @NotNull RemoteIntrospectionFlow of(final @NotNull IntrospectionRetriever<?> retriever) {
    return retriever instanceof AsyncIntrospectionRetriever
        ? new Async((AsyncIntrospectionRetriever) retriever)
        : new Sync(retriever);
  }

  @NotNull
  CompletableFuture<SchemaSource> loadSchema(
      @NotNull RemoteSchemaSource source,
      @NotNull LiloContext liloContext,
      @Nullable Object localContext);

  final class Async implements RemoteIntrospectionFlow {

    private final @NotNull AsyncIntrospectionRetriever retriever;

    Async(final @NotNull AsyncIntrospectionRetriever retriever) {
      this.retriever = retriever;
    }

    @Override
    public @NotNull CompletableFuture<SchemaSource> loadSchema(
        final @NotNull RemoteSchemaSource source,
        final @NotNull LiloContext liloContext,
        final @Nullable Object localContext) {

      return this.retriever
          .get(liloContext, source, RemoteSchemaSource.INTROSPECTION_REQUEST, localContext)
          .thenApply(res -> source.fetchIntrospection(res, liloContext))
          .exceptionally(
              e -> {
                RemoteSchemaSource.LOG.error(
                    "Could not load introspection for {}", source.schemaName);
                RemoteSchemaSource.LOG.debug("Introspection fetching exception", e);
                return source;
              });
    }
  }

  final class Sync implements RemoteIntrospectionFlow {

    // Cast on use, so a retriever of an unknown kind fails when it is first needed
    private final @NotNull IntrospectionRetriever<?> retriever;

    Sync(final @NotNull IntrospectionRetriever<?> retriever) {
      this.retriever = retriever;
    }

    @Override
    public @NotNull CompletableFuture<SchemaSource> loadSchema(
        final @NotNull RemoteSchemaSource source,
        final @NotNull LiloContext liloContext,
        final @Nullable Object localContext) {

      final SyncIntrospectionRetriever introspectionRetriever =
          (SyncIntrospectionRetriever) this.retriever;

      return CompletableFuture.supplyAsync(
          () -> {
            final String introspectionResponse;

            try {
              introspectionResponse =
                  introspectionRetriever.get(
                      liloContext, source, RemoteSchemaSource.INTROSPECTION_REQUEST, localContext);
            } catch (final Exception e) {
              RemoteSchemaSource.LOG.error(
                  "Could not load introspection for {}", source.schemaName);
              RemoteSchemaSource.LOG.debug("Introspection fetching exception", e);
              return source;
            }

            return source.fetchIntrospection(introspectionResponse, liloContext);
          });
    }
  }
}
