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
package io.fria.lilo.duplicate_fields;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static io.fria.lilo.TestUtils.createGraphQL;

import graphql.ExecutionInput;
import graphql.GraphQL;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.errors.StrictModeWiringException;
import io.fria.lilo.Lilo;
import io.fria.lilo.RemoteSchemaSource;
import io.fria.lilo.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DuplicateRootFieldTest {

  private static final RuntimeWiring WIRING =
      RuntimeWiring.newRuntimeWiring()
          .type(newTypeWiring("Query").dataFetcher("greeting1", env -> "Hello greeting1"))
          .build();

  @Test
  void sameRootFieldInTwoSourcesFailsAtMerge() {

    // Both sources declare Query.greeting1; stitching them has no single owner for the field.
    final GraphQL project1GraphQL = createGraphQL("/greetings/greeting1.graphqls", WIRING);
    final GraphQL project2GraphQL = createGraphQL("/greetings/greeting1.graphqls", WIRING);

    final Lilo lilo =
        Lilo.builder()
            .addSource(
                RemoteSchemaSource.create(
                    "project1",
                    new TestUtils.TestIntrospectionRetriever(project1GraphQL),
                    new TestUtils.TestQueryRetriever(project1GraphQL)))
            .addSource(
                RemoteSchemaSource.create(
                    "project2",
                    new TestUtils.TestIntrospectionRetriever(project2GraphQL),
                    new TestUtils.TestQueryRetriever(project2GraphQL)))
            .build();

    final ExecutionInput executionInput =
        ExecutionInput.newExecutionInput().query("{greeting1}").build();

    final Throwable thrown =
        Assertions.assertThrows(RuntimeException.class, () -> lilo.stitch(executionInput));
    Throwable root = thrown;
    while (root.getCause() != null) {
      root = root.getCause();
    }
    Assertions.assertInstanceOf(StrictModeWiringException.class, root);
    Assertions.assertTrue(root.getMessage().contains("greeting1"));
  }
}
