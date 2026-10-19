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
import graphql.schema.idl.RuntimeWiring;
import io.fria.lilo.DefinedSchemaSource;
import io.fria.lilo.Lilo;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ConflictingTypesTest {

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
}
