/*
 * Copyright 2023 the original author or authors.
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
package io.fria.lilo.dynamic_loading;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static io.fria.lilo.TestUtils.createGraphQL;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.introspection.IntrospectionQuery;
import graphql.schema.idl.RuntimeWiring;
import io.fria.lilo.GraphQLRequest;
import io.fria.lilo.Lilo;
import io.fria.lilo.RemoteSchemaSource;
import io.fria.lilo.TestUtils;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ReloadSchemaTest {

  private static final String SCHEMA1_NAME = "project1";
  private static final String SCHEMA2_NAME = "project2";

  private static final RuntimeWiring WIRING =
      RuntimeWiring.newRuntimeWiring()
          .type(
              newTypeWiring("Query")
                  .dataFetcher("greeting1", env -> "Hello greeting1")
                  .dataFetcher("greeting2", env -> "Hello greeting2")
                  .dataFetcher("greeting3", env -> "Hello greeting3"))
          .build();

  @Test
  void stitchingTest() {

    Map<String, Object> expected =
        Map.of("greeting1", "Hello greeting1", "greeting2", "Hello greeting2");

    ExecutionInput executionInput =
        ExecutionInput.newExecutionInput().query("{greeting1\ngreeting2}").build();

    // Stitching result ----------------------------------------------------
    final var project1GraphQL = createGraphQL("/greetings/greeting1.graphqls", WIRING);
    final var project2GraphQL = createGraphQL("/greetings/greeting2.graphqls", WIRING);
    final var introspection1Retriever = new TestUtils.TestIntrospectionRetriever(project1GraphQL);
    final var introspection2Retriever = new TestUtils.TestIntrospectionRetriever(project2GraphQL);
    final var query1Retriever = new TestUtils.TestQueryRetriever(project1GraphQL);
    final var query2Retriever = new TestUtils.TestQueryRetriever(project2GraphQL);

    final GraphQLRequest introspectionRequest = new GraphQLRequest();
    introspectionRequest.setQuery(IntrospectionQuery.INTROSPECTION_QUERY);

    final Lilo lilo =
        Lilo.builder()
            .addSource(
                RemoteSchemaSource.create(SCHEMA1_NAME, introspection1Retriever, query1Retriever))
            .addSource(
                RemoteSchemaSource.create(SCHEMA2_NAME, introspection2Retriever, query2Retriever))
            .build();

    ExecutionResult stitchResult = lilo.stitch(executionInput);
    Assertions.assertEquals(expected, stitchResult.getData());

    // After reloading context old expected result won't work.
    final var project3GraphQL = createGraphQL("/dynamic_loading/greeting3.graphqls", WIRING);

    query2Retriever.setGraphQL(project3GraphQL);
    introspection2Retriever.setGraphQL(project3GraphQL);

    lilo.getContext().invalidate(SCHEMA2_NAME);

    stitchResult = lilo.stitch(executionInput);
    Assertions.assertNotEquals(expected, stitchResult.getData());

    // But new query should work
    executionInput = ExecutionInput.newExecutionInput().query("{greeting1\ngreeting3}").build();

    expected = Map.of("greeting1", "Hello greeting1", "greeting3", "Hello greeting3");

    stitchResult = lilo.stitch(executionInput);
    Assertions.assertEquals(expected, stitchResult.getData());
  }
}
