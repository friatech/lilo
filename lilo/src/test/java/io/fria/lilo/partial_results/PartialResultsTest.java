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
package io.fria.lilo.partial_results;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import io.fria.lilo.AsyncIntrospectionRetriever;
import io.fria.lilo.GraphQLQuery;
import io.fria.lilo.Lilo;
import io.fria.lilo.LiloContext;
import io.fria.lilo.RemoteSchemaSource;
import io.fria.lilo.SchemaSource;
import io.fria.lilo.SyncIntrospectionRetriever;
import io.fria.lilo.SyncQueryRetriever;
import io.fria.lilo.TestUtils;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PartialResultsTest {

  private static final String SDL = "type Query { me: User }\ntype User { id: ID! nick: String }";

  private static SyncQueryRetriever fixed(final String response) {
    return new SyncQueryRetriever() {
      @Override
      public @NotNull String get(
          final @NotNull LiloContext liloContext,
          final @NotNull SchemaSource schemaSource,
          final @NotNull GraphQLQuery query,
          final @Nullable Object localContext) {
        return response;
      }
    };
  }

  private static Lilo remoteLilo(final String response) {
    final GraphQL remote =
        GraphQL.newGraphQL(
                new SchemaGenerator()
                    .makeExecutableSchema(
                        new SchemaParser().parse(SDL), RuntimeWiring.newRuntimeWiring().build()))
            .build();

    return Lilo.builder()
        .addSource(
            RemoteSchemaSource.create(
                "remote", new TestUtils.TestIntrospectionRetriever(remote), fixed(response)))
        .build();
  }

  @Test
  void partialDataSurvivesRemoteError() {

    final Lilo lilo =
        remoteLilo(
            "{\"data\":{\"me\":{\"id\":\"1\",\"nick\":null}},"
                + "\"errors\":[{\"message\":\"nick failed\",\"path\":[\"me\",\"nick\"]}]}");

    final ExecutionResult result = lilo.stitch("{ me { id nick } }");

    final Map<String, Object> me = new java.util.HashMap<>();
    me.put("id", "1");
    me.put("nick", null);
    Assertions.assertEquals(Map.of("me", me), result.getData());
    Assertions.assertEquals(1, result.getErrors().size());
    Assertions.assertTrue(result.getErrors().get(0).getMessage().contains("nick failed"));
  }

  @Test
  void errorsWithoutDataStillFail() {

    final Lilo lilo = remoteLilo("{\"errors\":[{\"message\":\"boom\"}]}");

    final ExecutionResult result = lilo.stitch("{ me { id nick } }");

    Assertions.assertEquals(1, result.getErrors().size());
    Assertions.assertTrue(result.getErrors().get(0).getMessage().contains("boom"));
  }

  @Test
  void stitchWithAllSourcesDownThrowsSchemaProblemNotWrapped() {

    final Lilo lilo =
        Lilo.builder()
            .addSource(
                RemoteSchemaSource.create(
                    "remote",
                    new SyncIntrospectionRetriever() {
                      @Override
                      public @NotNull String get(
                          final @NotNull LiloContext liloContext,
                          final @NotNull SchemaSource schemaSource,
                          final @NotNull String query,
                          final @Nullable Object localContext) {
                        throw new IllegalStateException("remote is down");
                      }
                    },
                    fixed("{}")))
            .build();

    final RuntimeException exception =
        Assertions.assertThrows(RuntimeException.class, () -> lilo.stitch("{ me { id } }"));

    Assertions.assertFalse(exception.getMessage().startsWith("java.util.concurrent"));
    Assertions.assertFalse(exception.getCause() instanceof java.util.concurrent.ExecutionException);
  }

  @Test
  void interruptFlagIsRestored() {

    final Lilo lilo =
        Lilo.builder()
            .addSource(
                RemoteSchemaSource.create(
                    "remote",
                    (AsyncIntrospectionRetriever)
                        (liloContext, schemaSource, query, localContext) ->
                            new CompletableFuture<>(),
                    fixed("{}")))
            .build();

    Thread.currentThread().interrupt();
    try {
      Assertions.assertThrows(RuntimeException.class, () -> lilo.stitch("{ me { id } }"));
      Assertions.assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }
}
