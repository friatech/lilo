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

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

import graphql.ExecutionResult;
import graphql.schema.idl.RuntimeWiring;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SchemaDescriptionTest {

  private static final String QUERY = "{ __schema { description } }";

  private static @NotNull SchemaSource source() {
    return DefinedSchemaSource.create(
        "hello",
        "type Query { hello: String }",
        RuntimeWiring.newRuntimeWiring()
            .type(newTypeWiring("Query").dataFetcher("hello", env -> "world"))
            .build());
  }

  private static @NotNull Object description(final @NotNull ExecutionResult result) {

    Assertions.assertEquals(java.util.List.of(), result.getErrors());

    final Map<String, Object> data = result.getData();
    final Map<?, ?> schema = (Map<?, ?>) data.get("__schema");
    return String.valueOf(schema.get("description"));
  }

  @Test
  void descriptionIsVisibleThroughIntrospection() {

    final Lilo lilo =
        Lilo.builder().addSource(source()).schemaDescription("Gateway for hello").build();

    Assertions.assertEquals("Gateway for hello", description(lilo.stitch(QUERY)));
    Assertions.assertEquals("world", ((Map<?, ?>) lilo.stitch("{ hello }").getData()).get("hello"));
  }

  @Test
  void descriptionIsUnsetByDefault() {

    Assertions.assertEquals(
        "null", description(Lilo.builder().addSource(source()).build().stitch(QUERY)));
  }

  @Test
  void nullAndBlankLeaveTheDescriptionUnset() {

    Assertions.assertEquals(
        "null",
        description(
            Lilo.builder().addSource(source()).schemaDescription(null).build().stitch(QUERY)));
    Assertions.assertEquals(
        "null",
        description(
            Lilo.builder().addSource(source()).schemaDescription("  ").build().stitch(QUERY)));
  }

  @Test
  void descriptionSurvivesInvalidationAndRemerge() {

    final Lilo lilo = Lilo.builder().addSource(source()).schemaDescription("Gateway").build();

    Assertions.assertEquals("Gateway", description(lilo.stitch(QUERY)));

    lilo.getContext().invalidateAll();

    Assertions.assertEquals("Gateway", description(lilo.stitch(QUERY)));
  }
}
