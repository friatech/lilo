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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static io.fria.lilo.JsonUtils.getMap;
import static io.fria.lilo.JsonUtils.toMap;
import static io.fria.lilo.JsonUtils.toObj;
import static io.fria.lilo.JsonUtils.toStr;

public class LiloContext {

  private static final Logger LOG = LoggerFactory.getLogger(LiloContext.class);

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
      @NotNull final DataFetcherExceptionHandler dataFetcherExceptionHandler,
      @NotNull final IntrospectionFetchingMode introspectionFetchingMode,
      @NotNull final SchemaSource... schemaSources) {
    this.dataFetcherExceptionHandler = Objects.requireNonNull(dataFetcherExceptionHandler);
    this.introspectionFetchingMode = Objects.requireNonNull(introspectionFetchingMode);
    this.sourceMap =
        Arrays.stream(schemaSources)
            .collect(Collectors.toMap(SchemaSource::getName, ProcessedSchemaSource::new));
  }

  private static @NotNull GraphQL combine(
      @NotNull final Map<String, TypeRuntimeWiring.Builder> typeRuntimeWiringBuilders,
      @NotNull final Map<String, Object> combinedSchemaMap,
      @NotNull final Map<String, ScalarTypeDefinition> scalars,
      @NotNull final DataFetcherExceptionHandler dataFetcherExceptionHandler) {

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

    return ImmutableMap.copyOf(
        this.sourceMap.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().schemaSource)));
  }

  public void invalidate(@NotNull final String schemaName) {

    if (!this.sourceMap.containsKey(Objects.requireNonNull(schemaName))) {
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

  synchronized @NotNull GraphQL getGraphQL(@Nullable final ExecutionInput executionInput) {

    if (this.graphQL == null) {
      final var sourceMapClone = this.processInvalidatedSources(executionInput);
      this.graphQL = this.createGraphQL(sourceMapClone);
      this.sourceMap = sourceMapClone;
    }

    return this.graphQL;
  }

  private void assignDataFetchers(
      @NotNull final Map<String, TypeRuntimeWiring.Builder> typeRuntimeWiringBuilders,
      @NotNull final ProcessedSchemaSource processedSchemaSource,
      @Nullable final String typeName) {

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

    if (typeDefinitionOptional.isEmpty()) {
      throw new IllegalArgumentException(
          String.format("Type definition %s is not found", typeName));
    }

    final var typeDefinition = typeDefinitionOptional.get();
    final List<FieldDefinition> children = typeDefinition.getChildren();

    for (final FieldDefinition field : children) {
      typeWiringBuilder.dataFetcher(field.getName(), e -> this.fetchData(e, schemaSource));
    }
  }

  private void assignHandler(
      @NotNull final ProcessedSchemaSource processedSchemaSource,
      @NotNull final Map<String, TypeRuntimeWiring.Builder> typeRuntimeWiringBuilders,
      @NotNull final Map<String, Object> combinedSchemaMap,
      @NotNull final Map<String, ScalarTypeDefinition> scalars) {

    if (processedSchemaSource.schema == null) {
      return;
    }

    final var operationTypeNames = SchemaMerger.getOperationTypeNames(processedSchemaSource.schema);

    this.assignDataFetchers(
        typeRuntimeWiringBuilders, processedSchemaSource, operationTypeNames.getQueryTypeName());
    this.assignDataFetchers(
        typeRuntimeWiringBuilders, processedSchemaSource, operationTypeNames.getMutationTypeName());

    scalars.putAll(processedSchemaSource.typeDefinitionRegistry.scalars());
    SchemaMerger.mergeSchema(combinedSchemaMap, processedSchemaSource.schema);
  }

  private @NotNull GraphQL createGraphQL(
      @NotNull final Map<String, ProcessedSchemaSource> processedSchemaSourceMap) {

    final Map<String, Object> combinedSchemaMap = new HashMap<>();
    final Map<String, TypeRuntimeWiring.Builder> typeRuntimeWiringBuilders = new HashMap<>();
    final Map<String, ScalarTypeDefinition> scalars = new HashMap<>();

    processedSchemaSourceMap
        .values()
        .forEach(
            pss -> this.assignHandler(pss, typeRuntimeWiringBuilders, combinedSchemaMap, scalars));

    return combine(
        typeRuntimeWiringBuilders, combinedSchemaMap, scalars, this.dataFetcherExceptionHandler);
  }

  private @Nullable Object fetchData(
      @NotNull final DataFetchingEnvironment environment,
      @NotNull final SchemaSource schemaSource) {

    final var request = QueryTransformer.extractQuery(environment);

    final var queryResult =
        schemaSource
            .getQueryRetriever()
            .get(this, schemaSource, request, environment.getLocalContext());
    final var graphQLResultOptional = toObj(queryResult, GraphQLResult.class);

    if (graphQLResultOptional.isEmpty()) {
      throw new IllegalArgumentException("DataFetcher caught an empty response");
    }

    final GraphQLResult graphQLResult = graphQLResultOptional.get();
    final List<? extends GraphQLError> errors = graphQLResult.errors;

    if (errors != null && !errors.isEmpty()) {
      throw new SourceDataFetcherException(errors);
    }

    return graphQLResult.data.values().iterator().next();
  }

  private @NotNull Map<String, ProcessedSchemaSource> processInvalidatedSources(
      @Nullable final ExecutionInput executionInput) {

    final Object localContext = executionInput == null ? null : executionInput.getLocalContext();

    return this.sourceMap.values().stream()
        .peek(
            ps -> {
              if (ps.isNotProcessed()) {
                ps.process(localContext);
              }
            })
        .collect(Collectors.toMap(ps -> ps.schemaSource.getName(), ps -> ps));
  }

  private static final class GraphQLResult {

    private Map<String, Object> data;
    private List<LiloGraphQLError> errors;

    public @NotNull Map<String, Object> getData() {
      return this.data;
    }

    public @NotNull List<LiloGraphQLError> getErrors() {
      return this.errors;
    }
  }

  private final class ProcessedSchemaSource {

    private final SchemaSource schemaSource;
    private Map<String, Object> schema;
    private TypeDefinitionRegistry typeDefinitionRegistry;

    private ProcessedSchemaSource(@NotNull final SchemaSource schemaSource) {
      this.schemaSource = Objects.requireNonNull(schemaSource);
    }

    private void invalidate() {
      this.schema = null;
    }

    private boolean isNotProcessed() {
      return this.schema == null;
    }

    private void process(@Nullable final Object localContext) {

      final String introspectionResponse;

      try {
        introspectionResponse =
            this.schemaSource
                .getIntrospectionRetriever()
                .get(LiloContext.this, this.schemaSource, INTROSPECTION_REQUEST, localContext);
      } catch (final Exception e) {
        LOG.error("Could not load introspection for {}", this.schemaSource.getName());
        LOG.debug("Introspection fetching exception", e);
        return;
      }

      final var introspectionResultOptional = toMap(introspectionResponse);

      if (introspectionResultOptional.isEmpty()) {
        throw new IllegalArgumentException("Introspection response is empty");
      }

      final var dataOptional = getMap(introspectionResultOptional.get(), "data");

      if (dataOptional.isEmpty()) {
        throw new IllegalArgumentException(
            "Introspection response is not valid, requires data section");
      }

      final var schemaOptional = getMap(dataOptional.get(), "__schema");

      if (schemaOptional.isEmpty()) {
        throw new IllegalArgumentException(
            "Introspection response is not valid, requires __schema section");
      }

      final var schemaDoc =
          new IntrospectionResultToSchema().createSchemaDefinition(dataOptional.get());

      this.typeDefinitionRegistry = new SchemaParser().buildRegistry(schemaDoc);
      this.schema = schemaOptional.get();
    }
  }
}
