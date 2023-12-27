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

import static io.fria.lilo.JsonUtils.toObj;
import static io.fria.lilo.JsonUtils.toStr;

import graphql.GraphQL;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TestUtils {

  private TestUtils() {
    // Utility class
  }

  public static @NotNull GraphQL createGraphQL(
      final String schemaDefinitionPath, final RuntimeWiring runtimeWiring) {

    final var schemaDefinitionText = loadResource(schemaDefinitionPath);
    final var typeRegistry = new SchemaParser().parse(schemaDefinitionText);
    final var graphQLSchema =
        new SchemaGenerator().makeExecutableSchema(typeRegistry, runtimeWiring);

    return GraphQL.newGraphQL(graphQLSchema).build();
  }

  public static @NotNull String loadResource(final @NotNull String path) {

    try {
      final InputStream stream = TestUtils.class.getResourceAsStream(path);

      if (stream != null) {
        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      }
    } catch (final IOException e) {
      // pass to the exception
    }

    throw new IllegalArgumentException(String.format("Resource %s not found", path));
  }

  private static @NotNull String runQuery(
      final @NotNull GraphQL graphQL, final @NotNull String query) {

    final var graphQLRequestOptional = toObj(query, GraphQLRequest.class);

    if (graphQLRequestOptional.isEmpty()) {
      throw new IllegalArgumentException("Query request is invalid: Empty query");
    }

    return runQuery(graphQL, graphQLRequestOptional.get());
  }

  private static @NotNull String runQuery(
      final @NotNull GraphQL graphQL, final @NotNull GraphQLRequest graphQLRequest) {
    return toStr(graphQL.execute(graphQLRequest.toExecutionInput()));
  }

  public static class TestIntrospectionRetriever implements SyncIntrospectionRetriever {

    private GraphQL graphQL;

    public TestIntrospectionRetriever(final @NotNull GraphQL graphQL) {
      this.graphQL = graphQL;
    }

    @Override
    public @NotNull String get(
        final @NotNull LiloContext liloContext,
        final @NotNull SchemaSource schemaSource,
        final @NotNull String query,
        final @Nullable Object localContext) {
      return runQuery(this.graphQL, query);
    }

    public void setGraphQL(final @NotNull GraphQL graphQL) {
      this.graphQL = graphQL;
    }
  }

  public static class TestAsyncIntrospectionRetriever implements AsyncIntrospectionRetriever {

    private final int wait;
    private GraphQL graphQL;

    public TestAsyncIntrospectionRetriever(final @NotNull GraphQL graphQL, final int wait) {
      this.graphQL = graphQL;
      this.wait = wait;
    }

    @Override
    public @NotNull CompletableFuture<String> get(
        final @NotNull LiloContext liloContext,
        final @NotNull SchemaSource schemaSource,
        final @NotNull String query,
        final @Nullable Object localContext) {

      return CompletableFuture.supplyAsync(
          () -> {
            try {
              Thread.sleep(this.wait);
            } catch (final InterruptedException e) {
              throw new RuntimeException(e);
            }

            return runQuery(TestAsyncIntrospectionRetriever.this.graphQL, query);
          });
    }

    public void setGraphQL(final @NotNull GraphQL graphQL) {
      this.graphQL = graphQL;
    }
  }

  public static class TestQueryRetriever implements SyncQueryRetriever {

    private GraphQL graphQL;

    public TestQueryRetriever(final @NotNull GraphQL graphQL) {
      this.graphQL = graphQL;
    }

    @Override
    public @NotNull String get(
        final @NotNull LiloContext liloContext,
        final @NotNull SchemaSource schemaSource,
        final @NotNull GraphQLQuery query,
        final @Nullable Object localContext) {
      return runQuery(this.graphQL, query.getQuery());
    }

    public void setGraphQL(final @NotNull GraphQL graphQL) {
      this.graphQL = graphQL;
    }
  }

  public static class TestAsyncQueryRetriever implements AsyncQueryRetriever {

    private GraphQL graphQL;

    public TestAsyncQueryRetriever(final @NotNull GraphQL graphQL) {
      this.graphQL = graphQL;
    }

    @Override
    public @NotNull CompletableFuture<String> get(
        final @NotNull LiloContext liloContext,
        final @NotNull SchemaSource schemaSource,
        final @NotNull GraphQLQuery query,
        final @Nullable Object localContext) {

      return CompletableFuture.supplyAsync(
          () -> runQuery(TestAsyncQueryRetriever.this.graphQL, query.getQuery()));
    }

    public void setGraphQL(final @NotNull GraphQL graphQL) {
      this.graphQL = graphQL;
    }
  }
}
