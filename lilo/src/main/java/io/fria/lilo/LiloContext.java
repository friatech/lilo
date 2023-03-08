package io.fria.lilo;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

import graphql.ExecutionInput;
import graphql.GraphQL;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.UnionTypeDefinition;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.TypeResolver;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.TypeDefinitionRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LiloContext {

  private static final Set<String> PREDEFINED_SCALARS =
      Set.of("Boolean", "Float", "Int", "ID", "String");
  private static final TypeResolver INTERFACE_TYPE_RESOLVER = env -> null;

  private static final TypeResolver UNION_TYPE_RESOLVER =
      env -> {
        final Map<String, Object> result = env.getObject();

        if (!result.containsKey("__typename")) {
          throw new IllegalArgumentException("Please provide __typename for union types");
        }

        return env.getSchema().getObjectType(result.get("__typename").toString());
      };

  private final DataFetcherExceptionHandler dataFetcherExceptionHandler;
  private final IntrospectionFetchingMode introspectionFetchingMode;
  private final boolean retrySchemaLoad;
  private Map<String, SchemaSource> sourceMap;
  private GraphQL graphQL;
  private boolean schemasAreNotLoaded = true;

  LiloContext(
      final @NotNull DataFetcherExceptionHandler dataFetcherExceptionHandler,
      final @NotNull IntrospectionFetchingMode introspectionFetchingMode,
      final boolean retrySchemaLoad,
      final @NotNull SchemaSource... schemaSources) {
    this.dataFetcherExceptionHandler = Objects.requireNonNull(dataFetcherExceptionHandler);
    this.introspectionFetchingMode = Objects.requireNonNull(introspectionFetchingMode);
    this.retrySchemaLoad = retrySchemaLoad;
    this.sourceMap = toSourceMap(Arrays.stream(schemaSources));
  }

  private static @NotNull RuntimeWiring finalizeWiring(
      final @NotNull TypeDefinitionRegistry typeRegistry,
      final @NotNull RuntimeWiring.Builder runtimeWiringBuilder) {

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

  private static @NotNull Map<String, SchemaSource> toSourceMap(
      final @NotNull Stream<SchemaSource> schemaSourcesStream) {
    return schemaSourcesStream.collect(Collectors.toMap(SchemaSource::getName, ss -> ss));
  }

  public @NotNull DataFetcherExceptionHandler getDataFetcherExceptionHandler() {
    return this.dataFetcherExceptionHandler;
  }

  public @NotNull GraphQL getGraphQL() {
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

    if (!this.sourceMap.containsKey(Objects.requireNonNull(schemaName))) {
      return;
    }

    final SchemaSource schemaSource = this.sourceMap.get(schemaName);
    schemaSource.invalidate();

    this.schemasAreNotLoaded = true;
  }

  public void invalidateAll() {
    this.sourceMap.values().forEach(SchemaSource::invalidate);
    this.schemasAreNotLoaded = true;
  }

  public @NotNull CompletableFuture<GraphQL> reloadGraphQL(final @Nullable Object localContext) {

    return this.loadSources(localContext)
        .thenApply(
            sourceMapClone -> {
              LiloContext.this.graphQL = LiloContext.this.createGraphQL(sourceMapClone);
              LiloContext.this.sourceMap = toSourceMap(sourceMapClone.stream());

              return LiloContext.this.graphQL;
            });
  }

  @NotNull
  GraphQL getGraphQL(final @Nullable ExecutionInput executionInput) {

    try {
      return this.getGraphQLAsync(executionInput).get();
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  CompletableFuture<GraphQL> getGraphQLAsync(final @Nullable ExecutionInput executionInput) {

    if (this.schemasAreNotLoaded()) {
      return this.reloadGraphQL(executionInput == null ? null : executionInput.getLocalContext());
    }

    return CompletableFuture.supplyAsync(() -> this.graphQL);
  }

  private @NotNull GraphQL createGraphQL(final @NotNull List<SchemaSource> schemaSourceList) {

    final TypeDefinitionRegistry combinedRegistry = new TypeDefinitionRegistry();
    final RuntimeWiring.Builder runtimeWiringBuilder = RuntimeWiring.newRuntimeWiring();
    SchemaMerger.mergeSchemas(schemaSourceList, combinedRegistry, runtimeWiringBuilder);

    final RuntimeWiring runtimeWiring = finalizeWiring(combinedRegistry, runtimeWiringBuilder);

    final GraphQLSchema graphQLSchema =
        new SchemaGenerator().makeExecutableSchema(combinedRegistry, runtimeWiring);

    return GraphQL.newGraphQL(graphQLSchema)
        .defaultDataFetcherExceptionHandler(this.dataFetcherExceptionHandler)
        .build();
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

  private boolean schemasAreNotLoaded() {

    if (this.schemasAreNotLoaded) {

      final Collection<SchemaSource> sources = this.sourceMap.values();
      final long notLoadedCount =
          this.sourceMap.values().stream().filter(SchemaSource::isSchemaNotLoaded).count();
      final int sourceCount = sources.size();

      if (notLoadedCount == sourceCount) { // none of them are loaded
        this.schemasAreNotLoaded = true;
      } else if (notLoadedCount == 0) { // all of them are loaded
        this.schemasAreNotLoaded = false;
      } else { // if partially loaded then check the retry option
        this.schemasAreNotLoaded = this.retrySchemaLoad;
      }
    }

    return this.schemasAreNotLoaded;
  }
}
