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
package io.fria.lilo.extensions;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.scalar.GraphqlStringCoercing;
import graphql.schema.GraphQLScalarType;
import graphql.schema.TypeResolver;
import graphql.schema.idl.RuntimeWiring;
import io.fria.lilo.DefinedSchemaSource;
import io.fria.lilo.Lilo;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Extensions in a source's own SDL are part of the types the gateway merges. */
class TypeExtensionTest {

  private static final TypeResolver THING_RESOLVER = env -> env.getSchema().getObjectType("Thing");

  private static ExecutionResult stitch(final Lilo lilo, final String query) {
    return lilo.stitch(ExecutionInput.newExecutionInput().query(query).build());
  }

  private static Lilo lilo(final String sdl, final RuntimeWiring wiring) {
    return Lilo.builder().addSource(DefinedSchemaSource.create("project1", sdl, wiring)).build();
  }

  private static Lilo twoSources(final String sdl1, final String sdl2) {
    final RuntimeWiring wiring1 =
        RuntimeWiring.newRuntimeWiring()
            .type(newTypeWiring("Query").dataFetcher("a", env -> Map.of("x", 1, "y", 2)))
            .build();
    final RuntimeWiring wiring2 =
        RuntimeWiring.newRuntimeWiring()
            .type(newTypeWiring("Query").dataFetcher("b", env -> Map.of("x", 3, "y", 4)))
            .build();

    return Lilo.builder()
        .addSource(DefinedSchemaSource.create("project1", sdl1, wiring1))
        .addSource(DefinedSchemaSource.create("project2", sdl2, wiring2))
        .build();
  }

  private static Throwable rootCause(final Lilo lilo, final String query) {
    final Throwable thrown =
        Assertions.assertThrows(RuntimeException.class, () -> stitch(lilo, query));
    Throwable root = thrown;
    while (root.getCause() != null) {
      root = root.getCause();
    }
    return root;
  }

  @Test
  void objectExtensionFieldsAreMerged() {

    final Lilo lilo =
        lilo(
            "type Query { foo: Foo } type Foo { a: Int } extend type Foo { b: Int }",
            RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Query").dataFetcher("foo", env -> Map.of("a", 1, "b", 2)))
                .build());

    final ExecutionResult result = stitch(lilo, "{ foo { a b } }");

    Assertions.assertEquals(0, result.getErrors().size(), result.getErrors().toString());
    Assertions.assertEquals(Map.of("foo", Map.of("a", 1, "b", 2)), result.getData());
  }

  @Test
  void objectExtensionInterfacesAreMerged() {

    final Lilo lilo =
        lilo(
            "type Query { node: Node } interface Node { id: ID } "
                + "type Thing { id: ID } extend type Thing implements Node",
            RuntimeWiring.newRuntimeWiring()
                .type(
                    newTypeWiring("Query")
                        .dataFetcher("node", env -> Map.of("__typename", "Thing", "id", "1")))
                .type(newTypeWiring("Node").typeResolver(THING_RESOLVER))
                .build());

    final ExecutionResult result = stitch(lilo, "{ node { id ... on Thing { id } } }");

    Assertions.assertEquals(0, result.getErrors().size(), result.getErrors().toString());
    Assertions.assertEquals(Map.of("node", Map.of("id", "1")), result.getData());
  }

  @Test
  void interfaceExtensionFieldsAreMerged() {

    final Lilo lilo =
        lilo(
            "type Query { node: Node } interface Node { id: ID } "
                + "extend interface Node { name: String } "
                + "type Thing implements Node { id: ID name: String }",
            RuntimeWiring.newRuntimeWiring()
                .type(
                    newTypeWiring("Query")
                        .dataFetcher(
                            "node", env -> Map.of("__typename", "Thing", "id", "1", "name", "n")))
                .type(newTypeWiring("Node").typeResolver(THING_RESOLVER))
                .build());

    final ExecutionResult result = stitch(lilo, "{ node { id name } }");

    Assertions.assertEquals(0, result.getErrors().size(), result.getErrors().toString());
    Assertions.assertEquals(Map.of("node", Map.of("id", "1", "name", "n")), result.getData());
  }

  @Test
  void enumExtensionValuesAreMerged() {

    final Lilo lilo =
        lilo(
            "type Query { color: Color } enum Color { RED } extend enum Color { GREEN }",
            RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Query").dataFetcher("color", env -> "GREEN"))
                .build());

    final ExecutionResult result = stitch(lilo, "{ color }");

    Assertions.assertEquals(0, result.getErrors().size(), result.getErrors().toString());
    Assertions.assertEquals(Map.of("color", "GREEN"), result.getData());
  }

  @Test
  void inputExtensionFieldsAreMerged() {

    final Lilo lilo =
        lilo(
            "type Query { echo(f: Filter): String } input Filter { a: Int } "
                + "extend input Filter { b: Int }",
            RuntimeWiring.newRuntimeWiring()
                .type(
                    newTypeWiring("Query")
                        .dataFetcher(
                            "echo",
                            env -> {
                              final Map<String, Object> f = env.getArgument("f");
                              return f.get("a") + "-" + f.get("b");
                            }))
                .build());

    final ExecutionResult result = stitch(lilo, "{ echo(f: { a: 1, b: 2 }) }");

    Assertions.assertEquals(0, result.getErrors().size(), result.getErrors().toString());
    Assertions.assertEquals(Map.of("echo", "1-2"), result.getData());
  }

  @Test
  void unionExtensionMembersAreMerged() {

    final Lilo lilo =
        lilo(
            "type Query { item: Item } type A { a: Int } type B { b: Int } "
                + "union Item = A extend union Item = B",
            RuntimeWiring.newRuntimeWiring()
                .type(
                    newTypeWiring("Query")
                        .dataFetcher("item", env -> Map.of("__typename", "B", "b", 2)))
                .type(newTypeWiring("Item").typeResolver(env -> env.getSchema().getObjectType("B")))
                .build());

    final ExecutionResult result = stitch(lilo, "{ item { ... on B { b } } }");

    Assertions.assertEquals(0, result.getErrors().size(), result.getErrors().toString());
    Assertions.assertEquals(Map.of("item", Map.of("b", 2)), result.getData());
  }

  @Test
  void scalarExtensionDirectivesAreMerged() {

    final RuntimeWiring wiring =
        RuntimeWiring.newRuntimeWiring()
            .type(newTypeWiring("Query").dataFetcher("a", env -> "2024-01-01"))
            .scalar(
                GraphQLScalarType.newScalar()
                    .name("Date")
                    .coercing(new GraphqlStringCoercing())
                    .build())
            .build();
    final RuntimeWiring wiring2 =
        RuntimeWiring.newRuntimeWiring()
            .type(newTypeWiring("Query").dataFetcher("b", env -> "2024-01-02"))
            .scalar(
                GraphQLScalarType.newScalar()
                    .name("Date")
                    .coercing(new GraphqlStringCoercing())
                    .build())
            .build();

    // The same scalar, once with the directive on the definition and once on an extension
    final Lilo lilo =
        Lilo.builder()
            .addSource(
                DefinedSchemaSource.create(
                    "project1",
                    "directive @tag on SCALAR type Query { a: Date } scalar Date "
                        + "extend scalar Date @tag",
                    wiring))
            .addSource(
                DefinedSchemaSource.create(
                    "project2",
                    "directive @tag on SCALAR type Query { b: Date } scalar Date @tag",
                    wiring2))
            .build();

    final ExecutionResult result = stitch(lilo, "{ a b }");

    Assertions.assertEquals(0, result.getErrors().size(), result.getErrors().toString());
    Assertions.assertEquals(Map.of("a", "2024-01-01", "b", "2024-01-02"), result.getData());
  }

  @Test
  void rootTypeExtensionFieldsAreMerged() {

    final Lilo lilo =
        lilo(
            "type Query { a: Int } extend type Query { x: Int }",
            RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Query").dataFetcher("a", env -> 1).dataFetcher("x", env -> 2))
                .build());

    final ExecutionResult result = stitch(lilo, "{ a x }");

    Assertions.assertEquals(0, result.getErrors().size(), result.getErrors().toString());
    Assertions.assertEquals(Map.of("a", 1, "x", 2), result.getData());
  }

  @Test
  void rootTypeExtensionFieldsAreMergedAcrossSources() {

    final RuntimeWiring wiring1 =
        RuntimeWiring.newRuntimeWiring()
            .type(newTypeWiring("Query").dataFetcher("a", env -> 1).dataFetcher("x", env -> 2))
            .build();
    final RuntimeWiring wiring2 =
        RuntimeWiring.newRuntimeWiring()
            .type(newTypeWiring("Query").dataFetcher("b", env -> 3))
            .build();

    final Lilo lilo =
        Lilo.builder()
            .addSource(
                DefinedSchemaSource.create(
                    "project1", "type Query { a: Int } extend type Query { x: Int }", wiring1))
            .addSource(DefinedSchemaSource.create("project2", "type Query { b: Int }", wiring2))
            .build();

    final ExecutionResult result = stitch(lilo, "{ a x b }");

    Assertions.assertEquals(0, result.getErrors().size(), result.getErrors().toString());
    Assertions.assertEquals(Map.of("a", 1, "x", 2, "b", 3), result.getData());
  }

  @Test
  void extendedTypeEqualToCompleteTypeIsAcceptedInEitherOrder() {

    final String complete = "type Query { a: Pair } type Pair { x: Int y: Int }";
    final String extended =
        "type Query { b: Pair } type Pair { x: Int } extend type Pair { y: Int }";
    final String completeB = "type Query { b: Pair } type Pair { x: Int y: Int }";
    final String extendedA =
        "type Query { a: Pair } type Pair { x: Int } extend type Pair { y: Int }";

    for (final Lilo lilo :
        new Lilo[] {twoSources(complete, extended), twoSources(extendedA, completeB)}) {
      final ExecutionResult result = stitch(lilo, "{ a { x y } b { x y } }");

      Assertions.assertEquals(0, result.getErrors().size(), result.getErrors().toString());
      Assertions.assertEquals(
          Map.of("a", Map.of("x", 1, "y", 2), "b", Map.of("x", 3, "y", 4)), result.getData());
    }
  }

  @Test
  void extensionThatMakesTypesDifferConflictsInEitherOrder() {

    final String plain = "type Query { a: Pair } type Pair { x: Int }";
    final String extended =
        "type Query { b: Pair } type Pair { x: Int } extend type Pair { y: Int }";
    final String plainB = "type Query { b: Pair } type Pair { x: Int }";
    final String extendedA =
        "type Query { a: Pair } type Pair { x: Int } extend type Pair { y: Int }";

    for (final Lilo lilo :
        new Lilo[] {twoSources(plain, extended), twoSources(extendedA, plainB)}) {
      final Throwable root = rootCause(lilo, "{ a { x } }");

      Assertions.assertInstanceOf(IllegalArgumentException.class, root);
      Assertions.assertTrue(root.getMessage().contains("Pair"), root.getMessage());
      Assertions.assertTrue(root.getMessage().contains("project1"), root.getMessage());
      Assertions.assertTrue(root.getMessage().contains("project2"), root.getMessage());
    }
  }

  @Test
  void extendingTypeOfAnotherSourceStillFails() {

    final Lilo lilo =
        twoSources(
            "type Query { a: Pair } type Pair { x: Int y: Int }",
            "type Query { b: Int } extend type Pair { z: Int }");

    final Throwable root = rootCause(lilo, "{ a { x } }");

    Assertions.assertTrue(
        root.getMessage().contains("missing its base underlying type"), root.getMessage());
  }

  @Test
  void extensionsSurviveReMerge() {

    final Lilo lilo =
        lilo(
            "type Query { foo: Foo } type Foo { a: Int } extend type Foo { b: Int }",
            RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Query").dataFetcher("foo", env -> Map.of("a", 1, "b", 2)))
                .build());

    Assertions.assertEquals(0, stitch(lilo, "{ foo { b } }").getErrors().size());
    lilo.getContext().invalidateAll();

    final ExecutionResult result = stitch(lilo, "{ foo { b } }");
    Assertions.assertEquals(0, result.getErrors().size(), result.getErrors().toString());
    Assertions.assertEquals(Map.of("foo", Map.of("b", 2)), result.getData());
  }

  @Test
  void customRootTypeExtensionFieldsAreMerged() {

    final Lilo lilo =
        lilo(
            "schema { query: Q } type Q { a: Int } extend type Q { x: Int }",
            RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Q").dataFetcher("a", env -> 1).dataFetcher("x", env -> 2))
                .build());

    final ExecutionResult result = stitch(lilo, "{ a x }");

    Assertions.assertEquals(0, result.getErrors().size(), result.getErrors().toString());
    Assertions.assertEquals(Map.of("a", 1, "x", 2), result.getData());
  }

  @Test
  void extensionWrittenBeforeItsBaseIsMerged() {

    final Lilo lilo =
        lilo(
            "extend type Foo { b: Int } type Query { foo: Foo } type Foo { a: Int }",
            RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Query").dataFetcher("foo", env -> Map.of("a", 1, "b", 2)))
                .build());

    final ExecutionResult result = stitch(lilo, "{ foo { a b } }");

    Assertions.assertEquals(0, result.getErrors().size(), result.getErrors().toString());
    Assertions.assertEquals(Map.of("foo", Map.of("a", 1, "b", 2)), result.getData());
  }

  @Test
  void objectDirectiveOnExtensionEqualsOnePieceTypeInEitherOrder() {

    final String directives = "directive @a on OBJECT directive @b on OBJECT ";
    final String split = directives + "type Pair @b { x: Int y: Int } extend type Pair @a";
    final String onePiece = directives + "type Pair @a @b { x: Int y: Int }";

    for (final Lilo lilo :
        new Lilo[] {
          twoSources("type Query { a: Pair } " + split, "type Query { b: Pair } " + onePiece),
          twoSources("type Query { a: Pair } " + onePiece, "type Query { b: Pair } " + split)
        }) {
      final ExecutionResult result = stitch(lilo, "{ a { x y } b { x y } }");

      Assertions.assertEquals(0, result.getErrors().size(), result.getErrors().toString());
      Assertions.assertEquals(
          Map.of("a", Map.of("x", 1, "y", 2), "b", Map.of("x", 3, "y", 4)), result.getData());
    }
  }
}
