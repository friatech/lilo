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
package io.fria.lilo.conflicting_types;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.schema.TypeResolver;
import graphql.schema.idl.RuntimeWiring;
import io.fria.lilo.DefinedSchemaSource;
import io.fria.lilo.Lilo;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ConflictingTypesTest {

  private static final TypeResolver USER_RESOLVER = env -> env.getSchema().getObjectType("User");

  private static Lilo lilo(final String sdl1, final String sdl2) {
    final RuntimeWiring wiring1 =
        RuntimeWiring.newRuntimeWiring()
            .type(newTypeWiring("Query").dataFetcher("a", env -> Map.of("name", "A")))
            .build();
    final RuntimeWiring wiring2 =
        RuntimeWiring.newRuntimeWiring()
            .type(newTypeWiring("Query").dataFetcher("b", env -> Map.of("name", "B")))
            .build();

    return Lilo.builder()
        .addSource(DefinedSchemaSource.create("project1", sdl1, wiring1))
        .addSource(DefinedSchemaSource.create("project2", sdl2, wiring2))
        .build();
  }

  @Test
  void identicalSharedTypeIsAccepted() {

    final Lilo lilo =
        lilo(
            "type Query { a: Tag } type Tag { name: String }",
            "type Query { b: Tag } type Tag { name: String }");

    final ExecutionResult result =
        lilo.stitch(ExecutionInput.newExecutionInput().query("{ a { name } b { name } }").build());

    Assertions.assertEquals(0, result.getErrors().size());
    Assertions.assertEquals(
        Map.of("a", Map.of("name", "A"), "b", Map.of("name", "B")), result.getData());
  }

  @Test
  void differingSharedTypeFailsAtMerge() {

    final Lilo lilo =
        lilo(
            "type Query { a: User } type User { id: ID }",
            "type Query { b: User } type User { id: ID email: String }");

    final ExecutionInput executionInput =
        ExecutionInput.newExecutionInput().query("{ a { id } }").build();

    final Throwable thrown =
        Assertions.assertThrows(RuntimeException.class, () -> lilo.stitch(executionInput));
    Throwable root = thrown;
    while (root.getCause() != null) {
      root = root.getCause();
    }
    Assertions.assertInstanceOf(IllegalArgumentException.class, root);
    Assertions.assertTrue(root.getMessage().contains("User"));
    Assertions.assertTrue(root.getMessage().contains("project1"));
    Assertions.assertTrue(root.getMessage().contains("project2"));
  }

  @Test
  void descriptionOnlyDifferenceIsAccepted() {

    final Lilo lilo =
        lilo(
            "# header comment\n\"A tag\" type Tag { \"the name\" name: String } type Query { a: Tag }",
            "type Tag { name: String } type Query { b: Tag }");

    final ExecutionResult result =
        lilo.stitch(ExecutionInput.newExecutionInput().query("{ a { name } b { name } }").build());

    Assertions.assertEquals(0, result.getErrors().size());
  }

  @Test
  void fieldOrderDifferenceIsAccepted() {

    final RuntimeWiring wiring1 =
        RuntimeWiring.newRuntimeWiring()
            .type(newTypeWiring("Query").dataFetcher("a", env -> Map.of("id", "1", "email", "a@x")))
            .type(newTypeWiring("Node").typeResolver(USER_RESOLVER))
            .type(newTypeWiring("Named").typeResolver(USER_RESOLVER))
            .type(newTypeWiring("Thing").typeResolver(USER_RESOLVER))
            .build();
    final RuntimeWiring wiring2 =
        RuntimeWiring.newRuntimeWiring()
            .type(newTypeWiring("Query").dataFetcher("b", env -> Map.of("id", "2", "email", "b@x")))
            .type(newTypeWiring("Node").typeResolver(USER_RESOLVER))
            .type(newTypeWiring("Named").typeResolver(USER_RESOLVER))
            .type(newTypeWiring("Thing").typeResolver(USER_RESOLVER))
            .build();

    final Lilo lilo =
        Lilo.builder()
            .addSource(
                DefinedSchemaSource.create(
                    "project1",
                    "type Query { a(x: Int, y: String): User } "
                        + "type User implements Node & Named { id: ID email: String "
                        + "tags(first: Int, after: String): [String] } "
                        + "interface Node { id: ID } interface Named { email: String } "
                        + "input Filter { a: Int b: String } "
                        + "union Thing = User | Other type Other { id: ID } "
                        + "directive @tagged on FIELD_DEFINITION | OBJECT",
                    wiring1))
            .addSource(
                DefinedSchemaSource.create(
                    "project2",
                    "type Query { b: User } "
                        + "type User implements Named & Node { "
                        + "tags(after: String, first: Int): [String] email: String id: ID } "
                        + "interface Node { id: ID } interface Named { email: String } "
                        + "input Filter { b: String a: Int } "
                        + "union Thing = Other | User type Other { id: ID } "
                        + "directive @tagged on OBJECT | FIELD_DEFINITION",
                    wiring2))
            .build();

    final ExecutionResult result =
        lilo.stitch(
            ExecutionInput.newExecutionInput().query("{ a { id email } b { email id } }").build());

    Assertions.assertEquals(0, result.getErrors().size());
    Assertions.assertEquals(
        Map.of("a", Map.of("id", "1", "email", "a@x"), "b", Map.of("email", "b@x", "id", "2")),
        result.getData());
  }

  @Test
  void differentEnumValueOrderIsAccepted() {

    final RuntimeWiring wiring1 =
        RuntimeWiring.newRuntimeWiring()
            .type(newTypeWiring("Query").dataFetcher("a", env -> "RED"))
            .build();
    final RuntimeWiring wiring2 =
        RuntimeWiring.newRuntimeWiring()
            .type(newTypeWiring("Query").dataFetcher("b", env -> "GREEN"))
            .build();

    final Lilo lilo =
        Lilo.builder()
            .addSource(
                DefinedSchemaSource.create(
                    "project1", "type Query { a: Color } enum Color { RED GREEN }", wiring1))
            .addSource(
                DefinedSchemaSource.create(
                    "project2", "type Query { b: Color } enum Color { GREEN RED }", wiring2))
            .build();

    final ExecutionResult result =
        lilo.stitch(ExecutionInput.newExecutionInput().query("{ a b }").build());

    Assertions.assertEquals(0, result.getErrors().size());
    Assertions.assertEquals(Map.of("a", "RED", "b", "GREEN"), result.getData());
  }

  @Test
  void differentFieldTypeStillConflicts() {

    assertConflict(
        "type Query { a: User } type User { id: ID email: String }",
        "type Query { b: User } type User { email: Int id: ID }",
        "User");
  }

  @Test
  void differentNullabilityStillConflicts() {

    assertConflict(
        "type Query { a: User } type User { id: ID email: String }",
        "type Query { b: User } type User { email: String id: ID! }",
        "User");
  }

  @Test
  void differentArgumentTypeStillConflicts() {

    assertConflict(
        "type Query { a: User } type User { tags(first: Int, after: String): [String] }",
        "type Query { b: User } type User { tags(after: String, first: String): [String] }",
        "User");
  }

  @Test
  void differingEnumValuesFailAtMerge() {

    assertConflict(
        "type Query { a: Color } enum Color { RED GREEN }",
        "type Query { b: Color } enum Color { RED BLUE }",
        "Color");
  }

  private static void assertConflict(final String sdl1, final String sdl2, final String typeName) {

    final Lilo lilo = lilo(sdl1, sdl2);
    final ExecutionInput executionInput =
        ExecutionInput.newExecutionInput().query("{ a { __typename } }").build();

    final Throwable thrown =
        Assertions.assertThrows(RuntimeException.class, () -> lilo.stitch(executionInput));
    Throwable root = thrown;
    while (root.getCause() != null) {
      root = root.getCause();
    }
    Assertions.assertInstanceOf(IllegalArgumentException.class, root);
    Assertions.assertTrue(root.getMessage().contains(typeName));
    Assertions.assertTrue(root.getMessage().contains("project1"));
    Assertions.assertTrue(root.getMessage().contains("project2"));
  }
}
