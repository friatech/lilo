package io.firat.lilo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionInput;
import graphql.introspection.IntrospectionResultToSchema;
import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FieldDefinition;
import graphql.language.OperationDefinition;
import graphql.language.SelectionSet;
import graphql.language.TypeDefinition;
import graphql.parser.Parser;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class Lilo {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Map<String, SchemaSource> querySchemaMap = new ConcurrentHashMap<>();

    public static LiloBuilder builder() {
        return new LiloBuilder(new Lilo());
    }

    public HashMap<Object, Object> stitch(final ExecutionInput executionInput) {

        final String           query       = executionInput.getQuery();
        final Document         document    = new Parser().parseDocument(query);
        final List<Definition> definitions = document.getDefinitions();

        final HashMap<Object, Object> combinedMap = new HashMap<>();
        combinedMap.put("data", new HashMap<String, Object>());
        combinedMap.put("errors", new ArrayList<>());

        definitions.forEach(definition -> {
            OperationDefinition operationDefinition = (OperationDefinition) definition;
            final SelectionSet  selectionSet        = operationDefinition.getSelectionSet();
            selectionSet.getSelections().forEach(selection -> {
                final Field        field        = (Field) selection;
                final SchemaSource schemaSource = this.querySchemaMap.get(field.getName());
                final String       queryResult  = schemaSource.getQueryRetriever().get(schemaSource.getUrl(), field);

                Map queryMap = null;
                try {
                    queryMap = OBJECT_MAPPER.readValue(queryResult, Map.class);
                } catch (final JsonProcessingException e) {
                    e.printStackTrace();
                }

                ((Map<String, Object>) combinedMap.get("data")).putAll((Map<String, Object>) queryMap.get("data"));
                ((List<Object>) combinedMap.get("errors")).addAll((List<Object>) queryMap.get("errors"));
            });
        });

        return combinedMap;
    }

//    public Mono<String> stitch(final String graphqlRequest) {
//
//        final Flux<String>       empty = Flux.empty();
//        final List<Mono<String>> monos = new ArrayList<>();
//
//        try {
//            final Map map = new ObjectMapper().readValue(graphqlRequest, Map.class);
//            final String           query       = (String) map.get("query");
//            final Document         document    = new Parser().parseDocument(query);
//            final List<Definition> definitions = document.getDefinitions();
//            document.getChildren().forEach(node -> {
//                node.getChildren().forEach(o -> {
//                    final SelectionSet selectionSet = (SelectionSet) o;
//                    selectionSet.getSelections().forEach(selection -> {
//                        final Field        selection1   = (Field) selection;
//                        final SchemaSource schemaSource = this.schemaSourceMap.get(selection1.getName());
//                        final Mono<String> stringMono = schemaSource.getQueryRetriever().get(schemaSource.getUrl(), selection1);
//                        monos.add(stringMono);
//                    });
//                });
//            });
//        } catch (final JsonProcessingException e) {
//            e.printStackTrace();
//        }
//
//        return Mono.zip(monos, new Function<Object[], String>() {
//            @Override
//            public String apply(final Object[] objects) {
//                return ((String) objects[0]) + ((String)objects[1]);
//            }
//        });
//    }

    public static class LiloBuilder {

        private final Lilo lilo;

        public LiloBuilder(final Lilo lilo) {
            this.lilo = lilo;
        }

        public LiloBuilder addSource(final SchemaSource schemaSource) {

            final String introspectionResponse = schemaSource.getIntrospectionRetriever().get(schemaSource.getUrl());

            try {
                final Map<String, Object> introspectionMap = OBJECT_MAPPER.readValue(introspectionResponse, new TypeReference<>() {
                });

                final Map<String, Object>    data                   = (Map<String, Object>) introspectionMap.get("data");
                final SchemaParser           parser                 = new SchemaParser();
                final Document               schemaDoc              = new IntrospectionResultToSchema().createSchemaDefinition(data);
                final TypeDefinitionRegistry typeDefinitionRegistry = parser.buildRegistry(schemaDoc);

                final Optional<TypeDefinition> queryOptional = typeDefinitionRegistry.getType("Query");

                if (queryOptional.isEmpty()) {
                    return this;
                }

                final TypeDefinition        typeDefinition = queryOptional.get();
                final List<FieldDefinition> children       = typeDefinition.getChildren();

                for (final FieldDefinition child : children) {
                    this.lilo.querySchemaMap.put(child.getName(), schemaSource);
                }

            } catch (final Exception e) {
                throw new IllegalArgumentException("Error while inspection read", e);
            }

            return this;
        }

        public Lilo build() {
            return this.lilo;
        }
    }
}
