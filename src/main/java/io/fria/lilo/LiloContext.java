package io.fria.lilo;

import graphql.ExecutionInput;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.com.google.common.collect.ImmutableMap;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.introspection.IntrospectionResultToSchema;
import graphql.language.AstPrinter;
import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FieldDefinition;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.Node;
import graphql.language.OperationDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.UnionTypeDefinition;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.TypeResolver;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static io.fria.lilo.JsonUtils.getList;
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
  private Map<String, ProcessedSchemaSource> sourceMap;
  private GraphQL graphQL;

  LiloContext(
      final DataFetcherExceptionHandler dataFetcherExceptionHandler,
      final SchemaSource... schemaSources) {
    this.dataFetcherExceptionHandler = dataFetcherExceptionHandler;
    this.sourceMap =
        Arrays.stream(schemaSources)
            .collect(Collectors.toMap(SchemaSource::getName, ProcessedSchemaSource::new));
  }

  private static void addFields(
      final String typeName,
      final Map<String, Object> typeDefinition,
      final Map<String, Map<String, Object>> targetTypeMap) {

    final String typeDefinitionName = getName(typeDefinition);

    if (!typeDefinitionName.equals(typeName)) {
      return;
    }

    final var targetQueryTypeDefinition = targetTypeMap.get(typeDefinitionName);
    final var fields = getList(targetQueryTypeDefinition, "fields");
    final var targetFields =
        fields.stream().collect(Collectors.toMap(f -> f.get("name").toString(), f -> f));

    getList(typeDefinition, "fields")
        .forEach(
            f -> {
              if (!targetFields.containsKey(getName(f))) {
                fields.add(f);
              }
            });
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

  private static GraphQLQuery createRequest(
      final DataFetchingEnvironment environment, final String queryName) {

    final var document = environment.getDocument();
    final var definitions = document.getDefinitions();

    if (definitions.isEmpty()) {
      throw new IllegalArgumentException("query is not in appropriate format");
    }

    final var operationDefinitionOptional =
        definitions.stream().filter(d -> d instanceof OperationDefinition).findFirst();

    if (operationDefinitionOptional.isEmpty()) {
      throw new IllegalArgumentException("GraphQL query should contain either query or mutation");
    }

    final var operationDefinition = (OperationDefinition) operationDefinitionOptional.get();

    final var selections =
        Optional.ofNullable(operationDefinition.getSelectionSet().getSelections())
            .orElse(List.of());

    final var queryNodeOptional =
        selections.stream().filter(f -> queryName.equals(((Field) f).getName())).findFirst();

    if (queryNodeOptional.isEmpty()) {
      throw new IllegalArgumentException("found query does not match with name");
    }

    final Field queryNode = (Field) queryNodeOptional.get();
    final Set<String> usedReferenceNames = new HashSet<>();
    final Set<String> usedFragmentName = new HashSet<>();

    findUsedItems(queryNode, usedReferenceNames, usedFragmentName);

    final List<VariableDefinition> newVariables =
        operationDefinition.getVariableDefinitions().stream()
            .filter(v -> usedReferenceNames.contains(v.getName()))
            .collect(Collectors.toList());

    final List<Definition<?>> newFragmentDefinitions =
        definitions.stream()
            .filter(d -> d instanceof FragmentDefinition)
            .map(d -> (FragmentDefinition) d)
            .filter(fd -> usedFragmentName.contains(fd.getName()))
            .map(LiloContext::removeAlias)
            .collect(Collectors.toList());

    final Field newQueryNode = removeAlias(queryNode);

    final var newOperationDefinition =
        operationDefinition.transform(
            builder -> builder
                .selectionSet(new SelectionSet(List.of(newQueryNode)))
                .variableDefinitions(newVariables));

    final ArrayList<Definition> newDefinitions = new ArrayList<>();
    newDefinitions.add(newOperationDefinition);
    newDefinitions.addAll(newFragmentDefinitions);

    final Document newDocument = document.transform(builder -> builder.definitions(newDefinitions));
    final var query = AstPrinter.printAst(newDocument);
    final Map<String, Object> filteredVariables = new HashMap<>();

    environment.getVariables().entrySet().stream()
        .filter(e -> usedReferenceNames.contains(e.getKey()))
        .forEach(e -> filteredVariables.put(e.getKey(), e.getValue()));

    final String queryText =
        toStr(
            GraphQLRequest.builder()
                .query(query)
                .variables(filteredVariables)
                .operationName(operationDefinition.getName())
                .build());

    return new GraphQLQuery(queryText, operationDefinition.getOperation(), queryNode);
  }

  private static void findUsedItems(
      final Node<?> node, final Set<String> usedReferenceNames, final Set<String> usedFragmentNames) {

    node.getChildren()
        .forEach(
            n -> {
              if (n instanceof FragmentSpread) {
                usedFragmentNames.add(((FragmentSpread) n).getName());
              } else if (n instanceof VariableReference) {
                usedReferenceNames.add(((VariableReference) n).getName());
              } else {
                findUsedItems(n, usedReferenceNames, usedFragmentNames);
              }
            });
  }

  private static void mergeSchema(
      final Map<String, Object> targetSchema, final Map<String, Object> sourceSchema) {

    if (targetSchema.isEmpty()) {
      targetSchema.putAll(sourceSchema);
      return;
    }

    mergeTypeName(targetSchema, sourceSchema, "queryType");
    mergeTypeName(targetSchema, sourceSchema, "mutationType");
    mergeSchemaTypes(targetSchema, sourceSchema);
    mergeSchemaDirectives(targetSchema, sourceSchema);
  }

  private static void mergeSchemaDirectives(
      final Map<String, Object> targetSchema, final Map<String, Object> sourceSchema) {

    final List<Map<String, Object>> sourceSchemaDirectives = getList(sourceSchema, "directives");

    if (sourceSchemaDirectives == null) {
      return;
    }

    List<Map<String, Object>> targetSchemaDirectives = getList(targetSchema, "directives");

    if (targetSchemaDirectives == null) {
      targetSchemaDirectives = new ArrayList<>();
      targetSchema.put("directives", targetSchemaDirectives);
    }

    final var targetDirectiveMap =
        targetSchemaDirectives.stream()
            .collect(Collectors.toMap(d -> d.get("name").toString(), d -> d));

    final List<Map<String, Object>> finalTargetSchemaDirectives = targetSchemaDirectives;

    sourceSchemaDirectives.forEach(
        sd -> {
          if (!targetDirectiveMap.containsKey(getName(sd))) {
            finalTargetSchemaDirectives.add(sd);
          }
        });
  }

  private static void mergeSchemaTypes(
      final Map<String, Object> targetSchema, final Map<String, Object> sourceSchema) {

    final List<Map<String, Object>> sourceSchemaTypes = getList(sourceSchema, "types");

    if (sourceSchemaTypes == null) {
      return;
    }

    List<Map<String, Object>> targetSchemaTypes = getList(targetSchema, "types");

    if (targetSchemaTypes == null) {
      targetSchemaTypes = new ArrayList<>();
      targetSchema.put("types", targetSchemaTypes);
    }

    final var targetTypeMap =
        targetSchemaTypes.stream()
            .collect(Collectors.toMap(st -> st.get("name").toString(), st -> st));
    final var finalTargetSchemaTypes = targetSchemaTypes;
    final var queryType = getMap(sourceSchema, "queryType");
    final var queryTypeName = queryType == null ? null : getName(queryType);
    final var mutationType = getMap(sourceSchema, "mutationType");
    final var mutationTypeName = mutationType == null ? null : getName(mutationType);

    sourceSchemaTypes.forEach(
        st -> {
          final String typeName = getName(st);

          if (!targetTypeMap.containsKey(typeName)) {
            finalTargetSchemaTypes.add(st);
            targetTypeMap.put(typeName, st);
          }

          addFields(queryTypeName, st, targetTypeMap);
          addFields(mutationTypeName, st, targetTypeMap);
        });
  }

  private static void mergeTypeName(
      final Map<String, Object> targetSchema,
      final Map<String, Object> sourceSchema,
      final String typeNameKey) {

    final Map<String, Object> sourceSchemaQueryType = getMap(sourceSchema, typeNameKey);

    if (sourceSchemaQueryType == null) {
      return;
    }

    final Map<String, Object> targetSchemaQueryType = getMap(targetSchema, typeNameKey);

    if (targetSchemaQueryType == null) {
      targetSchema.put(typeNameKey, sourceSchemaQueryType);
    } else if (!getName(sourceSchemaQueryType).equals(getName(targetSchemaQueryType))) {
      throw new IllegalArgumentException("type name mismatches");
    }
  }

  private static FragmentDefinition removeAlias(final FragmentDefinition fragment) {

    final SelectionSet selectionSet = fragment.getSelectionSet();

    if (selectionSet == null) {
      return fragment;
    }

    final List<Selection> newSelections =
        selectionSet.getSelections().stream()
            .map(
                s -> {
                  if (s instanceof Field) {
                    return removeAlias((Field) s);
                  }

                  return s;
                })
            .collect(Collectors.toList());

    return fragment.transform(
        builder -> {
          builder.selectionSet(SelectionSet.newSelectionSet(newSelections).build()).build();
        });
  }

  private static Field removeAlias(final Field field) {

    final SelectionSet selectionSet = field.getSelectionSet();

    List<Selection> newSelections = null;

    if (selectionSet != null) {
      newSelections =
          selectionSet.getSelections().stream()
              .map(
                  s -> {
                    if (s instanceof Field) {
                      return removeAlias((Field) s);
                    }

                    return s;
                  })
              .collect(Collectors.toList());
    }

    final List<Selection> finalNewSelections = newSelections;

    return field.transform(
        builder -> {
          if (finalNewSelections != null) {
            builder
                .alias(null)
                .selectionSet(SelectionSet.newSelectionSet(finalNewSelections).build())
                .build();
          } else {
            builder.alias(null).build();
          }
        });
  }

  public GraphQL getGraphQL() {
    return this.getGraphQL(null);
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
      final TypeDefinitionRegistry typeDefinitionRegistry,
      final SchemaSource schemaSource,
      final String typeName) {

    if (typeName == null) {
      return;
    }

    if (!typeRuntimeWiringBuilders.containsKey(typeName)) {
      typeRuntimeWiringBuilders.put(typeName, newTypeWiring(typeName));
    }

    final var typeWiringBuilder = typeRuntimeWiringBuilders.get(typeName);
    final var typeDefinitionOptional = typeDefinitionRegistry.getType(typeName);
    final var typeDefinition = typeDefinitionOptional.get();

    final List<FieldDefinition> children = typeDefinition.getChildren();

    for (final FieldDefinition field : children) {

      final String fieldName = field.getName();

      typeWiringBuilder.dataFetcher(
          fieldName, e -> this.createDataFetcher(e, schemaSource, fieldName));
    }
  }

  private void assignHandler(
      final ProcessedSchemaSource ss,
      final Map<String, TypeRuntimeWiring.Builder> typeRuntimeWiringBuilders,
      final Map<String, Object> combinedSchemaMap,
      final Map<String, ScalarTypeDefinition> scalars) {

    final var queryType = getMap(ss.schema, "queryType");
    final var queryTypeName = queryType == null ? null : getName(queryType);
    final var mutationType = getMap(ss.schema, "mutationType");
    final var mutationTypeName = mutationType == null ? null : getName(mutationType);

    this.assignDataFetchers(
        typeRuntimeWiringBuilders, ss.typeDefinitionRegistry, ss.schemaSource, queryTypeName);
    this.assignDataFetchers(
        typeRuntimeWiringBuilders, ss.typeDefinitionRegistry, ss.schemaSource, mutationTypeName);

    scalars.putAll(ss.typeDefinitionRegistry.scalars());

    mergeSchema(combinedSchemaMap, ss.schema);
  }

  private Object createDataFetcher(
      final DataFetchingEnvironment environment,
      final SchemaSource schemaSource,
      final String queryName) {

    final var request = createRequest(environment, queryName);

    final var queryResult =
        schemaSource
            .getQueryRetriever()
            .get(this, schemaSource, request, environment.getLocalContext());
    final var graphQLResult = toObj(queryResult, GraphQLResult.class);

    final List<? extends GraphQLError> errors = graphQLResult.errors;

    if (errors != null && !errors.isEmpty()) {
      throw new SourceDataFetcherException(errors);
    }

    return graphQLResult.data.get(queryName);
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
