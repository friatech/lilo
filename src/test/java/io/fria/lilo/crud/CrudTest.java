package io.fria.lilo.crud;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLScalarType;
import graphql.schema.idl.RuntimeWiring;
import io.fria.lilo.DummyCoercing;
import io.fria.lilo.Lilo;
import io.fria.lilo.TestUtils;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static io.fria.lilo.TestUtils.createGraphQL;
import static io.fria.lilo.TestUtils.createSchemaSource;
import static io.fria.lilo.TestUtils.loadResource;

class CrudTest {

  private static final String SCHEMA1_NAME = "project1";
  private static final String SCHEMA2_NAME = "project2";
  private static final Map<String, Object> RESULT_MAP =
      Map.of(
          "id",
          1,
          "name",
          "John",
          "age",
          34,
          "enabled",
          true,
          "role",
          "ADMIN",
          "username",
          "john",
          "__typename",
          "WebUser");

  private static RuntimeWiring createWiring() {

    return RuntimeWiring.newRuntimeWiring()
        .type(
            newTypeWiring("Queries")
                .dataFetcher("get", env -> RESULT_MAP)
                .dataFetcher("list", env -> List.of(RESULT_MAP)))
        .type(
            newTypeWiring("Mutations")
                .dataFetcher("create", env -> RESULT_MAP)
                .dataFetcher("delete", env -> null))
        .type(newTypeWiring("UserBase").typeResolver(env -> null))
        .type(
            newTypeWiring("SystemUser")
                .typeResolver(env -> env.getSchema().getObjectType("WebUser")))
        .scalar(GraphQLScalarType.newScalar().name("Void").coercing(new DummyCoercing()).build())
        .build();
  }

  @Test
  void stitchingFragmentedQueryTest() throws IOException {

    // Combined result -----------------------------------------------------
    final ExecutionInput executionInput =
        ExecutionInput.newExecutionInput()
            .query(loadResource("/crud/fragmented-query.graphql"))
            .build();

    final GraphQL combinedGraphQL = createGraphQL("/crud/combined.graphqls", createWiring());
    final ExecutionResult result = combinedGraphQL.execute(executionInput);
    final Map<String, Object> expected = result.getData();
    Assertions.assertNotNull(expected);

    // Stitching result ----------------------------------------------------
    final var project1GraphQL = createGraphQL("/crud/project1.graphqls", createWiring());
    final var project2GraphQL = createGraphQL("/crud/project2.graphqls", createWiring());
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
    Assertions.assertEquals(expected, stitchResult.getData());
  }

  @Test
  void stitchingMutationTest() throws IOException {

    // Combined result -----------------------------------------------------
    final ExecutionInput executionInput =
        ExecutionInput.newExecutionInput().query(loadResource("/crud/mutation.graphql")).build();

    final GraphQL combinedGraphQL = createGraphQL("/crud/combined.graphqls", createWiring());
    final ExecutionResult result = combinedGraphQL.execute(executionInput);
    final Map<String, Object> expected = result.getData();
    Assertions.assertNotNull(expected);

    // Stitching result ----------------------------------------------------
    final var project1GraphQL = createGraphQL("/crud/project1.graphqls", createWiring());
    final var project2GraphQL = createGraphQL("/crud/project2.graphqls", createWiring());
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
    Assertions.assertEquals(expected, stitchResult.getData());
  }

  @Test
  void stitchingQueryTest() throws IOException {

    // Combined result -----------------------------------------------------
    final ExecutionInput executionInput =
        ExecutionInput.newExecutionInput().query(loadResource("/crud/query.graphql")).build();

    final GraphQL combinedGraphQL = createGraphQL("/crud/combined.graphqls", createWiring());
    final ExecutionResult result = combinedGraphQL.execute(executionInput);
    final Map<String, Object> expected = result.getData();
    Assertions.assertNotNull(expected);

    // Stitching result ----------------------------------------------------
    final var project1GraphQL = createGraphQL("/crud/project1.graphqls", createWiring());
    final var project2GraphQL = createGraphQL("/crud/project2.graphqls", createWiring());
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
    Assertions.assertEquals(expected, stitchResult.getData());
  }
}
