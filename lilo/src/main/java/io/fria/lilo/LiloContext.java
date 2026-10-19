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

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

import graphql.ExecutionInput;
import graphql.GraphQL;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.instrumentation.Instrumentation;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.UnionTypeDefinition;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.TypeResolver;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.TypeDefinitionRegistry;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LiloContext {

  private static final Set<String> PREDEFINED_SCALARS =
      Set.of("Boolean", "Float", "Int", "ID", "String");
  private static final TypeResolver INTERFACE_TYPE_RESOLVER =
      typenameTypeResolver("Please provide __typename for interface types");
  private static final TypeResolver UNION_TYPE_RESOLVER =
      typenameTypeResolver("Please provide __typename for union types");

  private final DataFetcherExceptionHandler dataFetcherExceptionHandler;
  private final IntrospectionFetchingMode introspectionFetchingMode;
  private final boolean retrySchemaLoad;
  private final Instrumentation instrumentation;

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
      final @NotNull SchemaSource... schemaSources) {
    this.dataFetcherExceptionHandler = Objects.requireNonNull(dataFetcherExceptionHandler);
    this.introspectionFetchingMode = Objects.requireNonNull(introspectionFetchingMode);
    this.retrySchemaLoad = retrySchemaLoad;
    this.instrumentation = instrumentation;
    this.sourceMap = toSourceMap(Arrays.stream(schemaSources));
  }

  private static @NotNull RuntimeWiring finalizeWiring(
      final @NotNull TypeDefinitionRegistry typeRegistry,
      final @NotNull RuntimeWiring.Builder runtimeWiringBuilder,
      final @NotNull Set<FieldCoordinates> remoteRootFields) {

    // Applies only to fields without an explicit data fetcher, so the root fetchers wired by
    // SchemaMerger keep precedence; scalars and type resolvers are wired independently of it
    runtimeWiringBuilder.codeRegistry(
        GraphQLCodeRegistry.newCodeRegistry()
            .defaultDataFetcher(new ResultKeyDataFetcherFactory(remoteRootFields)));

    final var dummyCoercing = new DummyCoercing();

    typeRegistry.scalars().values().stream()
        .filter(sd -> !PREDEFINED_SCALARS.contains(sd.getName()))
        .forEach(
            sd ->
                runtimeWiringBuilder.scalar(
                    GraphQLScalarType.newScalar()
                        .name(sd.getName())
                        .coercing(dummyCoercing)
                        .build()));

    typeRegistry.types().values().stream()
        .filter(t -> t instanceof InterfaceTypeDefinition || t instanceof UnionTypeDefinition)
        .forEach(
            t -> {
              if (t instanceof InterfaceTypeDefinition) {
                runtimeWiringBuilder.type(
                    newTypeWiring(t.getName()).typeResolver(INTERFACE_TYPE_RESOLVER));
              } else {
                runtimeWiringBuilder.type(
                    newTypeWiring(t.getName()).typeResolver(UNION_TYPE_RESOLVER));
              }
            });

    return runtimeWiringBuilder.build();
  }

  /**
   * Resolves an interface or union value by the {@code __typename} its source returned. The query
   * sent to a source always selects {@code __typename} on abstract types (see {@link
   * QueryTransformer}), so a missing value means the source did not honour the selection.
   */
  private static @NotNull TypeResolver typenameTypeResolver(final @NotNull String missingMessage) {

    return env -> {
      final Object source = env.getObject();
      final Object typeName = source instanceof Map ? ((Map<?, ?>) source).get("__typename") : null;

      if (typeName == null) {
        throw new IllegalArgumentException(missingMessage);
      }

      return env.getSchema().getObjectType(typeName.toString());
    };
  }

  /** Root fields served by remote sources; their payloads are keyed by result key. */
  private static @NotNull Set<FieldCoordinates> remoteRootFields(
      final @NotNull List<SchemaSource> schemaSourceList) {

    final Set<FieldCoordinates> remoteRootFields = new HashSet<>();

    for (final SchemaSource schemaSource : schemaSourceList) {
      if (!(schemaSource instanceof RemoteSchemaSource) || schemaSource.isSchemaNotLoaded()) {
        continue;
      }

      final TypeDefinitionRegistry registry = schemaSource.getTypeDefinitionRegistry();
      final SchemaMerger.OperationTypeNames names = SchemaMerger.getOperationTypeNames(registry);

      Stream.of(names.getQuery(), names.getMutation(), names.getSubscription())
          .filter(Objects::nonNull)
          .map(registry::getTypeOrNull)
          .filter(t -> t instanceof ObjectTypeDefinition)
          .map(t -> (ObjectTypeDefinition) t)
          .forEach(
              t ->
                  t.getFieldDefinitions()
                      .forEach(
                          f ->
                              remoteRootFields.add(
                                  FieldCoordinates.coordinates(t.getName(), f.getName()))));
    }

    return remoteRootFields;
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
      build = this.loadSources(localContext).thenApply(this::createGraphQL);
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

  private @NotNull GraphQL createGraphQL(final @NotNull List<SchemaSource> schemaSourceList) {

    final TypeDefinitionRegistry combinedRegistry = new TypeDefinitionRegistry();
    final RuntimeWiring.Builder runtimeWiringBuilder = RuntimeWiring.newRuntimeWiring();
    SchemaMerger.mergeSchemas(schemaSourceList, combinedRegistry, runtimeWiringBuilder);

    final RuntimeWiring runtimeWiring =
        finalizeWiring(combinedRegistry, runtimeWiringBuilder, remoteRootFields(schemaSourceList));

    final GraphQLSchema graphQLSchema =
        new SchemaGenerator().makeExecutableSchema(combinedRegistry, runtimeWiring);

    final GraphQL.Builder builder =
        GraphQL.newGraphQL(graphQLSchema)
            .defaultDataFetcherExceptionHandler(this.dataFetcherExceptionHandler);

    if (this.instrumentation != null) {
      builder.instrumentation(this.instrumentation);
    }

    return builder.build();
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
}
