package io.fria.lilo;

import graphql.ExecutionInput;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.com.google.common.collect.ImmutableMap;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.introspection.IntrospectionResultToSchema;
import graphql.language.FieldDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.UnionTypeDefinition;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.TypeResolver;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;
import io.fria.lilo.error.LiloGraphQLError;
import io.fria.lilo.error.SourceDataFetcherException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static io.fria.lilo.JsonUtils.getMap;
import static io.fria.lilo.JsonUtils.getName;
import static io.fria.lilo.JsonUtils.toMap;
import static io.fria.lilo.JsonUtils.toObj;
import static io.fria.lilo.JsonUtils.toStr;

public class LiloContext {

  private static final Set<String> PREDEFINED_SCALARS =
      Set.of("Boolean", "Float", "Int", "ID", "String");
  private static final TypeResolver INTERFACE_TYPE_RESOLVER = env -> null;
  private static final String INTROSPECTION_REQUEST =
      toStr(
          GraphQLRequest.builder()
              .query(GraphQLRequest.INTROSPECTION_QUERY)
              .operationName("IntrospectionQuery")
              .build());

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
  private Map<String, ProcessedSchemaSource> sourceMap;
  private GraphQL graphQL;

  LiloContext(
      final DataFetcherExceptionHandler dataFetcherExceptionHandler,
      final IntrospectionFetchingMode introspectionFetchingMode,
      final SchemaSource... schemaSources) {
    this.dataFetcherExceptionHandler = Objects.requireNonNull(dataFetcherExceptionHandler);
    this.introspectionFetchingMode = Objects.requireNonNull(introspectionFetchingMode);
    this.sourceMap =
        Arrays.stream(schemaSources)
            .collect(Collectors.toMap(SchemaSource::getName, ProcessedSchemaSource::new));
  }

  private static GraphQL combine(
      final Map<String, TypeRuntimeWiring.Builder> typeRuntimeWiringBuilders,
      final Map<String, Object> combinedSchemaMap,
      final Map<String, ScalarTypeDefinition> scalars,
      final DataFetcherExceptionHandler dataFetcherExceptionHandler) {

    final var schemaMap = Map.of("__schema", combinedSchemaMap);
    final var parser = new SchemaParser();
    final var schemaDoc = new IntrospectionResultToSchema().createSchemaDefinition((Map) schemaMap);
    final var typeRegistry = parser.buildRegistry(schemaDoc);
    final var runtimeWiringBuilder = RuntimeWiring.newRuntimeWiring();
    final var dummyCoercing = new DummyCoercing();

    typeRuntimeWiringBuilders.values().forEach(runtimeWiringBuilder::type);
    scalars.values().stream()
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

    final RuntimeWiring runtimeWiring = runtimeWiringBuilder.build();
    final SchemaGenerator schemaGenerator = new SchemaGenerator();
    final GraphQLSchema graphQLSchema =
        schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);

    return GraphQL.newGraphQL(graphQLSchema)
        .defaultDataFetcherExceptionHandler(dataFetcherExceptionHandler)
        .build();
  }

  public DataFetcherExceptionHandler getDataFetcherExceptionHandler() {
    return this.dataFetcherExceptionHandler;
  }

  public GraphQL getGraphQL() {
    return this.getGraphQL(null);
  }

  public IntrospectionFetchingMode getIntrospectionFetchingMode() {
    return this.introspectionFetchingMode;
  }

  public Map<String, SchemaSource> getSchemaSources() {

    return ImmutableMap.copyOf(
        this.sourceMap.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().schemaSource)));
  }

  public void invalidate(final String schemaName) {

    if (!this.sourceMap.containsKey(schemaName)) {
      return;
    }

    final ProcessedSchemaSource processedSource = this.sourceMap.get(schemaName);
    processedSource.invalidate();

    this.graphQL = null;
  }

  public void invalidateAll() {
    this.sourceMap.values().forEach(ProcessedSchemaSource::invalidate);
    this.graphQL = null;
  }

  GraphQL getGraphQL(final ExecutionInput executionInput) {

    final Object localContext = executionInput == null ? null : executionInput.getLocalContext();

    if (this.graphQL == null) {
      final Map<String, ProcessedSchemaSource> sourceMapClone =
          this.sourceMap.values().stream()
              .map(
                  ps -> {
                    if (ps.schema == null) {
                      return this.processSource(ps.schemaSource, localContext);
                    }

                    return ps;
                  })
              .collect(Collectors.toMap(ps -> ps.schemaSource.getName(), ps -> ps));

      this.graphQL = this.createGraphQL(sourceMapClone);
      this.sourceMap = sourceMapClone;
    }

    return this.graphQL;
  }

  private void assignDataFetchers(
      final Map<String, TypeRuntimeWiring.Builder> typeRuntimeWiringBuilders,
      final ProcessedSchemaSource processedSchemaSource,
      final String typeName) {

    if (typeName == null) {
      return;
    }

    if (!typeRuntimeWiringBuilders.containsKey(typeName)) {
      typeRuntimeWiringBuilders.put(typeName, newTypeWiring(typeName));
    }

    final var schemaSource = processedSchemaSource.schemaSource;
    final var typeDefinitionRegistry = processedSchemaSource.typeDefinitionRegistry;
    final var typeWiringBuilder = typeRuntimeWiringBuilders.get(typeName);
    final var typeDefinitionOptional = typeDefinitionRegistry.getType(typeName);
    final var typeDefinition = typeDefinitionOptional.get();

    final List<FieldDefinition> children = typeDefinition.getChildren();

    for (final FieldDefinition field : children) {
      typeWiringBuilder.dataFetcher(field.getName(), e -> this.fetchData(e, schemaSource));
    }
  }

  private void assignHandler(
      final ProcessedSchemaSource processedSchemaSource,
      final Map<String, TypeRuntimeWiring.Builder> typeRuntimeWiringBuilders,
      final Map<String, Object> combinedSchemaMap,
      final Map<String, ScalarTypeDefinition> scalars) {

    final var queryType = getMap(processedSchemaSource.schema, "queryType");
    final var queryTypeName = queryType == null ? null : getName(queryType);
    final var mutationType = getMap(processedSchemaSource.schema, "mutationType");
    final var mutationTypeName = mutationType == null ? null : getName(mutationType);

    this.assignDataFetchers(typeRuntimeWiringBuilders, processedSchemaSource, queryTypeName);
    this.assignDataFetchers(typeRuntimeWiringBuilders, processedSchemaSource, mutationTypeName);

    scalars.putAll(processedSchemaSource.typeDefinitionRegistry.scalars());
    SchemaMerger.mergeSchema(combinedSchemaMap, processedSchemaSource.schema);
  }

  private GraphQL createGraphQL(final Map<String, ProcessedSchemaSource> processedSchemaSourceMap) {

    final Map<String, Object> combinedSchemaMap = new HashMap<>();
    final Map<String, TypeRuntimeWiring.Builder> typeRuntimeWiringBuilders = new HashMap<>();
    final Map<String, ScalarTypeDefinition> scalars = new HashMap<>();

    processedSchemaSourceMap
        .values()
        .forEach(
            ss -> this.assignHandler(ss, typeRuntimeWiringBuilders, combinedSchemaMap, scalars));

    return combine(
        typeRuntimeWiringBuilders, combinedSchemaMap, scalars, this.dataFetcherExceptionHandler);
  }

  private Object fetchData(
      final DataFetchingEnvironment environment, final SchemaSource schemaSource) {

    final var request = QueryTransformer.extractQuery(environment);

    final var queryResult =
        schemaSource
            .getQueryRetriever()
            .get(this, schemaSource, request, environment.getLocalContext());
    final var graphQLResult = toObj(queryResult, GraphQLResult.class);

    final List<? extends GraphQLError> errors = graphQLResult.errors;

    if (errors != null && !errors.isEmpty()) {
      throw new SourceDataFetcherException(errors);
    }

    return graphQLResult.data.values().iterator().next();
  }

  private ProcessedSchemaSource processSource(
      final SchemaSource schemaSource, final Object context) {

    final var introspectionResponse =
        schemaSource
            .getIntrospectionRetriever()
            .get(this, schemaSource, INTROSPECTION_REQUEST, context);

    final var introspectionResult = toMap(introspectionResponse);
    final var data = getMap(introspectionResult, "data");
    final var schema = getMap(data, "__schema");
    final var parser = new SchemaParser();
    final var schemaDoc = new IntrospectionResultToSchema().createSchemaDefinition(data);
    final var typeDefinitionRegistry = parser.buildRegistry(schemaDoc);

    return new ProcessedSchemaSource(schemaSource, schema, typeDefinitionRegistry);
  }

  private static class ProcessedSchemaSource {

    private final SchemaSource schemaSource;
    private Map<String, Object> schema;
    private TypeDefinitionRegistry typeDefinitionRegistry;

    ProcessedSchemaSource(final SchemaSource schemaSource) {
      this.schemaSource = schemaSource;
    }

    ProcessedSchemaSource(
        final SchemaSource schemaSource,
        final Map<String, Object> schema,
        final TypeDefinitionRegistry typeDefinitionRegistry) {

      this.schemaSource = schemaSource;
      this.schema = schema;
      this.typeDefinitionRegistry = typeDefinitionRegistry;
    }

    void invalidate() {
      this.schema = null;
    }
  }

  private static final class GraphQLResult {

    private Map<String, Object> data;
    private List<LiloGraphQLError> errors;

    @SuppressWarnings("checkstyle:WhitespaceAround")
    private GraphQLResult() {}

    public Map<String, Object> getData() {
      return this.data;
    }

    public List<LiloGraphQLError> getErrors() {
      return this.errors;
    }
  }
}
