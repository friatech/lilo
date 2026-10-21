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

import static io.fria.lilo.JsonUtils.toStr;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * How a remote source runs its introspection retriever; chosen once, when the source is created,
 * from the kind of the retriever.
 */
interface RemoteIntrospectionFlow {

  String INTROSPECTION_REQUEST =
      toStr(
          GraphQLRequest.builder()
              .query(GraphQLRequest.INTROSPECTION_QUERY)
              .operationName("IntrospectionQuery")
              .build());

  /** The failures are logged to the given logger, so they keep the category of the source. */
  static @NotNull RemoteIntrospectionFlow of(
      final @NotNull IntrospectionRetriever<?> retriever, final @NotNull Logger log) {
    return retriever instanceof AsyncIntrospectionRetriever
        ? new Async((AsyncIntrospectionRetriever) retriever, log)
        : new Sync(retriever, log);
  }

  /**
   * Retrieves the introspection response of the source and reads it with the given function. A
   * failed retrieval is logged and yields an empty result; the asynchronous flow treats a failed
   * read the same way, while the synchronous one completes exceptionally with it.
   */
  <T> @NotNull CompletableFuture<Optional<T>> load(
      @NotNull SchemaSource source,
      @NotNull LiloContext liloContext,
      @Nullable Object localContext,
      @NotNull Function<String, T> read);

  final class Async implements RemoteIntrospectionFlow {

    private final @NotNull AsyncIntrospectionRetriever retriever;
    private final @NotNull Logger log;

    Async(final @NotNull AsyncIntrospectionRetriever retriever, final @NotNull Logger log) {
      this.retriever = retriever;
      this.log = log;
    }

    @Override
    public <T> @NotNull CompletableFuture<Optional<T>> load(
        final @NotNull SchemaSource source,
        final @NotNull LiloContext liloContext,
        final @Nullable Object localContext,
        final @NotNull Function<String, T> read) {

      return this.retriever
          .get(liloContext, source, INTROSPECTION_REQUEST, localContext)
          .thenApply(res -> Optional.of(read.apply(res)))
          .exceptionally(
              e -> {
                this.log.error("Could not load introspection for {}", source.getName());
                this.log.debug("Introspection fetching exception", e);
                return Optional.empty();
              });
    }
  }

  final class Sync implements RemoteIntrospectionFlow {

    // Cast on use, so a retriever of an unknown kind fails when it is first needed
    private final @NotNull IntrospectionRetriever<?> retriever;
    private final @NotNull Logger log;

    Sync(final @NotNull IntrospectionRetriever<?> retriever, final @NotNull Logger log) {
      this.retriever = retriever;
      this.log = log;
    }

    @Override
    public <T> @NotNull CompletableFuture<Optional<T>> load(
        final @NotNull SchemaSource source,
        final @NotNull LiloContext liloContext,
        final @Nullable Object localContext,
        final @NotNull Function<String, T> read) {

      final SyncIntrospectionRetriever introspectionRetriever =
          (SyncIntrospectionRetriever) this.retriever;

      return CompletableFuture.supplyAsync(
          () -> {
            final String introspectionResponse;

            try {
              introspectionResponse =
                  introspectionRetriever.get(
                      liloContext, source, INTROSPECTION_REQUEST, localContext);
            } catch (final Exception e) {
              this.log.error("Could not load introspection for {}", source.getName());
              this.log.debug("Introspection fetching exception", e);
              return Optional.empty();
            }

            return Optional.of(read.apply(introspectionResponse));
          });
    }
  }
}
