/*
 * Copyright 2022-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fria.lilo.crud;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static io.fria.lilo.TestUtils.createGraphQL;
import static io.fria.lilo.TestUtils.loadResource;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLScalarType;
import graphql.schema.idl.RuntimeWiring;
import io.fria.lilo.DummyCoercing;
import io.fria.lilo.Lilo;
import io.fria.lilo.RemoteSchemaSource;
import io.fria.lilo.TestUtils;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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

  private static final RuntimeWiring WIRING =
      RuntimeWiring.newRuntimeWiring()
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

  @Test
  void stitchingFragmentedQueryTest() {

    // Combined result -----------------------------------------------------
    final ExecutionInput executionInput =
        ExecutionInput.newExecutionInput()
            .query(loadResource("/crud/fragmented-query.graphql"))
            .build();

    final GraphQL combinedGraphQL = createGraphQL("/crud/combined.graphqls", WIRING);
    final ExecutionResult result = combinedGraphQL.execute(executionInput);
    final Map<String, Object> expected = result.getData();
    Assertions.assertNotNull(expected);
    Assertions.assertEquals(0, result.getErrors().size());

    // Stitching result ----------------------------------------------------
    final var project1GraphQL = createGraphQL("/crud/project1.graphqls", WIRING);
    final var project2GraphQL = createGraphQL("/crud/project2.graphqls", WIRING);
    final var introspection1Retriever = new TestUtils.TestIntrospectionRetriever(project1GraphQL);
    final var introspection2Retriever = new TestUtils.TestIntrospectionRetriever(project2GraphQL);
    final var query1Retriever = new TestUtils.TestQueryRetriever(project1GraphQL);
    final var query2Retriever = new TestUtils.TestQueryRetriever(project2GraphQL);

    final Lilo lilo =
        Lilo.builder()
            .addSource(
                RemoteSchemaSource.create(SCHEMA1_NAME, introspection1Retriever, query1Retriever))
            .addSource(
                RemoteSchemaSource.create(SCHEMA2_NAME, introspection2Retriever, query2Retriever))
            .build();

    final ExecutionResult stitchResult = lilo.stitch(executionInput);
    Assertions.assertEquals(expected, stitchResult.getData());
    Assertions.assertEquals(0, stitchResult.getErrors().size());
  }

  @Test
  void stitchingMutationTest() {

    // Combined result -----------------------------------------------------
    final ExecutionInput executionInput =
        ExecutionInput.newExecutionInput().query(loadResource("/crud/mutation.graphql")).build();

    final GraphQL combinedGraphQL = createGraphQL("/crud/combined.graphqls", WIRING);
    final ExecutionResult result = combinedGraphQL.execute(executionInput);
    final Map<String, Object> expected = result.getData();
    Assertions.assertNotNull(expected);

    // Stitching result ----------------------------------------------------
    final var project1GraphQL = createGraphQL("/crud/project1.graphqls", WIRING);
    final var project2GraphQL = createGraphQL("/crud/project2.graphqls", WIRING);
    final var introspection1Retriever = new TestUtils.TestIntrospectionRetriever(project1GraphQL);
    final var introspection2Retriever = new TestUtils.TestIntrospectionRetriever(project2GraphQL);
    final var query1Retriever = new TestUtils.TestQueryRetriever(project1GraphQL);
    final var query2Retriever = new TestUtils.TestQueryRetriever(project2GraphQL);

    final Lilo lilo =
        Lilo.builder()
            .addSource(
                RemoteSchemaSource.create(SCHEMA1_NAME, introspection1Retriever, query1Retriever))
            .addSource(
                RemoteSchemaSource.create(SCHEMA2_NAME, introspection2Retriever, query2Retriever))
            .build();

    final ExecutionResult stitchResult = lilo.stitch(executionInput);
    Assertions.assertEquals(expected, stitchResult.getData());
  }

  @Test
  void stitchingQueryTest() {

    // Combined result -----------------------------------------------------
    final ExecutionInput executionInput =
        ExecutionInput.newExecutionInput().query(loadResource("/crud/query.graphql")).build();

    final GraphQL combinedGraphQL = createGraphQL("/crud/combined.graphqls", WIRING);
    final ExecutionResult result = combinedGraphQL.execute(executionInput);
    final Map<String, Object> expected = result.getData();
    Assertions.assertNotNull(expected);

    // Stitching result ----------------------------------------------------
    final var project1GraphQL = createGraphQL("/crud/project1.graphqls", WIRING);
    final var project2GraphQL = createGraphQL("/crud/project2.graphqls", WIRING);
    final var introspection1Retriever = new TestUtils.TestIntrospectionRetriever(project1GraphQL);
    final var introspection2Retriever = new TestUtils.TestIntrospectionRetriever(project2GraphQL);
    final var query1Retriever = new TestUtils.TestQueryRetriever(project1GraphQL);
    final var query2Retriever = new TestUtils.TestQueryRetriever(project2GraphQL);

    final Lilo lilo =
        Lilo.builder()
            .addSource(
                RemoteSchemaSource.create(SCHEMA1_NAME, introspection1Retriever, query1Retriever))
            .addSource(
                RemoteSchemaSource.create(SCHEMA2_NAME, introspection2Retriever, query2Retriever))
            .build();

    final ExecutionResult stitchResult = lilo.stitch(executionInput);
    Assertions.assertEquals(expected, stitchResult.getData());
  }
}
