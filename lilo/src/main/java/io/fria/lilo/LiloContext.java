package io.fria.lilo;

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
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

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
  private Map<String, BaseSchemaSource> sourceMap;
  private GraphQL graphQL;

  LiloContext(
      final @NotNull DataFetcherExceptionHandler dataFetcherExceptionHandler,
      final @NotNull IntrospectionFetchingMode introspectionFetchingMode,
      final @NotNull BaseSchemaSource... schemaSources) {
    this.dataFetcherExceptionHandler = Objects.requireNonNull(dataFetcherExceptionHandler);
    this.introspectionFetchingMode = Objects.requireNonNull(introspectionFetchingMode);
    this.sourceMap =
        Arrays.stream(schemaSources).collect(Collectors.toMap(SchemaSource::getName, ss -> ss));
  }

  private static RuntimeWiring finalizeWiring(
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

  public @NotNull DataFetcherExceptionHandler getDataFetcherExceptionHandler() {
    return this.dataFetcherExceptionHandler;
  }

  public @NotNull GraphQL getGraphQL() {
    return this.getGraphQL(null);
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

    this.graphQL = null;
  }

  public void invalidateAll() {
    this.sourceMap.values().forEach(SchemaSource::invalidate);
    this.graphQL = null;
  }

  synchronized @NotNull GraphQL getGraphQL(final @Nullable ExecutionInput executionInput) {

    if (this.graphQL == null) {
      final var sourceMapClone = this.loadSources(executionInput);
      this.graphQL = this.createGraphQL(sourceMapClone);
      this.sourceMap = sourceMapClone;
    }

    return this.graphQL;
  }

  private @NotNull GraphQL createGraphQL(
      final @NotNull Map<String, BaseSchemaSource> schemaSourceMap) {

    final TypeDefinitionRegistry combinedRegistry = new TypeDefinitionRegistry();
    final RuntimeWiring.Builder runtimeWiringBuilder = RuntimeWiring.newRuntimeWiring();
    SchemaMerger.mergeSchemas(schemaSourceMap.values(), combinedRegistry, runtimeWiringBuilder);

    final RuntimeWiring runtimeWiring = finalizeWiring(combinedRegistry, runtimeWiringBuilder);

    final GraphQLSchema graphQLSchema =
        new SchemaGenerator().makeExecutableSchema(combinedRegistry, runtimeWiring);

    return GraphQL.newGraphQL(graphQLSchema)
        .defaultDataFetcherExceptionHandler(this.dataFetcherExceptionHandler)
        .build();
  }

  private @NotNull Map<String, BaseSchemaSource> loadSources(
      final @Nullable ExecutionInput executionInput) {

    final Object localContext = executionInput == null ? null : executionInput.getLocalContext();

    return this.sourceMap.values().stream()
        .peek(
            ss -> {
              if (!ss.isSchemaLoaded()) {
                ss.loadSchema(LiloContext.this, localContext);
              }
            })
        .collect(Collectors.toMap(SchemaSource::getName, ss -> ss));
  }
}
