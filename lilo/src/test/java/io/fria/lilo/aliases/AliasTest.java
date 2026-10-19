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
package io.fria.lilo.aliases;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import io.fria.lilo.DefinedSchemaSource;
import io.fria.lilo.Lilo;
import io.fria.lilo.RemoteSchemaSource;
import io.fria.lilo.TestUtils;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AliasTest {

  private static final String SDL =
      "type W { name: String nick: String }\n"
          + "type User { id: ID! greet(l: String): String }\n"
          + "interface Node { id: ID! }\n"
          + "type Member implements Node { id: ID! name: String }\n"
          + "type Query { get: W me: User node: Node }";

  private static final Map<String, Object> ADA = Map.of("name", "Ada", "nick", "ada");

  private static final RuntimeWiring REMOTE_WIRING =
      RuntimeWiring.newRuntimeWiring()
          .type(
              newTypeWiring("Query")
                  .dataFetcher("get", env -> ADA)
                  .dataFetcher("me", env -> Map.of("id", "1"))
                  .dataFetcher("node", env -> Map.of("id", "2", "name", "Grace")))
          .type(
              newTypeWiring("User")
                  .dataFetcher(
                      "greet", env -> "en".equals(env.getArgument("l")) ? "Hello" : "Merhaba"))
          .type(newTypeWiring("Node").typeResolver(env -> env.getSchema().getObjectType("Member")))
          .build();

  private static @NotNull Lilo remoteLilo() {

    final GraphQL remote =
        GraphQL.newGraphQL(
                new SchemaGenerator()
                    .makeExecutableSchema(new SchemaParser().parse(SDL), REMOTE_WIRING))
            .build();

    return Lilo.builder()
        .addSource(
            RemoteSchemaSource.create(
                "remote",
                new TestUtils.TestIntrospectionRetriever(remote),
                new TestUtils.TestQueryRetriever(remote)))
        .build();
  }

  private static @NotNull Lilo definedLilo() {

    final RuntimeWiring wiring =
        RuntimeWiring.newRuntimeWiring()
            .type(
                newTypeWiring("Query")
                    .dataFetcher("get", env -> ADA)
                    .dataFetcher("me", env -> Map.of("id", "1"))
                    .dataFetcher(
                        "node", env -> Map.of("__typename", "Member", "id", "2", "name", "Grace")))
            .type(
                newTypeWiring("Node").typeResolver(env -> env.getSchema().getObjectType("Member")))
            .build();

    return Lilo.builder().addSource(DefinedSchemaSource.create("defined", SDL, wiring)).build();
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
  void aliasInsideInlineFragment() {
    assertData(
        Map.of("get", Map.of("n", "Ada")),
        stitch(remoteLilo(), "{ get { ... on W { n: name } } }"));
  }

  @Test
  void aliasInsideNamedFragment() {
    assertData(
        Map.of("get", Map.of("n", "Ada")),
        stitch(remoteLilo(), "query { get { ...F } }\nfragment F on W { n: name }"));
  }

  @Test
  void sameFieldTwiceWithDifferentArguments() {
    assertData(
        Map.of("me", Map.of("a", "Hello", "b", "Merhaba")),
        stitch(remoteLilo(), "{ me { a: greet(l: \"en\") b: greet(l: \"tr\") } }"));
  }

  @Test
  void aliasedRootFieldStillWorks() {
    assertData(Map.of("x", Map.of("id", "1")), stitch(remoteLilo(), "{ x: me { id } }"));
  }

  @Test
  void swappedAliasesOnRemoteSource() {
    assertData(
        Map.of("get", Map.of("nick", "Ada", "name", "ada")),
        stitch(remoteLilo(), "{ get { nick: name name: nick } }"));
  }

  @Test
  void aliasesOnAbstractTypeKeepTypeResolution() {
    assertData(
        Map.of("node", Map.of("k", "Member", "i", "2", "n", "Grace")),
        stitch(remoteLilo(), "{ node { k: __typename i: id ... on Member { n: name } } }"));
  }

  @Test
  void aliasOnDefinedSource() {
    assertData(
        Map.of("get", Map.of("n", "Ada", "nick", "Ada", "name", "ada")),
        stitch(definedLilo(), "{ get { n: name nick: name name: nick } }"));
  }

  @Test
  void aliasInsideFragmentOnDefinedSource() {
    assertData(
        Map.of("node", Map.of("k", "Member", "n", "Grace")),
        stitch(definedLilo(), "{ node { k: __typename ... on Member { n: name } } }"));
  }
}
