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
package io.fria.lilo.error;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static io.fria.lilo.TestUtils.createGraphQL;
import static io.fria.lilo.TestUtils.loadResource;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.GraphQLException;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.schema.idl.RuntimeWiring;
import io.fria.lilo.DefinedSchemaSource;
import io.fria.lilo.Lilo;
import io.fria.lilo.LiloContext;
import io.fria.lilo.RemoteSchemaSource;
import io.fria.lilo.SchemaSource;
import io.fria.lilo.SyncIntrospectionRetriever;
import io.fria.lilo.TestUtils;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ErrorTest {

  private static final String SCHEMA1_NAME = "project1";
  private static final String SCHEMA2_NAME = "project2";

  private static final RuntimeWiring WIRING =
      RuntimeWiring.newRuntimeWiring()
          .type(
              newTypeWiring("Query")
                  .dataFetcher(
                      "greeting1",
                      env -> {
                        throw new GraphQLException("An error occurred for greeting1");
                      })
                  .dataFetcher("greeting2", env -> "Hello greeting2"))
          .build();

  @Test
  void definedStitchingTest() {

    // Combined result -----------------------------------------------------
    final ExecutionInput executionInput =
        ExecutionInput.newExecutionInput().query("{greeting1\ngreeting2}").build();

    final GraphQL combinedGraphQL = createGraphQL("/greetings/combined.graphqls", WIRING);
    final ExecutionResult result = combinedGraphQL.execute(executionInput);
    final List<GraphQLError> combinedErrors = result.getErrors();
    Assertions.assertNotNull(combinedErrors);
    Assertions.assertEquals(1, combinedErrors.size());
    final GraphQLError expected = combinedErrors.get(0);

    // Stitching result ----------------------------------------------------
    final var project2GraphQL = createGraphQL("/greetings/greeting2.graphqls", WIRING);
    final var introspection2Retriever = new TestUtils.TestIntrospectionRetriever(project2GraphQL);
    final var query2Retriever = new TestUtils.TestQueryRetriever(project2GraphQL);

    final Lilo lilo =
        Lilo.builder()
            .addSource(
                DefinedSchemaSource.create(
                    SCHEMA1_NAME, loadResource("/greetings/greeting1.graphqls"), WIRING))
            .addSource(
                RemoteSchemaSource.create(SCHEMA2_NAME, introspection2Retriever, query2Retriever))
            .build();

    final ExecutionResult stitchResult = lilo.stitch(executionInput);
    final List<GraphQLError> stitchedErrors = stitchResult.getErrors();
    Assertions.assertNotNull(stitchedErrors);
    Assertions.assertEquals(1, stitchedErrors.size());
    Assertions.assertEquals(expected.getMessage(), stitchedErrors.get(0).getMessage());
  }

  @Test
  void ignoreUnloadedSource() {

    // Combined result -----------------------------------------------------
    final ExecutionInput executionInput =
        ExecutionInput.newExecutionInput().query("{greeting2}").build();

    final GraphQL combinedGraphQL = createGraphQL("/greetings/combined.graphqls", WIRING);
    final ExecutionResult result = combinedGraphQL.execute(executionInput);
    final Map<String, Object> expected = result.getData();
    Assertions.assertNotNull(expected);
    Assertions.assertEquals(0, result.getErrors().size());

    // Stitching result ----------------------------------------------------
    final var project1GraphQL = createGraphQL("/greetings/greeting1.graphqls", WIRING);
    final var project2GraphQL = createGraphQL("/greetings/greeting2.graphqls", WIRING);
    final var introspection1Retriever =
        new SyncIntrospectionRetriever() {
          @Override
          public @NotNull String get(
              @NotNull final LiloContext liloContext,
              @NotNull final SchemaSource schemaSource,
              @NotNull final String query,
              final @Nullable Object localContext) {
            throw new IllegalArgumentException("Cannot load remote introspection");
          }
        };
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
  void stitchingTest() {

    // Combined result -----------------------------------------------------
    final ExecutionInput executionInput =
        ExecutionInput.newExecutionInput().query("{greeting1\ngreeting2}").build();

    final GraphQL combinedGraphQL = createGraphQL("/greetings/combined.graphqls", WIRING);
    final ExecutionResult result = combinedGraphQL.execute(executionInput);
    final List<GraphQLError> combinedErrors = result.getErrors();
    Assertions.assertNotNull(combinedErrors);
    Assertions.assertEquals(1, combinedErrors.size());
    final GraphQLError expected = combinedErrors.get(0);

    // Stitching result ----------------------------------------------------
    final var project1GraphQL = createGraphQL("/greetings/greeting1.graphqls", WIRING);
    final var project2GraphQL = createGraphQL("/greetings/greeting2.graphqls", WIRING);
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
    final List<GraphQLError> stitchedErrors = stitchResult.getErrors();
    Assertions.assertNotNull(stitchedErrors);
    Assertions.assertEquals(1, stitchedErrors.size());
    Assertions.assertEquals(expected.getMessage(), stitchedErrors.get(0).getMessage());
  }

  @Test
  void instrumentationTest() throws InterruptedException {

    // Combined result -----------------------------------------------------
    final ExecutionInput executionInput =
        ExecutionInput.newExecutionInput().query("{greeting2}").build();

    // Stitching result ----------------------------------------------------
    final var project1GraphQL = createGraphQL("/greetings/greeting1.graphqls", WIRING);
    final var project2GraphQL = createGraphQL("/greetings/greeting2.graphqls", WIRING);
    final var introspection1Retriever = new TestUtils.TestIntrospectionRetriever(project1GraphQL);
    final var introspection2Retriever = new TestUtils.TestIntrospectionRetriever(project2GraphQL);
    final var query1Retriever = new TestUtils.TestQueryRetriever(project1GraphQL);
    final var query2Retriever = new TestUtils.TestQueryRetriever(project2GraphQL);

    final CountDownLatch countDownLatch = new CountDownLatch(1);

    final Lilo lilo =
        Lilo.builder()
            .addSource(
                RemoteSchemaSource.create(SCHEMA1_NAME, introspection1Retriever, query1Retriever))
            .addSource(
                RemoteSchemaSource.create(SCHEMA2_NAME, introspection2Retriever, query2Retriever))
            .instrumentation(
                new Instrumentation() {
                  @Override
                  public @NotNull CompletableFuture<ExecutionResult> instrumentExecutionResult(
                      final ExecutionResult executionResult,
                      final InstrumentationExecutionParameters parameters,
                      final InstrumentationState state) {
                    countDownLatch.countDown();
                    return Instrumentation.super.instrumentExecutionResult(
                        executionResult, parameters, state);
                  }
                })
            .build();

    lilo.stitch(executionInput);
    countDownLatch.await(5, TimeUnit.SECONDS);
    Assertions.assertEquals(0, countDownLatch.getCount());
  }
}
