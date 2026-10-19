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
package io.fria.lilo.limits;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentationContext;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import graphql.execution.preparsed.PreparsedDocumentProvider;
import graphql.introspection.Introspection;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import io.fria.lilo.DefinedSchemaSource;
import io.fria.lilo.Lilo;
import io.fria.lilo.RemoteSchemaSource;
import io.fria.lilo.SchemaSource;
import io.fria.lilo.TestUtils;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RequestLimitsTest {

  private static final String SCHEMA =
      "type Query {\n"
          + "  node: Node\n"
          + "  hello: String\n"
          + "}\n"
          + "type Node {\n"
          + "  id: ID\n"
          + "  name: String\n"
          + "  child: Node\n"
          + "}\n";

  /** {@code node} is at depth 1, so this query is 4 levels deep. */
  private static final String DEEP_QUERY = "{ node { child { child { name } } } }";

  private static final String SHALLOW_QUERY = "{ node { name } }";
  private static final String EXPENSIVE_QUERY = "{ node { id name child { id name } } }";
  private static final String SCHEMA_INTROSPECTION = "{ __schema { types { name } } }";
  private static final String TYPE_INTROSPECTION = "{ __type(name: \"Query\") { name } }";

  private static @NotNull Map<String, Object> nodeChain(final int depth) {

    final String name = "node" + depth;

    if (depth == 0) {
      return Map.of("id", name, "name", name);
    }

    return Map.of("id", name, "name", name, "child", nodeChain(depth - 1));
  }

  private static @NotNull RuntimeWiring wiring(final @NotNull AtomicInteger nodeCalls) {

    return RuntimeWiring.newRuntimeWiring()
        .type(
            newTypeWiring("Query")
                .dataFetcher(
                    "node",
                    env -> {
                      nodeCalls.incrementAndGet();
                      return nodeChain(5);
                    })
                .dataFetcher("hello", env -> "world"))
        .build();
  }

  private static @NotNull SchemaSource definedSource(final @NotNull AtomicInteger nodeCalls) {
    return DefinedSchemaSource.create("nodes", SCHEMA, wiring(nodeCalls));
  }

  private static @NotNull String firstErrorMessage(final @NotNull ExecutionResult result) {

    final List<GraphQLError> errors = result.getErrors();
    Assertions.assertFalse(errors.isEmpty(), "expected a GraphQL error");
    return errors.get(0).getMessage();
  }

  @Test
  void queryDeeperThanLimitIsRejected() {

    final AtomicInteger nodeCalls = new AtomicInteger();
    final Lilo lilo = Lilo.builder().addSource(definedSource(nodeCalls)).maxQueryDepth(3).build();

    final ExecutionResult result = lilo.stitch(DEEP_QUERY);

    Assertions.assertTrue(
        firstErrorMessage(result).contains("maximum query depth"), firstErrorMessage(result));
    Assertions.assertNull(result.getData());
    Assertions.assertEquals(0, nodeCalls.get(), "the source must not be called");
  }

  @Test
  void queryWithinLimitWorks() {

    final AtomicInteger nodeCalls = new AtomicInteger();
    final Lilo lilo = Lilo.builder().addSource(definedSource(nodeCalls)).maxQueryDepth(4).build();

    final ExecutionResult result = lilo.stitch(DEEP_QUERY);

    Assertions.assertEquals(List.of(), result.getErrors());
    Assertions.assertEquals(
        Map.of("node", Map.of("child", Map.of("child", Map.of("name", "node3")))),
        result.getData());
    Assertions.assertEquals(1, nodeCalls.get());
  }

  @Test
  void complexityLimitRejectsExpensiveQuery() {

    final AtomicInteger nodeCalls = new AtomicInteger();
    final Lilo lilo =
        Lilo.builder().addSource(definedSource(nodeCalls)).maxQueryComplexity(4).build();

    // node, name: complexity 2
    final ExecutionResult cheap = lilo.stitch(SHALLOW_QUERY);
    Assertions.assertEquals(List.of(), cheap.getErrors());
    Assertions.assertEquals(Map.of("node", Map.of("name", "node5")), cheap.getData());
    Assertions.assertEquals(1, nodeCalls.get());

    // node, id, name, child, id, name: complexity 6
    final ExecutionResult expensive = lilo.stitch(EXPENSIVE_QUERY);
    Assertions.assertTrue(
        firstErrorMessage(expensive).contains("maximum query complexity"),
        firstErrorMessage(expensive));
    Assertions.assertNull(expensive.getData());
    Assertions.assertEquals(1, nodeCalls.get(), "the source must not be called");
  }

  @Test
  void depthLimitAppliesToStitchAsync() throws Exception {

    final AtomicInteger nodeCalls = new AtomicInteger();
    final Lilo lilo = Lilo.builder().addSource(definedSource(nodeCalls)).maxQueryDepth(3).build();

    final ExecutionResult result =
        lilo.stitchAsync(ExecutionInput.newExecutionInput().query(DEEP_QUERY).build())
            .get(10, TimeUnit.SECONDS);

    Assertions.assertTrue(
        firstErrorMessage(result).contains("maximum query depth"), firstErrorMessage(result));
    Assertions.assertEquals(0, nodeCalls.get(), "the source must not be called");
  }

  @Test
  void introspectionCanBeDisabled() {

    final AtomicInteger nodeCalls = new AtomicInteger();
    final Lilo lilo =
        Lilo.builder().addSource(definedSource(nodeCalls)).introspectionEnabled(false).build();

    final ExecutionResult schemaResult = lilo.stitch(SCHEMA_INTROSPECTION);
    Assertions.assertTrue(
        firstErrorMessage(schemaResult).contains("Introspection has been disabled"),
        firstErrorMessage(schemaResult));
    Assertions.assertNull(schemaResult.getData());

    final ExecutionResult typeResult = lilo.stitch(TYPE_INTROSPECTION);
    Assertions.assertTrue(
        firstErrorMessage(typeResult).contains("Introspection has been disabled"),
        firstErrorMessage(typeResult));

    final ExecutionResult normal = lilo.stitch(SHALLOW_QUERY);
    Assertions.assertEquals(List.of(), normal.getErrors());
    Assertions.assertEquals(Map.of("node", Map.of("name", "node5")), normal.getData());

    // The setting is per Lilo instance, not JVM wide
    Assertions.assertTrue(Introspection.isEnabledJvmWide());
  }

  @Test
  void introspectionCannotBeReenabledByTheRequestContext() {

    final Lilo lilo =
        Lilo.builder()
            .addSource(definedSource(new AtomicInteger()))
            .introspectionEnabled(false)
            .build();

    final ExecutionResult result =
        lilo.stitch(
            ExecutionInput.newExecutionInput()
                .query(SCHEMA_INTROSPECTION)
                .graphQLContext(Map.of(Introspection.INTROSPECTION_DISABLED, false))
                .build());

    Assertions.assertTrue(
        firstErrorMessage(result).contains("Introspection has been disabled"),
        firstErrorMessage(result));
  }

  @Test
  void introspectionCanBeDisabledForStitchAsync() throws Exception {

    final Lilo lilo =
        Lilo.builder()
            .addSource(definedSource(new AtomicInteger()))
            .introspectionEnabled(false)
            .build();

    final ExecutionResult result =
        lilo.stitchAsync(ExecutionInput.newExecutionInput().query(SCHEMA_INTROSPECTION).build())
            .get(10, TimeUnit.SECONDS);

    Assertions.assertTrue(
        firstErrorMessage(result).contains("Introspection has been disabled"),
        firstErrorMessage(result));

    final ExecutionResult normal =
        lilo.stitchAsync(ExecutionInput.newExecutionInput().query("{ hello }").build())
            .get(10, TimeUnit.SECONDS);

    Assertions.assertEquals(List.of(), normal.getErrors());
    Assertions.assertEquals(Map.of("hello", "world"), normal.getData());
  }

  @Test
  void introspectionEnabledByDefault() {

    final Lilo lilo = Lilo.builder().addSource(definedSource(new AtomicInteger())).build();

    final ExecutionResult result = lilo.stitch(TYPE_INTROSPECTION);

    Assertions.assertEquals(List.of(), result.getErrors());
    Assertions.assertEquals(Map.of("__type", Map.of("name", "Query")), result.getData());
  }

  @Test
  void remoteSchemaLoadsWhenIntrospectionIsDisabled() {

    final GraphQL remoteGraphQL =
        GraphQL.newGraphQL(
                new SchemaGenerator()
                    .makeExecutableSchema(
                        new SchemaParser().parse(SCHEMA), wiring(new AtomicInteger())))
            .build();

    final AtomicInteger introspections = new AtomicInteger();
    final TestUtils.TestIntrospectionRetriever introspectionRetriever =
        new TestUtils.TestIntrospectionRetriever(remoteGraphQL) {
          @Override
          public @NotNull String get(
              final @NotNull io.fria.lilo.LiloContext liloContext,
              final @NotNull SchemaSource schemaSource,
              final @NotNull String query,
              final Object localContext) {
            introspections.incrementAndGet();
            return super.get(liloContext, schemaSource, query, localContext);
          }
        };

    final Lilo lilo =
        Lilo.builder()
            .addSource(
                RemoteSchemaSource.create(
                    "remote",
                    introspectionRetriever,
                    new TestUtils.TestQueryRetriever(remoteGraphQL)))
            .introspectionEnabled(false)
            .build();

    final ExecutionResult result = lilo.stitch("{ hello }");

    Assertions.assertEquals(List.of(), result.getErrors());
    Assertions.assertEquals(Map.of("hello", "world"), result.getData());
    Assertions.assertEquals(1, introspections.get());

    final ExecutionResult introspection = lilo.stitch(SCHEMA_INTROSPECTION);
    Assertions.assertTrue(
        firstErrorMessage(introspection).contains("Introspection has been disabled"),
        firstErrorMessage(introspection));
  }

  @Test
  void customizerIsApplied() {

    final AtomicInteger parses = new AtomicInteger();
    final Map<String, PreparsedDocumentEntry> cache = new ConcurrentHashMap<>();
    final PreparsedDocumentProvider cachingProvider =
        (final ExecutionInput executionInput,
            final Function<ExecutionInput, PreparsedDocumentEntry> parseAndValidate) ->
            CompletableFuture.completedFuture(
                cache.computeIfAbsent(
                    executionInput.getQuery(),
                    query -> {
                      parses.incrementAndGet();
                      return parseAndValidate.apply(executionInput);
                    }));

    final Lilo lilo =
        Lilo.builder()
            .addSource(definedSource(new AtomicInteger()))
            .graphQLCustomizer(builder -> builder.preparsedDocumentProvider(cachingProvider))
            .build();

    final ExecutionResult first = lilo.stitch("{ hello }");
    final ExecutionResult second = lilo.stitch("{ hello }");

    Assertions.assertEquals(Map.of("hello", "world"), first.getData());
    Assertions.assertEquals(Map.of("hello", "world"), second.getData());
    Assertions.assertEquals(1, parses.get());
  }

  @Test
  void existingInstrumentationStillRuns() {

    final AtomicInteger executions = new AtomicInteger();
    final SimplePerformantInstrumentation userInstrumentation =
        new SimplePerformantInstrumentation() {
          @Override
          public @NotNull InstrumentationContext<ExecutionResult> beginExecution(
              final InstrumentationExecutionParameters parameters,
              final InstrumentationState state) {
            executions.incrementAndGet();
            return SimpleInstrumentationContext.noOp();
          }
        };

    final AtomicInteger nodeCalls = new AtomicInteger();
    final Lilo lilo =
        Lilo.builder()
            .addSource(definedSource(nodeCalls))
            .instrumentation(userInstrumentation)
            .maxQueryDepth(3)
            .maxQueryComplexity(10)
            .introspectionEnabled(false)
            .build();

    final ExecutionResult ok = lilo.stitch(SHALLOW_QUERY);
    Assertions.assertEquals(List.of(), ok.getErrors());
    Assertions.assertEquals(1, executions.get());

    final ExecutionResult rejected = lilo.stitch(DEEP_QUERY);
    Assertions.assertTrue(
        firstErrorMessage(rejected).contains("maximum query depth"), firstErrorMessage(rejected));
    Assertions.assertEquals(2, executions.get());
    Assertions.assertEquals(1, nodeCalls.get());
  }

  @Test
  void invalidLimitsRejected() {

    Assertions.assertThrows(IllegalArgumentException.class, () -> Lilo.builder().maxQueryDepth(0));
    Assertions.assertThrows(IllegalArgumentException.class, () -> Lilo.builder().maxQueryDepth(-1));
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> Lilo.builder().maxQueryComplexity(0));
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> Lilo.builder().maxQueryComplexity(-5));
  }
}
