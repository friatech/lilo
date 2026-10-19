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

import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.schema.idl.RuntimeWiring;
import java.util.Map;
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
}
