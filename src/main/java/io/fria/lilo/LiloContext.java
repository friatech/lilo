package io.fria.lilo;

import graphql.ExecutionInput;
import graphql.GraphQL;
import graphql.introspection.IntrospectionResultToSchema;
import graphql.language.Argument;
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
import graphql.language.SelectionSet;
import graphql.language.UnionTypeDefinition;
import graphql.language.Value;
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
import static io.fria.lilo.JsonUtils.toStr;

public class LiloContext {

    private static final Set<String>  PREDEFINED_SCALARS      = Set.of("Int", "Float", "String", "Boolean", "ID");
    private static final TypeResolver INTERFACE_TYPE_RESOLVER = env -> null;
    private static final String       INTROSPECTION_REQUEST   = toStr(
        GraphQLRequest
            .builder()
            .query(GraphQLRequest.INTROSPECTION_QUERY)
            .operationName("IntrospectionQuery")
            .build()
    );

    private static final TypeResolver                       UNION_TYPE_RESOLVER = env -> {
        final Map<String, Object> result = env.getObject();

        if (!result.containsKey("__typename")) {
            throw new IllegalArgumentException("Please provide __typename for union types");
        }

        return env.getSchema().getObjectType(result.get("__typename").toString());
    };
    private final        SchemaSource[]                     schemaSources;
    private              Map<String, ProcessedSchemaSource> sourceMap;
    private              GraphQL                            graphQL;

    LiloContext(final SchemaSource... schemaSources) {
        this.schemaSources = schemaSources;
    }

    private static void addFields(final String typeName, final Map<String, Object> typeDefinition, final Map<String, Map<String, Object>> targetTypeMap) {

        final String typeDefinitionName = getName(typeDefinition);

        if (!typeDefinitionName.equals(typeName)) {
            return;
        }

        final var targetQueryTypeDefinition = targetTypeMap.get(typeDefinitionName);
        final var fields                    = getList(targetQueryTypeDefinition, "fields");
        final var targetFields              = fields.stream().collect(Collectors.toMap(f -> f.get("name").toString(), f -> f));

        getList(typeDefinition, "fields").forEach(f -> {
            if (!targetFields.containsKey(getName(f))) {
                fields.add(f);
            }
        });
    }

    private static GraphQL combine(final Map<String, TypeRuntimeWiring.Builder> typeRuntimeWiringBuilders, final Map<String, Object> combinedSchemaMap, final Map<String, ScalarTypeDefinition> scalars) {

        final var schemaMap            = Map.of("__schema", combinedSchemaMap);
        final var parser               = new SchemaParser();
        final var schemaDoc            = new IntrospectionResultToSchema().createSchemaDefinition((Map) schemaMap);
        final var typeRegistry         = parser.buildRegistry(schemaDoc);
        final var runtimeWiringBuilder = RuntimeWiring.newRuntimeWiring();
        final var dummyCoercing        = new DummyCoercing();

        typeRuntimeWiringBuilders.values().forEach(runtimeWiringBuilder::type);
        scalars
            .values()
            .stream()
            .filter(sd -> !PREDEFINED_SCALARS.contains(sd.getName()))
            .forEach(sd -> runtimeWiringBuilder.scalar(GraphQLScalarType.newScalar().name(sd.getName()).coercing(dummyCoercing).build()));

        typeRegistry
            .types()
            .values()
            .stream()
            .filter(t -> t instanceof InterfaceTypeDefinition || t instanceof UnionTypeDefinition)
            .forEach(t -> {
                if (t instanceof InterfaceTypeDefinition) {
                    runtimeWiringBuilder.type(newTypeWiring(t.getName()).typeResolver(INTERFACE_TYPE_RESOLVER));
                } else {
                    runtimeWiringBuilder.type(newTypeWiring(t.getName()).typeResolver(UNION_TYPE_RESOLVER));
                }
            });

        final RuntimeWiring   runtimeWiring   = runtimeWiringBuilder.build();
        final SchemaGenerator schemaGenerator = new SchemaGenerator();
        final GraphQLSchema   graphQLSchema   = schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);

        return GraphQL.newGraphQL(graphQLSchema).build();
    }

    private static GeneratedRequest createRequest(final DataFetchingEnvironment environment, final String fieldName) {

        final var document    = environment.getDocument();
        final var definitions = document.getDefinitions();

        if (definitions.isEmpty()) {
            throw new IllegalArgumentException("query is not in appropriate format");
        }

        final var operationDefinitionOptional = definitions
            .stream()
            .filter(d -> d instanceof OperationDefinition)
            .findFirst();

        if (operationDefinitionOptional.isEmpty()) {
            throw new IllegalArgumentException("GraphQL query should contain either query or mutation");
        }

        final var operationDefinition = (OperationDefinition) operationDefinitionOptional.get();

        final var selections = Optional
            .ofNullable(operationDefinition.getSelectionSet().getSelections())
            .orElse(List.of());

        final var queryNodeOptional = selections
            .stream()
            .filter(f -> fieldName.equals(((Field) f).getName()))
            .findFirst();

        if (queryNodeOptional.isEmpty()) {
            throw new IllegalArgumentException("found query does not match with name");
        }

        final Field queryNode = (Field) queryNodeOptional.get();

        final Set<String> usedReferences = findUsedVariables(queryNode)
            .stream()
            .map(VariableReference::getName)
            .collect(Collectors.toSet());

        final List<VariableDefinition> newVariables = operationDefinition
            .getVariableDefinitions()
            .stream()
            .filter(v -> usedReferences.contains(v.getName()))
            .collect(Collectors.toList());

        final Set<String> usedFragments = findUsedFragments(queryNode)
            .stream()
            .map(FragmentSpread::getName)
            .collect(Collectors.toSet());

        final List<Definition> fragmentDefinitions = definitions.stream()
            .filter(d -> d instanceof FragmentDefinition && usedFragments.contains(((FragmentDefinition) d).getName()))
            .collect(Collectors.toList());

        final var newOperationDefinition = operationDefinition.transform(builder -> {
            builder
                .selectionSet(new SelectionSet(List.of(queryNode)))
                .variableDefinitions(newVariables);
        });

        final ArrayList<Definition> newDefinitions = new ArrayList<>();
        newDefinitions.add(newOperationDefinition);
        newDefinitions.addAll(fragmentDefinitions);

        final Document newDocument = document.transform(builder -> {
            builder.definitions(newDefinitions);
        });

        final var query = AstPrinter.printAst(newDocument);

        return new GeneratedRequest(
            queryNode.getAlias() != null ? queryNode.getAlias() : queryNode.getName(),
            toStr(
                GraphQLRequest.builder()
                    .query(query)
                    .variables(environment.getVariables())
                    .operationName(operationDefinition.getName())
                    .build()
            )
        );
    }

    private static List<FragmentSpread> findUsedFragments(final Node node) {

        final List<FragmentSpread> usedFragments = new ArrayList<>();

        node.getChildren()
            .stream()
            .filter(n -> n instanceof SelectionSet)
            .flatMap(s -> ((SelectionSet) s).getSelections().stream())
            .forEach(s -> {
                final Node childNode = (Node) s;

                if (s instanceof FragmentSpread) {
                    usedFragments.add((FragmentSpread) childNode);
                } else {
                    usedFragments.addAll(findUsedFragments(childNode));
                }
            });

        return usedFragments;
    }

    private static List<VariableReference> findUsedVariables(final Node node) {

        final List<VariableReference> usedVariables = new ArrayList<>();

        node.getChildren()
            .stream()
            .forEach(n -> {
                final Node childNode = (Node) n;

                if (childNode instanceof Argument) {
                    final Argument argument = (Argument) n;
                    final Value argumentValue = argument.getValue();

                    if (argumentValue instanceof VariableReference) {
                        usedVariables.add((VariableReference) argumentValue);
                    }
                } else {
                    usedVariables.addAll(findUsedVariables(childNode));
                }
            });

         return usedVariables;
    }

    private static void mergeSchema(final Map<String, Object> targetSchema, final Map<String, Object> sourceSchema) {

        if (targetSchema.isEmpty()) {
            targetSchema.putAll(sourceSchema);
            return;
        }

        mergeTypeName(targetSchema, sourceSchema, "queryType");
        mergeTypeName(targetSchema, sourceSchema, "mutationType");
        mergeSchemaTypes(targetSchema, sourceSchema);
        mergeSchemaDirectives(targetSchema, sourceSchema);
    }

    private static void mergeSchemaDirectives(final Map<String, Object> targetSchema, final Map<String, Object> sourceSchema) {

        final List<Map<String, Object>> sourceSchemaDirectives = getList(sourceSchema, "directives");

        if (sourceSchemaDirectives == null) {
            return;
        }

        List<Map<String, Object>> targetSchemaDirectives = getList(targetSchema, "directives");

        if (targetSchemaDirectives == null) {
            targetSchemaDirectives = new ArrayList<>();
            targetSchema.put("directives", targetSchemaDirectives);
        }

        final var targetDirectiveMap = targetSchemaDirectives.stream().collect(Collectors.toMap(d -> d.get("name").toString(), d -> d));

        final List<Map<String, Object>> finalTargetSchemaDirectives = targetSchemaDirectives;

        sourceSchemaDirectives.forEach(sd -> {
            if (!targetDirectiveMap.containsKey(getName(sd))) {
                finalTargetSchemaDirectives.add(sd);
            }
        });
    }

    private static void mergeSchemaTypes(final Map<String, Object> targetSchema, final Map<String, Object> sourceSchema) {

        final List<Map<String, Object>> sourceSchemaTypes = getList(sourceSchema, "types");

        if (sourceSchemaTypes == null) {
            return;
        }

        List<Map<String, Object>> targetSchemaTypes = getList(targetSchema, "types");

        if (targetSchemaTypes == null) {
            targetSchemaTypes = new ArrayList<>();
            targetSchema.put("types", targetSchemaTypes);
        }

        final var targetTypeMap          = targetSchemaTypes.stream().collect(Collectors.toMap(st -> st.get("name").toString(), st -> st));
        final var finalTargetSchemaTypes = targetSchemaTypes;
        final var queryType              = getMap(sourceSchema, "queryType");
        final var queryTypeName          = queryType == null ? null : getName(queryType);
        final var mutationType           = getMap(sourceSchema, "mutationType");
        final var mutationTypeName       = mutationType == null ? null : getName(mutationType);

        sourceSchemaTypes.forEach(st -> {

            final String typeName = getName(st);

            if (!targetTypeMap.containsKey(typeName)) {
                finalTargetSchemaTypes.add(st);
                targetTypeMap.put(typeName, st);
            }

            addFields(queryTypeName, st, targetTypeMap);
            addFields(mutationTypeName, st, targetTypeMap);
        });
    }

    private static void mergeTypeName(final Map<String, Object> targetSchema, final Map<String, Object> sourceSchema, final String typeNameKey) {

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

    public GraphQL getGraphQL() {
        return this.getGraphQL(null);
    }

    public ProcessedSchemaSource processSource(final SchemaSource schemaSource, final Object context) {

        final var introspectionResponse  = schemaSource.getIntrospectionRetriever().get(this, INTROSPECTION_REQUEST, context);
        final var introspectionResult    = toMap(introspectionResponse);
        final var data                   = getMap(introspectionResult, "data");
        final var schema                 = getMap(data, "__schema");
        final var parser                 = new SchemaParser();
        final var schemaDoc              = new IntrospectionResultToSchema().createSchemaDefinition(data);
        final var typeDefinitionRegistry = parser.buildRegistry(schemaDoc);

        return new ProcessedSchemaSource(schemaSource, schema, typeDefinitionRegistry);
    }

    public void reload(final String schemaName) {

        final ProcessedSchemaSource processedSource = this.sourceMap.get(schemaName);
        final ProcessedSchemaSource updatedSource   = this.processSource(processedSource.getSchemaSource(), null);

        final HashMap<String, ProcessedSchemaSource> sourceMapClone = new HashMap<>(this.sourceMap);
        sourceMapClone.put(schemaName, updatedSource);

        this.graphQL = this.createGraphQL(sourceMapClone, null);
        this.sourceMap = sourceMapClone;
    }

    synchronized GraphQL getGraphQL(final ExecutionInput executionInput) {

        final Object localContext = executionInput == null ? null : executionInput.getLocalContext();

        if (this.graphQL == null) {
            this.sourceMap = Arrays.stream(this.schemaSources)
                .map(ss -> this.processSource(ss, localContext))
                .collect(Collectors.toMap(ss -> ss.getSchemaSource().getName(), ss -> ss));

            this.graphQL = this.createGraphQL(this.sourceMap, localContext);
        }

        return this.graphQL;
    }

    private void assignDataFetchers(final Map<String, TypeRuntimeWiring.Builder> typeRuntimeWiringBuilders, final TypeDefinitionRegistry typeDefinitionRegistry, final SchemaSource schemaSource, final String typeName) {

        if (typeName == null) {
            return;
        }

        if (!typeRuntimeWiringBuilders.containsKey(typeName)) {
            typeRuntimeWiringBuilders.put(typeName, newTypeWiring(typeName));
        }

        final var typeWiringBuilder      = typeRuntimeWiringBuilders.get(typeName);
        final var typeDefinitionOptional = typeDefinitionRegistry.getType(typeName);
        final var typeDefinition         = typeDefinitionOptional.get();

        final List<FieldDefinition> children = typeDefinition.getChildren();

        for (final FieldDefinition field : children) {

            final String fieldName = field.getName();

            typeWiringBuilder.dataFetcher(fieldName, e -> this.createDataFetcher(e, schemaSource, fieldName));
        }
    }

    private void assignHandler(final ProcessedSchemaSource ss, final Map<String, TypeRuntimeWiring.Builder> typeRuntimeWiringBuilders, final Map<String, Object> combinedSchemaMap, final Map<String, ScalarTypeDefinition> scalars) {

        final var schema                 = ss.getSchema();
        final var schemaSource           = ss.getSchemaSource();
        final var typeDefinitionRegistry = ss.getTypeDefinitionRegistry();
        final var queryType              = getMap(schema, "queryType");
        final var queryTypeName          = queryType == null ? null : getName(queryType);
        final var mutationType           = getMap(schema, "mutationType");
        final var mutationTypeName       = mutationType == null ? null : getName(mutationType);

        this.assignDataFetchers(typeRuntimeWiringBuilders, typeDefinitionRegistry, schemaSource, queryTypeName);
        this.assignDataFetchers(typeRuntimeWiringBuilders, typeDefinitionRegistry, schemaSource, mutationTypeName);

        scalars.putAll(typeDefinitionRegistry.scalars());

        mergeSchema(combinedSchemaMap, schema);
    }

    private Object createDataFetcher(final DataFetchingEnvironment environment, final SchemaSource schemaSource, final String fieldName) {

        final var request    = createRequest(environment, fieldName);
        final var queryResult     = schemaSource.getQueryRetriever().get(this, request.query, environment.getLocalContext());
        final var queryResultMap  = toMap(queryResult);
        final var queryResultData = getMap(queryResultMap, "data");

        return queryResultData.get(request.queryName);
    }

    private GraphQL createGraphQL(final Map<String, ProcessedSchemaSource> sourceMap, final Object localContext) {

        final Map<String, Object>                    combinedSchemaMap         = new HashMap<>();
        final Map<String, TypeRuntimeWiring.Builder> typeRuntimeWiringBuilders = new HashMap<>();
        final Map<String, ScalarTypeDefinition>      scalars                   = new HashMap<>();

        sourceMap.values().forEach(ss -> this.assignHandler(ss, typeRuntimeWiringBuilders, combinedSchemaMap, scalars));

        return combine(typeRuntimeWiringBuilders, combinedSchemaMap, scalars);
    }

    private static final class GeneratedRequest {

        private final String queryName;
        private final String query;

        private GeneratedRequest(final String queryName, final String query) {
            this.queryName = queryName;
            this.query = query;
        }
    }
}
