package io.fria.lilo.async;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.idl.RuntimeWiring;
import io.fria.lilo.Lilo;
import io.fria.lilo.RemoteSchemaSource;
import io.fria.lilo.TestUtils;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static io.fria.lilo.TestUtils.createGraphQL;

class AsyncTest {

  private static final String SCHEMA1_NAME = "project1";
  private static final String SCHEMA2_NAME = "project2";

  private static final RuntimeWiring WIRING =
      RuntimeWiring.newRuntimeWiring()
          .type(
              newTypeWiring("Query")
                  .dataFetcher(
                      "greeting1",
                      env ->
                          CompletableFuture.supplyAsync(
                              () -> AsyncTest.asyncGreeting("greeting1", 400)))
                  .dataFetcher(
                      "greeting2",
                      env ->
                          CompletableFuture.supplyAsync(
                              () -> AsyncTest.asyncGreeting("greeting2", 600))))
          .build();

  private static String asyncGreeting(final String returnValue, final int wait) {
    try {
      Thread.sleep(wait);
    } catch (final InterruptedException e) {
      throw new RuntimeException(e);
    }
    return returnValue;
  }

  @Test
  void stitchingTest() {

    final CountDownLatch lock = new CountDownLatch(1);

    // Combined result -----------------------------------------------------
    final ExecutionInput executionInput =
        ExecutionInput.newExecutionInput().query("{greeting1\ngreeting2}").build();

    final GraphQL combinedGraphQL = createGraphQL("/greetings/combined.graphqls", WIRING);
    final List<Map<String, Object>> expectedList = new ArrayList<>();
    final var resultCompletable = combinedGraphQL.executeAsync(executionInput);

    Assertions.assertDoesNotThrow(
        () -> {
          final ExecutionResult result = resultCompletable.get();
          final Map<String, Object> expected = result.getData();
          expectedList.add(expected);
          Assertions.assertNotNull(expected);
          Assertions.assertEquals("greeting1", expected.get("greeting1"));
          Assertions.assertEquals("greeting2", expected.get("greeting2"));
          Assertions.assertEquals(0, result.getErrors().size());
          lock.countDown();
        });

    Assertions.assertDoesNotThrow(() -> Assertions.assertTrue(lock.await(1000, TimeUnit.SECONDS)));

    // Stitching result ----------------------------------------------------
    final var project1GraphQL = createGraphQL("/greetings/greeting1.graphqls", WIRING);
    final var project2GraphQL = createGraphQL("/greetings/greeting2.graphqls", WIRING);
    final var introspection1Retriever = new TestUtils.TestIntrospectionRetriever(project1GraphQL);
    final var introspection2Retriever = new TestUtils.TestIntrospectionRetriever(project2GraphQL);
    final var query1Retriever = new TestUtils.TestAsyncQueryRetriever(project1GraphQL);
    final var query2Retriever = new TestUtils.TestAsyncQueryRetriever(project2GraphQL);

    final Lilo lilo =
        Lilo.builder()
            .addSource(
                RemoteSchemaSource.create(SCHEMA1_NAME, introspection1Retriever, query1Retriever))
            .addSource(
                RemoteSchemaSource.create(SCHEMA2_NAME, introspection2Retriever, query2Retriever))
            .build();

    Assertions.assertTimeout(
        Duration.ofSeconds(1),
        () -> {
          final ExecutionResult stitchResult = lilo.stitchAsync(executionInput).get();
          final Map<String, Object> expected = expectedList.get(0);
          Assertions.assertEquals(expected, stitchResult.getData());
          Assertions.assertEquals(0, stitchResult.getErrors().size());
        });
  }
}
