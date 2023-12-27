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
package io.fria.lilo.local_context;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static io.fria.lilo.JsonUtils.toStr;
import static io.fria.lilo.TestUtils.createGraphQL;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import graphql.ExecutionInput;
import graphql.schema.idl.RuntimeWiring;
import io.fria.lilo.GraphQLRequest;
import io.fria.lilo.Lilo;
import io.fria.lilo.RemoteSchemaSource;
import io.fria.lilo.SyncIntrospectionRetriever;
import io.fria.lilo.SyncQueryRetriever;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LocalContextTest {

  private static final String SCHEMA1_NAME = "project1";

  private static final RuntimeWiring WIRING =
      RuntimeWiring.newRuntimeWiring()
          .type(
              newTypeWiring("Query")
                  .dataFetcher(
                      "add", env -> env.<Integer>getArgument("a") + env.<Integer>getArgument("b")))
          .build();

  @Test
  void localContextTest() {

    final ArgumentCaptor<Object> argumentCaptor = ArgumentCaptor.forClass(Object.class);
    final String aLocalContextObject = "aLocalContextObject";

    final ExecutionInput executionInput =
        ExecutionInput.newExecutionInput()
            .localContext(aLocalContextObject)
            .query("{add(a: 1, b: 2)}")
            .build();

    final ExecutionInput executionInputIntrospection =
        ExecutionInput.newExecutionInput().query(GraphQLRequest.INTROSPECTION_QUERY).build();

    final var combinedGraphQL = createGraphQL("/math/add.graphqls", WIRING);
    final var introspection1Retriever = mock(SyncIntrospectionRetriever.class);
    final var query1Retriever = mock(SyncQueryRetriever.class);

    when(introspection1Retriever.get(any(), any(), any(), any()))
        .thenReturn(toStr(combinedGraphQL.execute(executionInputIntrospection)));
    when(query1Retriever.get(any(), any(), any(), argumentCaptor.capture()))
        .thenReturn(toStr(combinedGraphQL.execute(executionInput)));

    final Lilo lilo =
        Lilo.builder()
            .addSource(
                RemoteSchemaSource.create(SCHEMA1_NAME, introspection1Retriever, query1Retriever))
            .build();

    lilo.stitch(executionInput);

    Assertions.assertEquals(aLocalContextObject, argumentCaptor.getValue());
  }
}
