package io.fria.lilo.crud;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLScalarType;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import io.fria.lilo.DummyCoercing;
import io.fria.lilo.Lilo;
import io.fria.lilo.TestUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static io.fria.lilo.TestUtils.createSchemaSource;

class CrudTest {

    private static final String              SCHEMA1_NAME = "project1";
    private static final String              SCHEMA2_NAME = "project2";
    private static final Map<String, Object> RESULT_MAP   = Map.of("id", 1, "name", "John", "age", 34, "enabled", true, "role", "ADMIN", "username", "john", "__typename", "WebUser");

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
            .type(newTypeWiring("UserBase").typeResolver(env -> null))
            .type(newTypeWiring("SystemUser").typeResolver(env -> env.getSchema().getObjectType("WebUser")))
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
            .type(newTypeWiring("UserBase").typeResolver(env -> null))
            .type(newTypeWiring("SystemUser").typeResolver(env -> env.getSchema().getObjectType("WebUser")))
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
            .type(newTypeWiring("UserBase").typeResolver(env -> null))
            .type(newTypeWiring("SystemUser").typeResolver(env -> env.getSchema().getObjectType("WebUser")))
            .scalar(GraphQLScalarType.newScalar().name("Void").coercing(new DummyCoercing()).build())
            .build();
    }

    @Test
    void stitchingQueryTest() throws IOException {

        // Combined result -----------------------------------------------------
        final Map<String, Object> expected = Map.of("get", RESULT_MAP, "list", List.of(RESULT_MAP));

        final ExecutionInput executionInput = ExecutionInput.newExecutionInput()
            .query("""
                query {
                    get(id: 1) {
                        __typename
                        ... on WebUser {
                            id
                            name
                            age
                            enabled
                            role
                            username
                        }
                    }
                    list {
                        __typename
                        ... on WebUser {
                            id
                            name
                            age
                            enabled
                            role
                            username
                        }
                    }
                }
                """)
            .build();

        final GraphQL combinedGraphQL = createGraphQL("/crud/combined.graphqls", createCombinedWiring());

        final ExecutionResult result = combinedGraphQL.execute(executionInput);
        Assertions.assertEquals(expected, result.getData());

        // Stitching result ----------------------------------------------------
        final var project1GraphQL         = createGraphQL("/crud/project1.graphqls", createProject1Wiring());
        final var project2GraphQL         = createGraphQL("/crud/project2.graphqls", createProject2Wiring());
        final var introspection1Retriever = new TestUtils.TestIntrospectionRetriever(project1GraphQL);
        final var introspection2Retriever = new TestUtils.TestIntrospectionRetriever(project2GraphQL);
        final var query1Retriever         = new TestUtils.TestQueryRetriever(project1GraphQL);
        final var query2Retriever         = new TestUtils.TestQueryRetriever(project2GraphQL);

        final Lilo lilo = Lilo.builder()
            .addSource(createSchemaSource(SCHEMA1_NAME, introspection1Retriever, query1Retriever))
            .addSource(createSchemaSource(SCHEMA2_NAME, introspection2Retriever, query2Retriever))
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
                        __typename
                        ... on WebUser {
                            id
                            name
                            age
                            enabled
                            role
                            username
                        }
                    }
                    delete(id: 1)
                }
                """)
            .build();

        final GraphQL combinedGraphQL = createGraphQL("/crud/combined.graphqls", createCombinedWiring());

        final ExecutionResult result = combinedGraphQL.execute(executionInput);
        Assertions.assertEquals(expected, result.getData());

        // Stitching result ----------------------------------------------------
        final var project1GraphQL         = createGraphQL("/crud/project1.graphqls", createProject1Wiring());
        final var project2GraphQL         = createGraphQL("/crud/project2.graphqls", createProject2Wiring());
        final var introspection1Retriever = new TestUtils.TestIntrospectionRetriever(project1GraphQL);
        final var introspection2Retriever = new TestUtils.TestIntrospectionRetriever(project2GraphQL);
        final var query1Retriever         = new TestUtils.TestQueryRetriever(project1GraphQL);
        final var query2Retriever         = new TestUtils.TestQueryRetriever(project2GraphQL);

        final Lilo lilo = Lilo.builder()
            .addSource(createSchemaSource(SCHEMA1_NAME, introspection1Retriever, query1Retriever))
            .addSource(createSchemaSource(SCHEMA2_NAME, introspection2Retriever, query2Retriever))
            .build();

        final ExecutionResult stitchResult = lilo.stitch(executionInput);
        Assertions.assertEquals(expected, stitchResult.getData());
    }
}