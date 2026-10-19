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
package io.fria.lilo.abstract_types;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.TypeResolver;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import io.fria.lilo.DefinedSchemaSource;
import io.fria.lilo.GraphQLQuery;
import io.fria.lilo.Lilo;
import io.fria.lilo.LiloContext;
import io.fria.lilo.RemoteSchemaSource;
import io.fria.lilo.SchemaSource;
import io.fria.lilo.SyncQueryRetriever;
import io.fria.lilo.TestUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AbstractTypesTest {

  private static final String INTERFACE_SDL =
      "interface Node { id: ID! }\n"
          + "type User implements Node { id: ID! name: String }\n"
          + "type Query { node: Node }";

  private static final String UNION_SDL =
      "type User { id: ID! name: String }\n"
          + "type Robot { id: ID! model: String }\n"
          + "union Result = User | Robot\n"
          + "type Query { result: Result }";

  private static final Map<String, Object> ADA = Map.of("id", "1", "name", "Ada");

  // The remote resolves the concrete type on its own; the gateway only sees the JSON response
  private static final TypeResolver REMOTE_TYPE_RESOLVER =
      env -> {
        final Map<String, Object> source = env.getObject();
        return env.getSchema().getObjectType(source.containsKey("name") ? "User" : "Robot");
      };

  private static @NotNull Lilo remoteLilo(
      final @NotNull String sdl,
      final @NotNull String abstractTypeName,
      final @NotNull String fieldName) {
    return remoteLilo(sdl, abstractTypeName, fieldName, ADA);
  }

  private static @NotNull Lilo remoteLilo(
      final @NotNull String sdl,
      final @NotNull String abstractTypeName,
      final @NotNull String fieldName,
      final @NotNull Map<String, Object> fieldValue) {
    return remoteLilo(sdl, abstractTypeName, fieldName, fieldValue, new ArrayList<>());
  }

  private static @NotNull Lilo remoteLilo(
      final @NotNull String sdl,
      final @NotNull String abstractTypeName,
      final @NotNull String fieldName,
      final @NotNull Map<String, Object> fieldValue,
      final @NotNull List<String> sentQueries) {

    final RuntimeWiring wiring =
        RuntimeWiring.newRuntimeWiring()
            .type(newTypeWiring("Query").dataFetcher(fieldName, env -> fieldValue))
            .type(newTypeWiring(abstractTypeName).typeResolver(REMOTE_TYPE_RESOLVER))
            .build();
    final GraphQL remote =
        GraphQL.newGraphQL(
                new SchemaGenerator().makeExecutableSchema(new SchemaParser().parse(sdl), wiring))
            .build();

    return Lilo.builder()
        .addSource(
            RemoteSchemaSource.create(
                "remote",
                new TestUtils.TestIntrospectionRetriever(remote),
                new RecordingQueryRetriever(remote, sentQueries)))
        .build();
  }

  private static @NotNull ExecutionResult stitch(
      final @NotNull Lilo lilo, final @NotNull String query) {
    return lilo.stitch(ExecutionInput.newExecutionInput().query(query).build());
  }

  private static void assertData(
      final @NotNull Map<String, Object> expected, final @NotNull ExecutionResult result) {
    Assertions.assertEquals(List.of(), result.getErrors());
    Assertions.assertEquals(expected, result.getData());
  }

  @Test
  void interfaceFieldResolvesWithoutClientAskingTypename() {

    final Lilo lilo = remoteLilo(INTERFACE_SDL, "Node", "node");

    assertData(Map.of("node", ADA), stitch(lilo, "{ node { id ... on User { name } } }"));
  }

  @Test
  void unionFieldResolvesWithoutClientAskingTypename() {

    final Lilo lilo = remoteLilo(UNION_SDL, "Result", "result");

    assertData(Map.of("result", ADA), stitch(lilo, "{ result { ... on User { id name } } }"));
  }

  @Test
  void interfaceFieldResolvesThroughNamedFragment() {

    final Lilo lilo = remoteLilo(INTERFACE_SDL, "Node", "node");

    assertData(
        Map.of("node", ADA),
        stitch(
            lilo,
            "query { node { ...NodeFields } }\n"
                + "fragment NodeFields on Node { id ... on User { name } }"));
  }

  @Test
  void nestedInterfaceListResolves() {

    final Lilo lilo =
        remoteLilo(
            "interface Node { id: ID! }\n"
                + "type User implements Node { id: ID! name: String }\n"
                + "type Holder { nodes: [Node!]! }\n"
                + "type Query { holder: Holder }",
            "Node",
            "holder",
            Map.of("nodes", List.of(ADA)));

    assertData(
        Map.of("holder", Map.of("nodes", List.of(ADA))),
        stitch(lilo, "{ holder { nodes { id ... on User { name } } } }"));
  }

  @Test
  void aliasedTypenameDoesNotSuppressInjection() {

    final Lilo lilo = remoteLilo(INTERFACE_SDL, "Node", "node");

    assertData(
        Map.of("node", Map.of("kind", "User", "id", "1", "name", "Ada")),
        stitch(lilo, "{ node { kind: __typename id ... on User { name } } }"));
  }

  @Test
  void typenameExcludedByIncludeDirectiveDoesNotSuppressInjection() {

    final Lilo lilo = remoteLilo(INTERFACE_SDL, "Node", "node");

    assertData(
        Map.of("node", Map.of("id", "1")),
        stitch(lilo, "{ node { __typename @include(if: false) id } }"));
  }

  @Test
  void typenameSkippedByVariableDoesNotSuppressInjection() {

    final Lilo lilo = remoteLilo(INTERFACE_SDL, "Node", "node");

    final ExecutionResult result =
        lilo.stitch(
            ExecutionInput.newExecutionInput()
                .query("query($s: Boolean!) { node { __typename @skip(if: $s) id } }")
                .variables(Map.of("s", true))
                .build());

    assertData(Map.of("node", Map.of("id", "1")), result);
  }

  @Test
  void plainTypenameSelectionIsSentOnce() {

    final List<String> sentQueries = new ArrayList<>();
    final Lilo lilo = remoteLilo(INTERFACE_SDL, "Node", "node", ADA, sentQueries);

    assertData(
        Map.of("node", Map.of("__typename", "User", "id", "1")),
        stitch(lilo, "{ node { __typename id } }"));

    Assertions.assertEquals(1, sentQueries.size());
    final String sent = sentQueries.get(0);
    Assertions.assertEquals(
        1, sent.split("__typename", -1).length - 1, () -> "Sent query: " + sent);
  }

  @Test
  void definedSourceInterfaceFieldResolves() {

    // graphql-java requires the source's own schema to carry a type resolver for Node; the
    // gateway does not reuse it and resolves from the __typename in the source result instead
    final RuntimeWiring wiring =
        RuntimeWiring.newRuntimeWiring()
            .type(
                newTypeWiring("Query")
                    .dataFetcher(
                        "node", env -> Map.of("__typename", "User", "id", "1", "name", "Ada")))
            .type(
                newTypeWiring("Node")
                    .typeResolver(
                        env -> {
                          final Map<String, Object> source = env.getObject();
                          return env.getSchema().getObjectType(source.get("__typename").toString());
                        }))
            .build();

    final Lilo lilo =
        Lilo.builder()
            .addSource(DefinedSchemaSource.create("defined", INTERFACE_SDL, wiring))
            .build();

    assertData(Map.of("node", ADA), stitch(lilo, "{ node { id ... on User { name } } }"));
  }

  @Test
  void definedSourceWithoutTypenameFailsWithClearMessage() {

    final RuntimeWiring wiring =
        RuntimeWiring.newRuntimeWiring()
            .type(
                newTypeWiring("Query").dataFetcher("node", env -> Map.of("id", "1", "name", "Ada")))
            .type(newTypeWiring("Node").typeResolver(env -> env.getSchema().getObjectType("User")))
            .build();

    final Lilo lilo =
        Lilo.builder()
            .addSource(DefinedSchemaSource.create("defined", INTERFACE_SDL, wiring))
            .build();

    final IllegalArgumentException exception =
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> stitch(lilo, "{ node { id ... on User { name } } }"));

    Assertions.assertEquals(
        "Please provide __typename for interface types", exception.getMessage());
  }

  /** Forwards to the remote and records the query text sent to it. */
  private static final class RecordingQueryRetriever implements SyncQueryRetriever {

    private final TestUtils.TestQueryRetriever delegate;
    private final List<String> sentQueries;

    RecordingQueryRetriever(
        final @NotNull GraphQL graphQL, final @NotNull List<String> sentQueries) {
      this.delegate = new TestUtils.TestQueryRetriever(graphQL);
      this.sentQueries = sentQueries;
    }

    @Override
    public @NotNull String get(
        final @NotNull LiloContext liloContext,
        final @NotNull SchemaSource schemaSource,
        final @NotNull GraphQLQuery query,
        final @Nullable Object localContext) {

      synchronized (this.sentQueries) {
        this.sentQueries.add(query.getRequest().getQuery());
      }

      return this.delegate.get(liloContext, schemaSource, query, localContext);
    }
  }
}
