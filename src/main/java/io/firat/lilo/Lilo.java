package io.firat.lilo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.introspection.IntrospectionResultToSchema;
import graphql.language.Document;
import graphql.language.FieldDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.TypeDefinition;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.ScalarInfo;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;
import io.firat.lilo.pojo.Directive;
import io.firat.lilo.pojo.IntrospectionResult;
import io.firat.lilo.pojo.MutationType;
import io.firat.lilo.pojo.QueryType;
import io.firat.lilo.pojo.Schema;
import io.firat.lilo.pojo.SchemaContainer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

public final class Lilo {

    private static final ObjectMapper OBJECT_MAPPER = createMapper();
    private final        GraphQL      graphQL;

    private Lilo(final GraphQL graphQL) {
        this.graphQL = graphQL;
    }

    private static ObjectMapper createMapper() {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    public static LiloBuilder builder() {
        return new LiloBuilder();
    }

    public ExecutionResult stitch(final ExecutionInput executionInput) {

        return this.graphQL.execute(executionInput);
    }

    public static class LiloBuilder {

        private final Schema                                 combinedSchema            = new Schema();
        private final Map<String, TypeRuntimeWiring.Builder> typeRuntimeWiringBuilders = new HashMap<>();
        private final Map<String, ScalarTypeDefinition>      scalars                   = new HashMap<>();

        private LiloBuilder() {
        }

        public LiloBuilder addSource(final SchemaSource schemaSource) {

            final String introspectionResponse = schemaSource.getIntrospectionRetriever().get();

            try {
                final Map<String, Object> introspectionMap = OBJECT_MAPPER.readValue(introspectionResponse, new TypeReference<>() {
                });

                final IntrospectionResult graphQLResult = OBJECT_MAPPER.readValue(introspectionResponse, IntrospectionResult.class);

                final Map<String, Object> data   = (Map<String, Object>) introspectionMap.get("data");
                final Schema              schema = graphQLResult.getData().getSchema();
                mergeSchema(this.combinedSchema, schema);

                final SchemaParser           parser                 = new SchemaParser();
                final Document               schemaDoc              = new IntrospectionResultToSchema().createSchemaDefinition(data);
                final TypeDefinitionRegistry typeDefinitionRegistry = parser.buildRegistry(schemaDoc);

                final String queryTypeName    = schema.getQueryType() == null ? null : schema.getQueryType().getName();
                final String mutationTypeName = schema.getMutationType() == null ? null : schema.getMutationType().getName();

                this.assignDataFetchers(typeDefinitionRegistry, schemaSource, queryTypeName);
                this.assignDataFetchers(typeDefinitionRegistry, schemaSource, mutationTypeName);

                this.scalars.putAll(typeDefinitionRegistry.scalars());
            } catch (final Exception e) {
                throw new IllegalArgumentException("Error while inspection read", e);
            }

            return this;
        }

        private void assignDataFetchers(final TypeDefinitionRegistry typeDefinitionRegistry, final SchemaSource schemaSource, final String typeName) {

            if (typeName == null) {
                return;
            }

            if (!this.typeRuntimeWiringBuilders.containsKey(typeName)) {
                this.typeRuntimeWiringBuilders.put(typeName, newTypeWiring(typeName));
            }

            final TypeRuntimeWiring.Builder typeWiringBuilder = this.typeRuntimeWiringBuilders.get(typeName);

            final Optional<TypeDefinition> typeDefinitionOptional = typeDefinitionRegistry.getType(typeName);
            final TypeDefinition           typeDefinition         = typeDefinitionOptional.get();
            final List<FieldDefinition>    children               = typeDefinition.getChildren();

            for (final FieldDefinition field : children) {
                typeWiringBuilder.dataFetcher(field.getName(), e -> {
                    final String s     = schemaSource.getQueryRetriever().get(e);
                    final Map    map   = OBJECT_MAPPER.readValue(s, Map.class);
                    final Map    data1 = (Map) map.get("data");

                    return data1.get(field.getName());
                });
            }
        }

        private static void mergeSchema(final Schema targetSchema, final Schema sourceSchema) {

            mergeSchemaQueryType(targetSchema, sourceSchema);
            mergeSchemaMutationType(targetSchema, sourceSchema);
            mergeSchemaTypes(targetSchema, sourceSchema);
            mergeSchemaDirectives(targetSchema, sourceSchema);
        }

        private static void mergeSchemaDirectives(final Schema targetSchema, final Schema sourceSchema) {

            final var sourceSchemaDirectives = sourceSchema.getDirectives();

            if (sourceSchemaDirectives == null) {
                return;
            }

            var targetSchemaDirectives = targetSchema.getDirectives();

            if (targetSchemaDirectives == null) {
                targetSchemaDirectives = new ArrayList<>();
                targetSchema.setDirectives(targetSchemaDirectives);
            }

            final var targetDirectiveMap = targetSchemaDirectives.stream().collect(Collectors.toMap(d -> d.getName(), d -> d));

            final List<Directive> finalTargetSchemaDirectives = targetSchemaDirectives;

            sourceSchemaDirectives.forEach(sd -> {
                if (!targetDirectiveMap.containsKey(sd.getName())) {
                    finalTargetSchemaDirectives.add(sd);
                    targetDirectiveMap.put(sd.getName(), sd);
                }
            });
        }

        private static void mergeSchemaTypes(final Schema targetSchema, final Schema sourceSchema) {

            final var sourceSchemaTypes = sourceSchema.getTypes();

            if (sourceSchemaTypes == null) {
                return;
            }

            var targetSchemaTypes = targetSchema.getTypes();

            if (targetSchemaTypes == null) {
                targetSchemaTypes = new ArrayList<>();
                targetSchema.setTypes(targetSchemaTypes);
            }

            final var                                     targetTypeMap          = targetSchemaTypes.stream().collect(Collectors.toMap(io.firat.lilo.pojo.TypeDefinition::getName, st -> st));
            final List<io.firat.lilo.pojo.TypeDefinition> finalTargetSchemaTypes = targetSchemaTypes;

            final String queryTypeName    = sourceSchema.getQueryType() == null ? null : sourceSchema.getQueryType().getName();
            final String mutationTypeName = sourceSchema.getMutationType() == null ? null : sourceSchema.getMutationType().getName();

            sourceSchemaTypes.forEach(st -> {

                if (!targetTypeMap.containsKey(st.getName())) {
                    finalTargetSchemaTypes.add(st);
                    targetTypeMap.put(st.getName(), st);
                }

                addFields(queryTypeName, st, targetTypeMap);
                addFields(mutationTypeName, st, targetTypeMap);
            });
        }

        private static void addFields(final String typeName, final io.firat.lilo.pojo.TypeDefinition typeDefinition, final Map<String, io.firat.lilo.pojo.TypeDefinition> targetTypeMap) {
            if (typeDefinition.getName().equals(typeName)) {
                final var targetQueryTypeDefinition = targetTypeMap.get(typeDefinition.getName());

                final var targetFields = targetQueryTypeDefinition.getFields().stream().collect(Collectors.toMap(f -> f.getName(), f -> f));
                typeDefinition.getFields().stream().forEach(f -> {
                    if (!targetFields.containsKey(f.getName())) {
                        targetQueryTypeDefinition.getFields().add(f);
                        targetFields.put(f.getName(), f);
                    }
                });
            }
        }

        private static void mergeSchemaQueryType(final Schema targetSchema, final Schema sourceSchema) {

            final QueryType sourceSchemaQueryType = sourceSchema.getQueryType();

            if (sourceSchemaQueryType == null) {
                return;
            }

            final QueryType targetSchemaQueryType = targetSchema.getQueryType();

            if (targetSchemaQueryType == null) {
                targetSchema.setQueryType(sourceSchemaQueryType);
            } else if (!sourceSchemaQueryType.getName().equals(targetSchemaQueryType.getName())) {
                throw new IllegalArgumentException("Query type name mismatches");
            }
        }

        private static void mergeSchemaMutationType(final Schema targetSchema, final Schema sourceSchema) {

            final MutationType sourceSchemaQueryType = sourceSchema.getMutationType();

            if (sourceSchemaQueryType == null) {
                return;
            }

            final MutationType targetSchemaQueryType = targetSchema.getMutationType();

            if (targetSchemaQueryType == null) {
                targetSchema.setMutationType(sourceSchemaQueryType);
            } else if (!sourceSchemaQueryType.getName().equals(targetSchemaQueryType.getName())) {
                throw new IllegalArgumentException("Mutation type name mismatches");
            }
        }

        public Lilo build() {

            try {
                final SchemaContainer schemaContainer = new SchemaContainer();
                schemaContainer.setSchema(this.combinedSchema);
                final String schemaText = OBJECT_MAPPER.writeValueAsString(schemaContainer);
                final Map    schemaMap  = OBJECT_MAPPER.readValue(schemaText, Map.class);

                final SchemaParser           parser               = new SchemaParser();
                final Document               schemaDoc            = new IntrospectionResultToSchema().createSchemaDefinition(schemaMap);
                final TypeDefinitionRegistry typeRegistry         = parser.buildRegistry(schemaDoc);
                final RuntimeWiring.Builder  runtimeWiringBuilder = RuntimeWiring.newRuntimeWiring();

                final DummyCoercing dummyCoercing = new DummyCoercing();

                this.typeRuntimeWiringBuilders.values().forEach(runtimeWiringBuilder::type);
                this.scalars
                    .values()
                    .stream()
                    .filter(sd -> !ScalarInfo.GRAPHQL_SPECIFICATION_SCALARS_DEFINITIONS.containsKey(sd.getName()))
                    .forEach(sd -> runtimeWiringBuilder.scalar(GraphQLScalarType.newScalar().name(sd.getName()).coercing(dummyCoercing).build()));

                final RuntimeWiring   runtimeWiring   = runtimeWiringBuilder.build();
                final SchemaGenerator schemaGenerator = new SchemaGenerator();
                final GraphQLSchema   graphQLSchema   = schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);
                final GraphQL         graphQL         = GraphQL.newGraphQL(graphQLSchema).build();

                return new Lilo(graphQL);
            } catch (final Exception e) {
                e.printStackTrace();
            }

            return null;
        }
    }
}
