package io.fria.lilo.defined;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.idl.RuntimeWiring;
import io.fria.lilo.DefinedSchemaSource;
import io.fria.lilo.Lilo;
import io.fria.lilo.RemoteSchemaSource;
import io.fria.lilo.TestUtils;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static io.fria.lilo.TestUtils.createGraphQL;
import static io.fria.lilo.TestUtils.loadResource;

class DefinedSchemaTest {

  private static final String SCHEMA1_NAME = "project1";
  private static final String SCHEMA2_NAME = "project2";

  private static RuntimeWiring createWiring() {

    return RuntimeWiring.newRuntimeWiring()
        .type(
            newTypeWiring("Query")
                .dataFetcher("greeting1", env -> "Hello greeting1")
                .dataFetcher("greeting2", env -> "Hello greeting2"))
        .build();
  }

  @Test
  void stitchingTest() {

    // Combined result -----------------------------------------------------
    final ExecutionInput executionInput =
        ExecutionInput.newExecutionInput().query("{greeting1\ngreeting2}").build();

    final RuntimeWiring wiring = createWiring();
    final GraphQL combinedGraphQL = createGraphQL("/greetings/combined.graphqls", wiring);
    final ExecutionResult result = combinedGraphQL.execute(executionInput);
    final Map<String, Object> expected = result.getData();
    Assertions.assertNotNull(expected);
    Assertions.assertEquals(0, result.getErrors().size());

    // Stitching result ----------------------------------------------------

    final var project1GraphQL = createGraphQL("/greetings/greeting1.graphqls", wiring);
    final var project2GraphQL = createGraphQL("/greetings/greeting2.graphqls", wiring);
    final var introspection1Retriever = new TestUtils.TestIntrospectionRetriever(project1GraphQL);
    final var introspection2Retriever = new TestUtils.TestIntrospectionRetriever(project2GraphQL);
    final var query1Retriever = new TestUtils.TestQueryRetriever(project1GraphQL);
    final var query2Retriever = new TestUtils.TestQueryRetriever(project2GraphQL);

    final Lilo lilo =
        Lilo.builder()
            .addSource(
                DefinedSchemaSource.create(
                    SCHEMA1_NAME, loadResource("/greetings/greeting1.graphqls"), wiring))
            .addSource(
                RemoteSchemaSource.create(SCHEMA2_NAME, introspection2Retriever, query2Retriever))
            .build();

    final ExecutionResult stitchResult = lilo.stitch(executionInput);
    Assertions.assertEquals(expected, stitchResult.getData());
    Assertions.assertEquals(0, stitchResult.getErrors().size());
  }
}
