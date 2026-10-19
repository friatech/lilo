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

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeRuntimeWiring;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class NullSafetyTest {

  @Test
  void schemaSourceGettersThrowWhenNotLoaded() {

    final SchemaSource source =
        DefinedSchemaSource.create(
            "project", "type Query { name: String }", RuntimeWiring.newRuntimeWiring().build());

    Assertions.assertTrue(source.isSchemaNotLoaded());
    Assertions.assertThrows(IllegalArgumentException.class, source::getRuntimeWiring);
    Assertions.assertThrows(IllegalArgumentException.class, source::getTypeDefinitionRegistry);
  }

  @Test
  void graphQLQueryBuilderRequiresMandatoryFields() {

    final NullPointerException exception =
        Assertions.assertThrows(
            NullPointerException.class,
            () ->
                GraphQLQuery.builder()
                    .operationType(OperationDefinition.Operation.QUERY)
                    .queryNode(new Field("name"))
                    .arguments(Map.of())
                    .build());

    Assertions.assertEquals("query must be set", exception.getMessage());

    final NullPointerException argumentsException =
        Assertions.assertThrows(
            NullPointerException.class,
            () ->
                GraphQLQuery.builder()
                    .query("{\"query\":\"{name}\"}")
                    .operationType(OperationDefinition.Operation.QUERY)
                    .queryNode(new Field("name"))
                    .build());

    Assertions.assertEquals("arguments must be set", argumentsException.getMessage());
  }

  @Test
  void graphQLQueryBuilderBuildsWithAllFields() {

    final GraphQLQuery query =
        GraphQLQuery.builder()
            .query("{\"query\":\"{name}\"}")
            .operationType(OperationDefinition.Operation.QUERY)
            .queryNode(new Field("name"))
            .arguments(Map.of())
            .build();

    Assertions.assertEquals("{name}", query.getRequest().getQuery());
    Assertions.assertNull(query.getVariables());
  }

  @Test
  void unionWithoutTypenameFailsWithClearMessage() {

    final Lilo lilo =
        remoteLilo(
            "type Query { item: Item }\n"
                + "union Item = A | B\n"
                + "type A { x: String }\n"
                + "type B { y: String }",
            "{\"data\":{\"item\":{\"x\":\"value\"}}}");

    final IllegalArgumentException exception =
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () ->
                lilo.stitch(
                    ExecutionInput.newExecutionInput()
                        .query("{ item { ... on A { x } } }")
                        .build()));

    Assertions.assertEquals("Please provide __typename for union types", exception.getMessage());
  }

  @Test
  void remoteResponseWithoutDataAndErrorsReportsError() {

    final Lilo lilo = remoteLilo("type Query { greeting: String }", "{}");

    final ExecutionResult result =
        lilo.stitch(ExecutionInput.newExecutionInput().query("{ greeting }").build());

    Assertions.assertFalse(result.getErrors().isEmpty());
  }

  @Test
  void sourceWithoutMutationAndSubscriptionMerges() {

    final Lilo lilo =
        Lilo.builder()
            .addSource(
                DefinedSchemaSource.create(
                    "project",
                    "type Query { foo: Foo }\ntype Foo { name: String }",
                    RuntimeWiring.newRuntimeWiring()
                        .type(
                            TypeRuntimeWiring.newTypeWiring("Query")
                                .dataFetcher("foo", env -> Map.of("name", "bar")))
                        .build()))
            .build();

    final ExecutionResult result =
        lilo.stitch(ExecutionInput.newExecutionInput().query("{ foo { name } }").build());

    Assertions.assertTrue(result.getErrors().isEmpty());
    Assertions.assertEquals(Map.of("foo", Map.of("name", "bar")), result.getData());
  }

  private static Lilo remoteLilo(final String sdl, final String queryResponse) {

    final RuntimeWiring wiring =
        RuntimeWiring.newRuntimeWiring()
            .type(TypeRuntimeWiring.newTypeWiring("Item").typeResolver(env -> null))
            .build();
    final GraphQL remote =
        GraphQL.newGraphQL(
                new SchemaGenerator().makeExecutableSchema(new SchemaParser().parse(sdl), wiring))
            .build();

    final SyncQueryRetriever fixedResponseRetriever =
        new SyncQueryRetriever() {
          @Override
          public @NotNull String get(
              final @NotNull LiloContext liloContext,
              final @NotNull SchemaSource schemaSource,
              final @NotNull GraphQLQuery query,
              final @Nullable Object localContext) {
            return queryResponse;
          }
        };

    return Lilo.builder()
        .addSource(
            RemoteSchemaSource.create(
                "remote", new TestUtils.TestIntrospectionRetriever(remote), fixedResponseRetriever))
        .build();
  }
}
