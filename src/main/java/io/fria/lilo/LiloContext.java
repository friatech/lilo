package io.fria.lilo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.GraphQL;
import graphql.introspection.IntrospectionQuery;
import graphql.introspection.IntrospectionResultToSchema;
import graphql.language.AstPrinter;
import graphql.language.Definition;
import graphql.language.Field;
import graphql.language.FieldDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.OperationDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.Selection;
import graphql.language.TypeDefinition;
import graphql.language.UnionTypeDefinition;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.TypeResolver;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.ScalarInfo;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

public class LiloContext {

    private static final ObjectMapper                       OBJECT_MAPPER           = createMapper();
    private static final TypeResolver                       INTERFACE_TYPE_RESOLVER = env -> null;
    private static final String                             INTROSPECTION_REQUEST   = createRequest(IntrospectionQuery.INTROSPECTION_QUERY, null, "IntrospectionQuery");
    private static final TypeResolver                       UNION_TYPE_RESOLVER     = env -> {
        final Map<String, Object> result = env.getObject();

        if (!result.containsKey("__typename")) {
            throw new IllegalArgumentException("Please provide __typename for union types");
        }

        return env.getSchema().getObjectType(result.get("__typename").toString());
    };
    private              Map<String, ProcessedSchemaSource> sourceMap;
    private              GraphQL                            graphQL;

    LiloContext(final SchemaSource... schemaSources) {

        final Map<String, ProcessedSchemaSource> sourceMap = Arrays.stream(schemaSources)
            .map(this::processSource)
            .collect(Collectors.toMap(ss -> ss.getSchemaSource().getName(), ss -> ss));

        this.graphQL = this.createGraphQL(sourceMap);
        this.sourceMap = sourceMap;
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
            .filter(sd -> !ScalarInfo.GRAPHQL_SPECIFICATION_SCALARS_DEFINITIONS.containsKey(sd.getName()))
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

    private static ObjectMapper createMapper() {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    private static String createRequest(final String query, final Map<String, Object> variables, final String operationName) {

        try {

            final HashMap<Object, Object> requestMap = new HashMap<>();
            requestMap.put("query", query);

            if (variables != null) {
                requestMap.put("variables", variables);
            }

            if (operationName != null) {
                requestMap.put("operationName", operationName);
            }

            return OBJECT_MAPPER.writeValueAsString(requestMap);
        } catch (final IOException e) {
            throw new IllegalArgumentException("Could not create introspection request");
        }
    }

    private static List<Map<String, Object>> getList(final Map<String, Object> map, final String key) {
        return (List<Map<String, Object>>) map.get(key);
    }

    private static Map<String, Object> getMap(final Map<String, Object> map, final String key) {
        return (Map<String, Object>) map.get(key);
    }

    private static String getName(final Map<String, Object> map) {
        return (String) map.get("name");
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
        return this.graphQL;
    }

    public ProcessedSchemaSource processSource(final SchemaSource schemaSource) {

        try {
            final String introspectionResponse = schemaSource.getIntrospectionRetriever().get(this, INTROSPECTION_REQUEST);
            final Map<String, Object> introspectionResult = OBJECT_MAPPER.readValue(introspectionResponse, new TypeReference<>() {
            });

            final var data                   = getMap(introspectionResult, "data");
            final var schema                 = getMap(data, "__schema");
            final var parser                 = new SchemaParser();
            final var schemaDoc              = new IntrospectionResultToSchema().createSchemaDefinition(data);
            final var typeDefinitionRegistry = parser.buildRegistry(schemaDoc);

            return new ProcessedSchemaSource(schemaSource, schema, typeDefinitionRegistry);
        } catch (final Exception e) {
            throw new IllegalArgumentException("Error while inspection read", e);
        }
    }

    public void reload(final String schemaName) {

        final ProcessedSchemaSource processedSource = this.sourceMap.get(schemaName);
        final ProcessedSchemaSource updatedSource   = this.processSource(processedSource.getSchemaSource());

        final HashMap<String, ProcessedSchemaSource> sourceMapClone = new HashMap<>(this.sourceMap);
        sourceMapClone.put(schemaName, updatedSource);

        this.graphQL = this.createGraphQL(sourceMapClone);
        this.sourceMap = sourceMapClone;
    }

    private void assignDataFetchers(final Map<String, TypeRuntimeWiring.Builder> typeRuntimeWiringBuilders, final TypeDefinitionRegistry typeDefinitionRegistry, final SchemaSource schemaSource, final String typeName) {

        if (typeName == null) {
            return;
        }

        if (!typeRuntimeWiringBuilders.containsKey(typeName)) {
            typeRuntimeWiringBuilders.put(typeName, newTypeWiring(typeName));
        }

        final TypeRuntimeWiring.Builder typeWiringBuilder = typeRuntimeWiringBuilders.get(typeName);

        final Optional<TypeDefinition> typeDefinitionOptional = typeDefinitionRegistry.getType(typeName);
        final TypeDefinition           typeDefinition         = typeDefinitionOptional.get();
        final List<FieldDefinition>    children               = typeDefinition.getChildren();

        for (final FieldDefinition field : children) {

            final String fieldName = field.getName();

            typeWiringBuilder.dataFetcher(fieldName, e -> {
                final List<Definition> definitions = e.getDocument().getDefinitions();

                if (definitions.isEmpty()) {
                    throw new IllegalArgumentException("query is not in appropriate format");
                }

                final OperationDefinition definition = (OperationDefinition) definitions.get(0);
                final Optional<Selection> queryNodeOptional = definition.getSelectionSet().getSelections()
                    .stream().filter(f -> fieldName.equals(((Field) f).getName()))
                    .findFirst();

                if (queryNodeOptional.isEmpty()) {
                    throw new IllegalArgumentException("query is not in appropriate format");
                }

                final Field  subField     = (Field) queryNodeOptional.get();
                final String subFieldText = AstPrinter.printAst(subField);

                final OperationDefinition.Operation operation = definition.getOperation();
                final String                        query;

                if (operation.equals(OperationDefinition.Operation.MUTATION)) {
                    query = "mutation {\n" + subFieldText + "\n}";
                } else if (operation.equals(OperationDefinition.Operation.QUERY)) {
                    query = "query {\n" + subFieldText + "\n}";
                } else {
                    throw new IllegalArgumentException("Unsupported operation type");
                }

                final String requestQuery = createRequest(query, e.getVariables(), null);
                final String queryResult  = schemaSource.getQueryRetriever().get(this, requestQuery, e.getLocalContext());
                final Map<String, Object> queryResultMap = OBJECT_MAPPER.readValue(queryResult, new TypeReference<>() {
                });
                final Map<String, Object> queryResultData = getMap(queryResultMap, "data");

                return queryResultData.get(fieldName);
            });
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

    private GraphQL createGraphQL(final Map<String, ProcessedSchemaSource> sourceMap) {

        final Map<String, Object>                    combinedSchemaMap         = new HashMap<>();
        final Map<String, TypeRuntimeWiring.Builder> typeRuntimeWiringBuilders = new HashMap<>();
        final Map<String, ScalarTypeDefinition>      scalars                   = new HashMap<>();

        sourceMap.values().forEach(ss -> this.assignHandler(ss, typeRuntimeWiringBuilders, combinedSchemaMap, scalars));

        return combine(typeRuntimeWiringBuilders, combinedSchemaMap, scalars);
    }
}
