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
package io.fria.lilo.defined_subscription;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static io.fria.lilo.TestUtils.createGraphQL;
import static io.fria.lilo.TestUtils.loadResource;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.execution.reactive.SubscriptionPublisher;
import graphql.scalar.GraphqlStringCoercing;
import graphql.schema.GraphQLScalarType;
import graphql.schema.idl.RuntimeWiring;
import io.fria.lilo.DefinedSchemaSource;
import io.fria.lilo.Lilo;
import io.fria.lilo.RemoteSchemaSource;
import io.fria.lilo.TestUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

class DefinedSubscriptionSchemaTest {

  private static final String SCHEMA1_NAME = "project1";
  private static final String SCHEMA2_NAME = "project2";

  private static final RuntimeWiring WIRING =
      RuntimeWiring.newRuntimeWiring()
          .type(
              newTypeWiring("Query")
                  .dataFetcher("greeting1", env -> "Hello greeting1")
                  .dataFetcher("greeting2", env -> "Hello greeting2"))
          .type(
              newTypeWiring("Subscription")
                  .dataFetcher(
                      "greeting1Subscription",
                      env ->
                          (Publisher<String>)
                              s -> {
                                s.onNext("Hello greeting1a");
                                s.onNext("Hello greeting1b");
                              })
                  .dataFetcher(
                      "greeting2Subscription",
                      env -> (Publisher<String>) s -> s.onNext("Hello greeting2")))
          .scalar(
              GraphQLScalarType.newScalar()
                  .name("Text")
                  .coercing(new GraphqlStringCoercing())
                  .build())
          .build();

  @Test
  void stitchingTest() throws InterruptedException {

    // Combined result -----------------------------------------------------
    final ExecutionInput executionInput =
        ExecutionInput.newExecutionInput().query("subscription {greeting1Subscription}").build();

    final GraphQL combinedGraphQL =
        createGraphQL("/defined_subscription/combined.graphqls", WIRING);
    final List<String> expected = new ArrayList<>();

    final CountDownLatch latch = new CountDownLatch(2);
    final ExecutionResult result = combinedGraphQL.execute(executionInput);
    final SubscriptionPublisher publisher = result.getData();
    publisher
        .getUpstreamPublisher()
        .subscribe(
            new Subscriber<>() {
              @Override
              public void onSubscribe(final Subscription s) {}

              @Override
              public void onNext(final Object o) {
                expected.add((String) o);
                latch.countDown();
              }

              @Override
              public void onError(final Throwable t) {}

              @Override
              public void onComplete() {}
            });

    latch.await(2, TimeUnit.SECONDS);
    Assertions.assertNotNull(expected.get(0));
    Assertions.assertNotNull(expected.get(1));
    Assertions.assertEquals(0, result.getErrors().size());

    // Stitching result ----------------------------------------------------

    final var project2GraphQL = createGraphQL("/defined_subscription/greeting2.graphqls", WIRING);
    final var introspection2Retriever = new TestUtils.TestIntrospectionRetriever(project2GraphQL);
    final var query2Retriever = new TestUtils.TestQueryRetriever(project2GraphQL);

    final Lilo lilo =
        Lilo.builder()
            .addSource(
                DefinedSchemaSource.create(
                    SCHEMA1_NAME, loadResource("/defined_subscription/greeting1.graphqls"), WIRING))
            .addSource(
                RemoteSchemaSource.create(SCHEMA2_NAME, introspection2Retriever, query2Retriever))
            .build();

    final List<String> stitchData = new ArrayList<>();
    final CountDownLatch stitchLatch = new CountDownLatch(1);
    final ExecutionResult stitchResult = lilo.stitch(executionInput);
    final SubscriptionPublisher stitchPublisher = stitchResult.getData();
    stitchPublisher
        .getUpstreamPublisher()
        .subscribe(
            new Subscriber<>() {
              @Override
              public void onSubscribe(final Subscription s) {}

              @Override
              public void onNext(final Object o) {
                stitchData.add((String) o);
                latch.countDown();
              }

              @Override
              public void onError(final Throwable t) {}

              @Override
              public void onComplete() {}
            });

    Assertions.assertFalse(stitchLatch.await(2, TimeUnit.SECONDS));
    Assertions.assertEquals(expected.get(0), stitchData.get(0));
    Assertions.assertEquals(expected.get(1), stitchData.get(1));
    Assertions.assertEquals(0, stitchResult.getErrors().size());
  }
}
