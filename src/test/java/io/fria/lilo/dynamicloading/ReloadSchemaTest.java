package io.fria.lilo.dynamicloading;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.introspection.IntrospectionQuery;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import io.fria.lilo.GraphQLRequest;
import io.fria.lilo.Lilo;
import io.fria.lilo.TestUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static io.fria.lilo.TestUtils.createGraphQL;
import static io.fria.lilo.TestUtils.createSchemaSource;

class ReloadSchemaTest {

    private static final String SCHEMA1_NAME = "project1";
    private static final String SCHEMA2_NAME = "project2";

    private static RuntimeWiring createProject1Wiring() {

        return RuntimeWiring.newRuntimeWiring()
            .type(
                newTypeWiring("Query")
                    .dataFetcher("greeting1", env -> "Hello greeting1")
            )
            .build();
    }

    private static RuntimeWiring createProject2Wiring() {

        return RuntimeWiring.newRuntimeWiring()
            .type(
                newTypeWiring("Query")
                    .dataFetcher("greeting2", env -> "Hello greeting2")
            )
            .build();
    }

    private static RuntimeWiring createProject3Wiring() {

        return RuntimeWiring.newRuntimeWiring()
            .type(
                newTypeWiring("Query")
                    .dataFetcher("greeting3", env -> "Hello greeting3")
            )
            .build();
    }

    @Test
    void stitchingTest() throws IOException {

        Map<String, Object> expected = Map.of("greeting1", "Hello greeting1", "greeting2", "Hello greeting2");

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
            .query("{greeting1\ngreeting2}")
            .build();

        // Stitching result ----------------------------------------------------
        final var project1GraphQL         = createGraphQL("/greetings/greeting1.graphqls", createProject1Wiring());
        final var project2GraphQL         = createGraphQL("/greetings/greeting2.graphqls", createProject2Wiring());
        final var introspection1Retriever = new TestUtils.TestIntrospectionRetriever(project1GraphQL);
        final var introspection2Retriever = new TestUtils.TestIntrospectionRetriever(project2GraphQL);
        final var query1Retriever         = new TestUtils.TestQueryRetriever(project1GraphQL);
        final var query2Retriever         = new TestUtils.TestQueryRetriever(project2GraphQL);

        final GraphQLRequest introspectionRequest = new GraphQLRequest();
        introspectionRequest.setQuery(IntrospectionQuery.INTROSPECTION_QUERY);

        final Lilo lilo = Lilo.builder()
            .addSource(createSchemaSource(SCHEMA1_NAME, introspection1Retriever, query1Retriever))
            .addSource(createSchemaSource(SCHEMA2_NAME, introspection2Retriever, query2Retriever))
            .build();

        ExecutionResult stitchResult = lilo.stitch(executionInput);
        Assertions.assertEquals(expected, stitchResult.getData());

        // After reloading context old expected result won't work.
        final var project3GraphQL = createGraphQL("/dynamicloading/greeting3.graphqls", createProject3Wiring());

        query2Retriever.setGraphQL(project3GraphQL);
        introspection2Retriever.setGraphQL(project3GraphQL);

        lilo.getContext().reload(SCHEMA2_NAME);

        stitchResult = lilo.stitch(executionInput);
        Assertions.assertNotEquals(expected, stitchResult.getData());

        // But new query should work
        executionInput = ExecutionInput.newExecutionInput()
            .query("{greeting1\ngreeting3}")
            .build();

        expected = Map.of("greeting1", "Hello greeting1", "greeting3", "Hello greeting3");

        stitchResult = lilo.stitch(executionInput);
        Assertions.assertEquals(expected, stitchResult.getData());
    }
}