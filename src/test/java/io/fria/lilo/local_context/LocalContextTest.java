package io.fria.lilo.local_context;

import graphql.ExecutionInput;
import graphql.schema.idl.RuntimeWiring;
import io.fria.lilo.GraphQLRequest;
import io.fria.lilo.IntrospectionRetriever;
import io.fria.lilo.Lilo;
import io.fria.lilo.QueryRetriever;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static io.fria.lilo.JsonUtils.toStr;
import static io.fria.lilo.TestUtils.createGraphQL;
import static io.fria.lilo.TestUtils.createSchemaSource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalContextTest {

  private static final String SCHEMA1_NAME = "project1";

  private static RuntimeWiring createWiring() {

    return RuntimeWiring.newRuntimeWiring()
        .type(
            newTypeWiring("Query")
                .dataFetcher(
                    "add", env -> env.<Integer>getArgument("a") + env.<Integer>getArgument("b")))
        .build();
  }

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

    final var combinedGraphQL = createGraphQL("/math/add.graphqls", createWiring());
    final var introspection1Retriever = mock(IntrospectionRetriever.class);
    final var query1Retriever = mock(QueryRetriever.class);

    when(introspection1Retriever.get(any(), any(), any(), any()))
        .thenReturn(toStr(combinedGraphQL.execute(executionInputIntrospection)));
    when(query1Retriever.get(any(), any(), any(), argumentCaptor.capture()))
        .thenReturn(toStr(combinedGraphQL.execute(executionInput)));

    final Lilo lilo =
        Lilo.builder()
            .addSource(createSchemaSource(SCHEMA1_NAME, introspection1Retriever, query1Retriever))
            .build();

    lilo.stitch(executionInput);

    Assertions.assertEquals(aLocalContextObject, argumentCaptor.getValue());
  }
}
