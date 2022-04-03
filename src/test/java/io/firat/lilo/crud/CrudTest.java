package io.firat.lilo.crud;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.TypeResolutionEnvironment;
import graphql.introspection.IntrospectionQuery;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.TypeResolver;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import io.firat.lilo.DummyCoercing;
import io.firat.lilo.IntrospectionRetriever;
import io.firat.lilo.Lilo;
import io.firat.lilo.QueryRetriever;
import io.firat.lilo.SchemaSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

@ExtendWith(MockitoExtension.class)
class CrudTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String       SCHEMA1_NAME  = "project1";
    private static final String       SCHEMA2_NAME  = "project2";
    private static final Map<String, Object> RESULT_MAP = Map.of("id", 1, "name", "John", "age", 34, "enabled", true, "role", "ADMIN");

    @Mock
    private IntrospectionRetriever introspection1Retriever;

    @Mock
    private IntrospectionRetriever introspection2Retriever;

    @Mock
    private QueryRetriever query1Retriever;

    @Mock
    private QueryRetriever query2Retriever;

    private static RuntimeWiring createCombinedWiring() {

        return RuntimeWiring.newRuntimeWiring()
            .type(
                newTypeWiring("Queries")
                    .dataFetcher("get", env -> RESULT_MAP)
                    .dataFetcher("list", env -> List.of(RESULT_MAP))
            )
            .type(
                newTypeWiring("Mutations")
                    .dataFetcher("create", env -> RESULT_MAP)
                    .dataFetcher("delete", env -> null)
            )
            .type(newTypeWiring("UserRequest").typeResolver(env -> null))
            .scalar(GraphQLScalarType.newScalar().name("Void").coercing(new DummyCoercing()).build())
            .build();
    }

    private static GraphQL createGraphQL(final String schemaDefinitionPath, final RuntimeWiring runtimeWiring) throws IOException {

        final InputStream resourceAsStream = CrudTest.class.getResourceAsStream(schemaDefinitionPath);
        Assertions.assertNotNull(resourceAsStream);

        final var schemaDefinitionText = new String(resourceAsStream.readAllBytes());
        final var typeRegistry         = new SchemaParser().parse(schemaDefinitionText);
        final var schemaGenerator      = new SchemaGenerator();
        final var graphQLSchema        = schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);

        return GraphQL.newGraphQL(graphQLSchema).build();
    }

    private static RuntimeWiring createProject1Wiring() {

        return RuntimeWiring.newRuntimeWiring()
            .type(
                newTypeWiring("Queries")
                    .dataFetcher("get", env -> RESULT_MAP)
            )
            .type(
                newTypeWiring("Mutations")
                    .dataFetcher("create", env -> RESULT_MAP)
            )
            .type(newTypeWiring("UserRequest").typeResolver(env -> null))
            .build();
    }

    private static RuntimeWiring createProject2Wiring() {

        return RuntimeWiring.newRuntimeWiring()
            .type(
                newTypeWiring("Queries")
                    .dataFetcher("list", env -> List.of(RESULT_MAP))
            )
            .type(
                newTypeWiring("Mutations")
                    .dataFetcher("delete", env -> null)
            )
            .type(newTypeWiring("UserRequest").typeResolver(env -> null))
            .scalar(GraphQLScalarType.newScalar().name("Void").coercing(new DummyCoercing()).build())
            .build();
    }

    private static SchemaSource createSchemaSource(
        final String schemaName,
        final IntrospectionRetriever introspectionRetriever,
        final QueryRetriever queryRetriever
    ) {

        return SchemaSource.builder()
            .name(schemaName)
            .introspectionRetriever(introspectionRetriever)
            .queryRetriever(queryRetriever)
            .build();
    }

    private static String runQuery(final GraphQL graphQL, final String query) throws JsonProcessingException {

        final ExecutionInput executionInput = ExecutionInput.newExecutionInput()
            .query(query)
            .build();

        return OBJECT_MAPPER.writeValueAsString(graphQL.execute(executionInput));
    }

    @Test
    void stitchingQueryTest() throws IOException {

        // Combined result -----------------------------------------------------
        final Map<String, Object> expected = Map.of("get", RESULT_MAP, "list", List.of(RESULT_MAP));

        final ExecutionInput executionInput = ExecutionInput.newExecutionInput()
            .query("""
                query {
                    get(id: 1) {
                        id
                        name
                        age
                        enabled
                        role
                    }
                    list {
                        id
                        name
                        age
                        enabled
                        role
                    }
                }
                """)
            .build();

        final GraphQL combinedGraphQL = createGraphQL("/crud/combined.graphqls", createCombinedWiring());

        final ExecutionResult result = combinedGraphQL.execute(executionInput);
        Assertions.assertEquals(expected, result.getData());

        // Stitching result ----------------------------------------------------
        final GraphQL project1GraphQL = createGraphQL("/crud/project1.graphqls", createProject1Wiring());
        final GraphQL project2GraphQL = createGraphQL("/crud/project2.graphqls", createProject2Wiring());

        Mockito.when(this.introspection1Retriever.get())
            .thenReturn(runQuery(project1GraphQL, IntrospectionQuery.INTROSPECTION_QUERY));

        Mockito.when(this.introspection2Retriever.get())
            .thenReturn(runQuery(project2GraphQL, IntrospectionQuery.INTROSPECTION_QUERY));

        final String project1Query = """
            query {
                get(id: 1) {
                    id
                    name
                    age
                    enabled
                    role
                }
            }
            """;

        Mockito.when(this.query1Retriever.get(Mockito.any()))
            .thenReturn(runQuery(project1GraphQL, project1Query));

        final String project2Query = """
            query {
                list {
                    id
                    name
                    age
                    enabled
                    role
                }
            }
            """;

        Mockito.when(this.query2Retriever.get(Mockito.any()))
            .thenReturn(runQuery(project2GraphQL, project2Query));

        final Lilo lilo = Lilo.builder()
            .addSource(createSchemaSource(SCHEMA1_NAME, this.introspection1Retriever, this.query1Retriever))
            .addSource(createSchemaSource(SCHEMA2_NAME, this.introspection2Retriever, this.query2Retriever))
            .build();

        final ExecutionResult stitchResult = lilo.stitch(executionInput);
        Assertions.assertEquals(expected, stitchResult.getData());
    }

    @Test
    void stitchingMutationTest() throws IOException {

        // Combined result -----------------------------------------------------
        final Map<String, Object> expected = new HashMap<>();

        expected.put("create", RESULT_MAP);
        expected.put("delete", null);

        final ExecutionInput executionInput = ExecutionInput.newExecutionInput()
            .query("""
                mutation {
                    create(user: {name: "John", age: 34, enabled: true, role: ADMIN}) {
                        id
                        name
                        age
                        enabled
                        role
                    }
                    delete(id: 1)
                }
                """)
            .build();

        final GraphQL combinedGraphQL = createGraphQL("/crud/combined.graphqls", createCombinedWiring());

        final ExecutionResult result = combinedGraphQL.execute(executionInput);
        Assertions.assertEquals(expected, result.getData());

        // Stitching result ----------------------------------------------------
        final GraphQL project1GraphQL = createGraphQL("/crud/project1.graphqls", createProject1Wiring());
        final GraphQL project2GraphQL = createGraphQL("/crud/project2.graphqls", createProject2Wiring());

        Mockito.when(this.introspection1Retriever.get())
            .thenReturn(runQuery(project1GraphQL, IntrospectionQuery.INTROSPECTION_QUERY));

        Mockito.when(this.introspection2Retriever.get())
            .thenReturn(runQuery(project2GraphQL, IntrospectionQuery.INTROSPECTION_QUERY));

        final String project1Query = """
            mutation {
                create(user: {name: "John", age: 34, enabled: true, role: ADMIN}) {
                    id
                    name
                    age
                    enabled
                    role
                }
            }
            """;

        Mockito.when(this.query1Retriever.get(Mockito.any()))
            .thenReturn(runQuery(project1GraphQL, project1Query));

        final String project2Query = """
            mutation {
                delete(id: 1)
            }
            """;

        Mockito.when(this.query2Retriever.get(Mockito.any()))
            .thenReturn(runQuery(project2GraphQL, project2Query));

        final Lilo lilo = Lilo.builder()
            .addSource(createSchemaSource(SCHEMA1_NAME, this.introspection1Retriever, this.query1Retriever))
            .addSource(createSchemaSource(SCHEMA2_NAME, this.introspection2Retriever, this.query2Retriever))
            .build();

        final ExecutionResult stitchResult = lilo.stitch(executionInput);
        Assertions.assertEquals(expected, stitchResult.getData());
    }
}