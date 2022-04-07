package io.fria.lilo.math;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.idl.RuntimeWiring;
import io.fria.lilo.Lilo;
import io.fria.lilo.TestUtils;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static io.fria.lilo.TestUtils.createGraphQL;
import static io.fria.lilo.TestUtils.createSchemaSource;
import static io.fria.lilo.TestUtils.loadResource;

class MathTest {

    private static final String SCHEMA1_NAME = "project1";
    private static final String SCHEMA2_NAME = "project2";

    private static RuntimeWiring createCombinedWiring() {

        return RuntimeWiring.newRuntimeWiring()
            .type(
                newTypeWiring("Query")
                    .dataFetcher("add", env -> env.<Integer>getArgument("a") + env.<Integer>getArgument("b"))
                    .dataFetcher("subtract", env -> env.<Integer>getArgument("a") - env.<Integer>getArgument("b"))
            )
            .build();
    }

    private static RuntimeWiring createProject1Wiring() {

        return RuntimeWiring.newRuntimeWiring()
            .type(
                newTypeWiring("Query")
                    .dataFetcher("add", env -> env.<Integer>getArgument("a") + env.<Integer>getArgument("b"))
            )
            .build();
    }

    private static RuntimeWiring createProject2Wiring() {

        return RuntimeWiring.newRuntimeWiring()
            .type(
                newTypeWiring("Query")
                    .dataFetcher("subtract", env -> env.<Integer>getArgument("a") - env.<Integer>getArgument("b"))
            )
            .build();
    }

    @Test
    void stitchingTest() throws IOException {

        // Combined result -----------------------------------------------------
        final Map<String, Object> expected = Map.of("add", 3, "subtract", 10);

        final ExecutionInput executionInput = ExecutionInput.newExecutionInput()
            .query("{add(a: 1, b: 2)\nsubtract(a: 20, b: 10)}")
            .build();

        final GraphQL combinedGraphQL = createGraphQL("/math/combined.graphqls", createCombinedWiring());

        final ExecutionResult result = combinedGraphQL.execute(executionInput);
        Assertions.assertEquals(expected, result.getData());

        // Stitching result ----------------------------------------------------
        final var project1GraphQL         = createGraphQL("/math/add.graphqls", createProject1Wiring());
        final var project2GraphQL         = createGraphQL("/math/subtract.graphqls", createProject2Wiring());
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
    void variablesTest() throws IOException {

        // Combined result -----------------------------------------------------
        final Map<String, Object> expected = Map.of("add", 3, "subtract", 10);

        final ExecutionInput executionInput = ExecutionInput.newExecutionInput()
            .query(loadResource("/math/query.graphql"))
            .operationName("someMath")
            .variables(Map.of("paramA", 1, "paramB", 2, "paramC", 20, "paramD", 10))
            .build();

        final GraphQL combinedGraphQL = createGraphQL("/math/combined.graphqls", createCombinedWiring());

        final ExecutionResult result = combinedGraphQL.execute(executionInput);
        Assertions.assertEquals(expected, result.getData());

        // Stitching result ----------------------------------------------------
        final var project1GraphQL         = createGraphQL("/math/add.graphqls", createProject1Wiring());
        final var project2GraphQL         = createGraphQL("/math/subtract.graphqls", createProject2Wiring());
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