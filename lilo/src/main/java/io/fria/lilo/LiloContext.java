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

import graphql.ExecutionInput;
import graphql.GraphQL;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.instrumentation.Instrumentation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LiloContext {

  private final DataFetcherExceptionHandler dataFetcherExceptionHandler;
  private final IntrospectionFetchingMode introspectionFetchingMode;
  private final boolean retrySchemaLoad;
  private final GraphQLAssembler assembler;

  /*
   * Concurrency model: the warm path only reads the volatile fields below and never locks. Every
   * state transition (invalidate, starting a load, publishing a finished load) happens while
   * holding stateLock, so transitions are atomic with respect to each other. No I/O or schema
   * building runs while the lock is held.
   */
  private final Object stateLock = new Object();

  /** Names of the sources invalidated while a load was in flight; guarded by stateLock. */
  private final Set<String> invalidatedDuringLoad = new HashSet<>();

  /** The load every caller shares while it runs; guarded by stateLock. */
  private @Nullable CompletableFuture<GraphQL> inFlightLoad;

  /** Immutable; the set of sources never changes, only their loaded state does. */
  private final Map<String, SchemaSource> sourceMap;

  private volatile @Nullable GraphQL mergedGraphQL;
  private volatile boolean schemasAreNotLoaded = true;

  LiloContext(
      final @NotNull DataFetcherExceptionHandler dataFetcherExceptionHandler,
      final @NotNull IntrospectionFetchingMode introspectionFetchingMode,
      final boolean retrySchemaLoad,
      final @Nullable Instrumentation instrumentation,
      final @NotNull RequestLimits requestLimits,
      final @Nullable Consumer<GraphQL.Builder> graphQLCustomizer,
      final @NotNull SchemaSource... schemaSources) {
    this.dataFetcherExceptionHandler = Objects.requireNonNull(dataFetcherExceptionHandler);
    this.introspectionFetchingMode = Objects.requireNonNull(introspectionFetchingMode);
    this.retrySchemaLoad = retrySchemaLoad;
    this.assembler =
        new GraphQLAssembler(
            this.dataFetcherExceptionHandler,
            GraphQLAssembler.combineInstrumentation(
                Objects.requireNonNull(requestLimits), instrumentation),
            graphQLCustomizer);
    this.sourceMap = toSourceMap(Arrays.stream(schemaSources));
  }

  private static @NotNull Map<String, SchemaSource> toSourceMap(
      final @NotNull Stream<SchemaSource> schemaSourcesStream) {
    return Collections.unmodifiableMap(
        schemaSourcesStream.collect(Collectors.toMap(SchemaSource::getName, ss -> ss)));
  }

  public @NotNull DataFetcherExceptionHandler getDataFetcherExceptionHandler() {
    return this.dataFetcherExceptionHandler;
  }

  public @NotNull GraphQL getMergedGraphQL() {
    return this.getGraphQL(null);
  }

  public @NotNull CompletableFuture<GraphQL> getGraphQLAsync() {
    return this.getGraphQLAsync(null);
  }

  public @NotNull IntrospectionFetchingMode getIntrospectionFetchingMode() {
    return this.introspectionFetchingMode;
  }

  public @NotNull Map<String, SchemaSource> getSchemaSources() {
    return Map.copyOf(this.sourceMap);
  }

  public void invalidate(final @NotNull String schemaName) {

    synchronized (this.stateLock) {
      final SchemaSource schemaSource = this.sourceMap.get(Objects.requireNonNull(schemaName));

      if (schemaSource == null) {
        return;
      }

      this.invalidateLocked(schemaSource);
      this.schemasAreNotLoaded = this.computeSchemasAreNotLoaded();
    }
  }

  public void invalidateAll() {

    synchronized (this.stateLock) {
      this.sourceMap.values().forEach(this::invalidateLocked);
      this.schemasAreNotLoaded = this.computeSchemasAreNotLoaded();
    }
  }

  /**
   * Reloads the sources that are not loaded and rebuilds the merged schema. While a load is in
   * flight every caller gets that same future, so N concurrent callers trigger one introspection
   * per source (each caller receives its own {@link CompletableFuture#copy() copy}, so cancelling
   * one does not affect the others). The shared future is cleared when the load completes or fails,
   * so a failed load is retried by the next caller.
   */
  public @NotNull CompletableFuture<GraphQL> reloadGraphQL(final @Nullable Object localContext) {

    final CompletableFuture<GraphQL> load;

    synchronized (this.stateLock) {
      if (this.inFlightLoad != null) {
        // A copy, so that a caller completing or cancelling its future cannot affect the others
        return this.inFlightLoad.copy();
      }

      load = new CompletableFuture<>();
      this.inFlightLoad = load;
    }

    final CompletableFuture<GraphQL> build;

    try {
      build = this.loadSources(localContext).thenApply(this.assembler::assemble);
    } catch (final Throwable e) {
      // A retriever may throw synchronously, including an Error; the load must still finish,
      // otherwise it stays in flight and every later caller waits on it forever
      this.finishLoad(load, null, e);
      return load.copy();
    }

    build.whenComplete((graphQL, throwable) -> this.finishLoad(load, graphQL, throwable));

    return load.copy();
  }

  @NotNull
  GraphQL getGraphQL(final @Nullable ExecutionInput executionInput) {

    try {
      return this.getGraphQLAsync(executionInput).get();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    } catch (final ExecutionException e) {
      if (e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      }

      throw new RuntimeException(e);
    }
  }

  @NotNull
  CompletableFuture<GraphQL> getGraphQLAsync(final @Nullable ExecutionInput executionInput) {

    // Read the flag first: a load publishes mergedGraphQL before it clears the flag, so seeing the
    // cleared flag guarantees seeing the schema it was cleared for
    final boolean notLoaded = this.schemasAreNotLoaded;
    final GraphQL graphQL = this.mergedGraphQL;

    if (notLoaded || graphQL == null) {
      return this.reloadGraphQL(executionInput == null ? null : executionInput.getLocalContext());
    }

    return CompletableFuture.completedFuture(graphQL);
  }

  private void finishLoad(
      final @NotNull CompletableFuture<GraphQL> load,
      final @Nullable GraphQL graphQL,
      final @Nullable Throwable throwable) {

    synchronized (this.stateLock) {
      if (this.inFlightLoad == load) {
        this.inFlightLoad = null;
      }

      // Sources invalidated while their introspection was in flight may have been marked loaded
      // again by that (possibly stale) response; invalidate them once more so that the next
      // request fetches them again instead of losing the invalidation
      this.invalidatedDuringLoad.stream()
          .map(this.sourceMap::get)
          .filter(Objects::nonNull)
          .forEach(SchemaSource::invalidate);
      this.invalidatedDuringLoad.clear();

      if (graphQL != null) {
        this.mergedGraphQL = graphQL;
        this.schemasAreNotLoaded = this.computeSchemasAreNotLoaded();
      }
      // A failed load publishes nothing and leaves the flag as it was, so a request-triggered
      // load is retried by the next request, as before
    }

    // Complete outside the lock: dependent stages run on this thread
    if (throwable != null) {
      load.completeExceptionally(
          throwable instanceof CompletionException && throwable.getCause() != null
              ? throwable.getCause()
              : throwable);
    } else {
      load.complete(graphQL);
    }
  }

  private void invalidateLocked(final @NotNull SchemaSource schemaSource) {

    schemaSource.invalidate();

    if (this.inFlightLoad != null) {
      this.invalidatedDuringLoad.add(schemaSource.getName());
    }
  }

  private @NotNull CompletableFuture<List<SchemaSource>> loadSources(
      final @Nullable Object localContext) {

    CompletableFuture<List<SchemaSource>> combined = CompletableFuture.supplyAsync(ArrayList::new);

    final List<CompletableFuture<SchemaSource>> futures =
        this.sourceMap.values().stream()
            .map(
                ss -> {
                  if (ss.isSchemaNotLoaded()) {
                    return ss.loadSchema(LiloContext.this, localContext);
                  } else {
                    return CompletableFuture.supplyAsync(() -> ss);
                  }
                })
            .collect(Collectors.toList());

    for (final CompletableFuture<SchemaSource> future : futures) {
      combined =
          combined.thenCombine(
              future,
              (combinedSchemaSources, baseSchemaSource) -> {
                combinedSchemaSources.add(baseSchemaSource);
                return combinedSchemaSources;
              });
    }

    return combined;
  }

  /** Decides from the sources' state whether the next request has to load; holds stateLock. */
  private boolean computeSchemasAreNotLoaded() {

    final Collection<SchemaSource> sources = this.sourceMap.values();
    final long notLoadedCount = sources.stream().filter(SchemaSource::isSchemaNotLoaded).count();

    if (notLoadedCount == sources.size()) { // none of them are loaded
      return true;
    } else if (notLoadedCount == 0) { // all of them are loaded
      return false;
    } else { // if partially loaded then check the retry option
      return this.retrySchemaLoad;
    }
  }

  /** Lilo's per-request limits; a {@code null} limit is not enforced. */
  static final class RequestLimits {

    private final @Nullable Integer maxQueryDepth;
    private final @Nullable Integer maxQueryComplexity;
    private final boolean introspectionEnabled;

    RequestLimits(
        final @Nullable Integer maxQueryDepth,
        final @Nullable Integer maxQueryComplexity,
        final boolean introspectionEnabled) {
      this.maxQueryDepth = maxQueryDepth;
      this.maxQueryComplexity = maxQueryComplexity;
      this.introspectionEnabled = introspectionEnabled;
    }

    @Nullable
    Integer getMaxQueryDepth() {
      return this.maxQueryDepth;
    }

    @Nullable
    Integer getMaxQueryComplexity() {
      return this.maxQueryComplexity;
    }

    boolean isIntrospectionEnabled() {
      return this.introspectionEnabled;
    }
  }
}
