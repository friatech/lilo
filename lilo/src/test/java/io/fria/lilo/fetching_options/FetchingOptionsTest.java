package io.fria.lilo.fetching_options;

import graphql.ExecutionInput;
import graphql.GraphQL;
import graphql.schema.idl.RuntimeWiring;
import io.fria.lilo.GraphQLRequest;
import io.fria.lilo.IntrospectionFetchingMode;
import io.fria.lilo.IntrospectionRetriever;
import io.fria.lilo.Lilo;
import io.fria.lilo.QueryRetriever;
import io.fria.lilo.RemoteSchemaSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static io.fria.lilo.JsonUtils.toStr;
import static io.fria.lilo.TestUtils.createGraphQL;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FetchingOptionsTest {

  private static final String SCHEMA1_NAME = "project1";
  private static final RuntimeWiring WIRING =
      RuntimeWiring.newRuntimeWiring()
          .type(
              newTypeWiring("Query")
                  .dataFetcher(
                      "add", env -> env.<Integer>getArgument("a") + env.<Integer>getArgument("b")))
          .build();
  private static ExecutionInput EXECUTION_INPUT_QUERY;
  private static String RESPONSE_INTROSPECTION;
  private static String RESPONSE_QUERY;

  @BeforeAll
  static void init() {

    EXECUTION_INPUT_QUERY = ExecutionInput.newExecutionInput().query("{add(a: 1, b: 2)}").build();

    final ExecutionInput executionInputIntrospection =
        ExecutionInput.newExecutionInput().query(GraphQLRequest.INTROSPECTION_QUERY).build();

    final GraphQL combinedGraphQL = createGraphQL("/math/add.graphqls", WIRING);
    RESPONSE_INTROSPECTION = toStr(combinedGraphQL.execute(executionInputIntrospection));
    RESPONSE_QUERY = toStr(combinedGraphQL.execute(EXECUTION_INPUT_QUERY));
  }

  @Test
  void cacheUntilInvalidationTest() {

    final var introspection1Retriever = mock(IntrospectionRetriever.class);
    final var query1Retriever = mock(QueryRetriever.class);

    when(introspection1Retriever.get(any(), any(), any(), any()))
        .thenReturn(RESPONSE_INTROSPECTION);
    when(query1Retriever.get(any(), any(), any(), any())).thenReturn(RESPONSE_QUERY);

    final Lilo lilo =
        Lilo.builder()
            .addSource(
                RemoteSchemaSource.create(SCHEMA1_NAME, introspection1Retriever, query1Retriever))
            .build();

    lilo.stitch(EXECUTION_INPUT_QUERY);
    lilo.stitch(EXECUTION_INPUT_QUERY);

    verify(introspection1Retriever, times(1)).get(any(), any(), any(), any());
    verify(query1Retriever, times(2)).get(any(), any(), any(), any());
  }

  @Test
  void fetchBeforeEveryRequestTest() {

    final var introspection1Retriever = mock(IntrospectionRetriever.class);
    final var query1Retriever = mock(QueryRetriever.class);

    when(introspection1Retriever.get(any(), any(), any(), any()))
        .thenReturn(RESPONSE_INTROSPECTION);
    when(query1Retriever.get(any(), any(), any(), any())).thenReturn(RESPONSE_QUERY);

    final Lilo lilo =
        Lilo.builder()
            .introspectionFetchingMode(IntrospectionFetchingMode.FETCH_BEFORE_EVERY_REQUEST)
            .addSource(
                RemoteSchemaSource.create(SCHEMA1_NAME, introspection1Retriever, query1Retriever))
            .build();

    lilo.stitch(EXECUTION_INPUT_QUERY);
    lilo.stitch(EXECUTION_INPUT_QUERY);

    verify(introspection1Retriever, times(2)).get(any(), any(), any(), any());
    verify(query1Retriever, times(2)).get(any(), any(), any(), any());
  }
}
