package io.fria.lilo.error;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.GraphQLException;
import graphql.schema.idl.RuntimeWiring;
import io.fria.lilo.Lilo;
import io.fria.lilo.TestUtils;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static io.fria.lilo.TestUtils.createGraphQL;
import static io.fria.lilo.TestUtils.createSchemaSource;

class ErrorTest {

  private static final String SCHEMA1_NAME = "project1";
  private static final String SCHEMA2_NAME = "project2";

  private static RuntimeWiring createWiring() {

    return RuntimeWiring.newRuntimeWiring()
        .type(
            newTypeWiring("Query")
                .dataFetcher(
                    "greeting1",
                    env -> {
                      throw new GraphQLException("An error occurred for greeting1");
                    })
                .dataFetcher("greeting2", env -> "Hello greeting2"))
        .build();
  }

  @Test
  void stitchingTest() throws IOException {

    // Combined result -----------------------------------------------------
    final ExecutionInput executionInput =
        ExecutionInput.newExecutionInput().query("{greeting1\ngreeting2}").build();

    final GraphQL combinedGraphQL = createGraphQL("/greetings/combined.graphqls", createWiring());
    final ExecutionResult result = combinedGraphQL.execute(executionInput);
    final List<GraphQLError> combinedErrors = result.getErrors();
    Assertions.assertNotNull(combinedErrors);
    Assertions.assertEquals(1, combinedErrors.size());
    final GraphQLError expected = combinedErrors.get(0);

    // Stitching result ----------------------------------------------------
    final var project1GraphQL = createGraphQL("/greetings/greeting1.graphqls", createWiring());
    final var project2GraphQL = createGraphQL("/greetings/greeting2.graphqls", createWiring());
    final var introspection1Retriever = new TestUtils.TestIntrospectionRetriever(project1GraphQL);
    final var introspection2Retriever = new TestUtils.TestIntrospectionRetriever(project2GraphQL);
    final var query1Retriever = new TestUtils.TestQueryRetriever(project1GraphQL);
    final var query2Retriever = new TestUtils.TestQueryRetriever(project2GraphQL);

    final Lilo lilo =
        Lilo.builder()
            .addSource(createSchemaSource(SCHEMA1_NAME, introspection1Retriever, query1Retriever))
            .addSource(createSchemaSource(SCHEMA2_NAME, introspection2Retriever, query2Retriever))
            .build();

    final ExecutionResult stitchResult = lilo.stitch(executionInput);
    final List<GraphQLError> stitchedErrors = stitchResult.getErrors();
    Assertions.assertNotNull(stitchedErrors);
    Assertions.assertEquals(1, stitchedErrors.size());
    Assertions.assertEquals(expected.getMessage(), stitchedErrors.get(0).getMessage());
  }
}
