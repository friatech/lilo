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
package io.fria.lilo;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Pins how Sync and unknown retriever kinds behave inside RemoteSchemaSource. */
class RetrieverBehaviourTest {

  private static final String SDL = "type Query { me: User }\ntype User { id: ID! }";

  private static GraphQL remote() {
    return GraphQL.newGraphQL(
            new SchemaGenerator()
                .makeExecutableSchema(
                    new SchemaParser().parse(SDL), RuntimeWiring.newRuntimeWiring().build()))
        .build();
  }

  private static SyncQueryRetriever sync(final java.util.function.Supplier<String> response) {
    return new SyncQueryRetriever() {
      @Override
      public @NotNull String get(
          final @NotNull LiloContext liloContext,
          final @NotNull SchemaSource schemaSource,
          final @NotNull GraphQLQuery query,
          final @Nullable Object localContext) {
        return response.get();
      }
    };
  }

  private static Lilo lilo(final SchemaSource source) {
    return Lilo.builder().addSource(source).build();
  }

  private static GraphQLQuery query() {
    return GraphQLQuery.builder()
        .query("{\"query\":\"{ me { id } }\"}")
        .operationType(OperationDefinition.Operation.QUERY)
        .queryNode(new Field("me"))
        .arguments(Map.of())
        .build();
  }

  @Test
  void introspectionRetrieverOfUnknownKindFailsAtFirstUseNotAtCreate() {

    final IntrospectionRetriever<String> unknown =
        (liloContext, schemaSource, query, localContext) -> "{}";

    final SchemaSource source =
        Assertions.assertDoesNotThrow(
            () -> RemoteSchemaSource.create("remote", unknown, sync(() -> "{}")));

    final RuntimeException e =
        Assertions.assertThrows(RuntimeException.class, () -> lilo(source).stitch("{ me { id } }"));
    Assertions.assertTrue(describe(e).startsWith("ClassCastException(class "), describe(e));
    Assertions.assertTrue(
        describe(e).contains("cannot be cast to class io.fria.lilo.SyncIntrospectionRetriever"),
        describe(e));
  }

  @Test
  void queryRetrieverOfUnknownKindFailsAtFirstUseNotAtCreate() {

    final QueryRetriever<String> unknown = (liloContext, schemaSource, query, localContext) -> "{}";

    final SchemaSource source =
        Assertions.assertDoesNotThrow(
            () ->
                RemoteSchemaSource.create(
                    "remote", new TestUtils.TestIntrospectionRetriever(remote()), unknown));

    final ExecutionResult result = lilo(source).stitch("{ me { id } }");
    final String message = describeResult(result);
    Assertions.assertTrue(
        message.startsWith("Exception while fetching data (/me) : class "), message);
    Assertions.assertTrue(
        message.contains("cannot be cast to class io.fria.lilo.SyncQueryRetriever"), message);
  }

  @Test
  void malformedSyncIntrospectionFailsTheLoad() {

    final SchemaSource source =
        RemoteSchemaSource.create(
            "remote",
            (SyncIntrospectionRetriever)
                (liloContext, schemaSource, query, localContext) -> "{\"foo\":1}",
            sync(() -> "{}"));

    final RuntimeException e =
        Assertions.assertThrows(RuntimeException.class, () -> lilo(source).stitch("{ me { id } }"));
    Assertions.assertEquals(
        "IllegalArgumentException(Introspection response is not valid, requires data section)>",
        describe(e));
  }

  @Test
  void syncQueryRetrieverRunsOffTheCallerThreadWhenFetchingThroughLilo() {

    final AtomicReference<Thread> thread = new AtomicReference<>();
    final SchemaSource source =
        RemoteSchemaSource.create(
            "remote",
            new TestUtils.TestIntrospectionRetriever(remote()),
            sync(
                () -> {
                  thread.set(Thread.currentThread());
                  return "{\"data\":{\"me\":{\"id\":\"1\"}}}";
                }));

    lilo(source).stitch("{ me { id } }");

    Assertions.assertNotSame(Thread.currentThread(), thread.get());
  }

  @Test
  void syncQueryRetrieverRunsOnTheCallerThreadWhenExecutedDirectly() {

    final AtomicReference<Thread> thread = new AtomicReference<>();
    final Lilo lilo =
        lilo(
            RemoteSchemaSource.create(
                "remote",
                new TestUtils.TestIntrospectionRetriever(remote()),
                sync(
                    () -> {
                      thread.set(Thread.currentThread());
                      return "{\"data\":{\"me\":{\"id\":\"1\"}}}";
                    })));
    final SchemaSource source = lilo.getContext().getSchemaSources().get("remote");

    final CompletableFuture<ExecutionResult> future =
        source.execute(lilo.getContext(), query(), null);
    future.join();

    Assertions.assertSame(Thread.currentThread(), thread.get());
  }

  @Test
  void failedSyncFetchesAreReportedAsBefore() {

    final String badJson = describeResult(failing(() -> "not json"));
    final String thrown =
        describeResult(
            failing(
                () -> {
                  throw new IllegalStateException("boom");
                }));
    final String noData = describeResult(failing(() -> "{}"));

    Assertions.assertEquals(
        "Exception while fetching data (/me) : java.util.concurrent.ExecutionException: "
            + "java.lang.IllegalArgumentException: Deserialization exception",
        badJson);
    Assertions.assertEquals("Exception while fetching data (/me) : boom", thrown);
    Assertions.assertEquals(
        "Exception while fetching data (/me) : Remote source returned a response without data",
        noData);
  }

  private static ExecutionResult failing(final java.util.function.Supplier<String> response) {
    return lilo(RemoteSchemaSource.create(
            "remote", new TestUtils.TestIntrospectionRetriever(remote()), sync(response)))
        .stitch("{ me { id } }");
  }

  private static String describe(final Throwable e) {
    final StringBuilder sb = new StringBuilder();
    for (Throwable t = e; t != null; t = t.getCause()) {
      sb.append(t.getClass().getSimpleName()).append('(').append(t.getMessage()).append(")>");
    }
    return sb.toString();
  }

  private static String describeResult(final ExecutionResult result) {
    return result.getErrors().isEmpty() ? "no errors" : result.getErrors().get(0).getMessage();
  }
}
