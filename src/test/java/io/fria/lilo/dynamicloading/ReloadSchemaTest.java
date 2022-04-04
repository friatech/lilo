package io.fria.lilo.dynamicloading;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.introspection.IntrospectionQuery;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import io.fria.lilo.IntrospectionRetriever;
import io.fria.lilo.Lilo;
import io.fria.lilo.QueryRetriever;
import io.fria.lilo.SchemaSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

@ExtendWith(MockitoExtension.class)
class ReloadSchemaTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String       SCHEMA1_NAME  = "project1";
    private static final String       SCHEMA2_NAME  = "project2";

    @Mock
    private IntrospectionRetriever introspection1Retriever;

    @Mock
    private IntrospectionRetriever introspection2Retriever;

    @Mock
    private QueryRetriever query1Retriever;

    @Mock
    private QueryRetriever query2Retriever;

    private static RuntimeWiring createCombinedWiring() {

        return RuntimeWiring.newRuntimeWiring()
            .type(
                newTypeWiring("Query")
                    .dataFetcher("greeting1", env -> "Hello greeting1")
                    .dataFetcher("greeting2", env -> "Hello greeting2")
            )
            .build();
    }

    private static GraphQL createGraphQL(final String schemaDefinitionPath, final RuntimeWiring runtimeWiring) throws IOException {

        final InputStream resourceAsStream = ReloadSchemaTest.class.getResourceAsStream(schemaDefinitionPath);
        Assertions.assertNotNull(resourceAsStream);

        final var schemaDefinitionText = new String(resourceAsStream.readAllBytes());
        final var typeRegistry         = new SchemaParser().parse(schemaDefinitionText);
        final var schemaGenerator      = new SchemaGenerator();
        final var graphQLSchema        = schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);

        return GraphQL.newGraphQL(graphQLSchema).build();
    }

    private static RuntimeWiring createProject1Wiring() {

        return RuntimeWiring.newRuntimeWiring()
            .type(
                newTypeWiring("Query")
                    .dataFetcher("greeting1", env -> "Hello greeting1")
            )
            .build();
    }

    private static RuntimeWiring createProject2Wiring() {

        return RuntimeWiring.newRuntimeWiring()
            .type(
                newTypeWiring("Query")
                    .dataFetcher("greeting2", env -> "Hello greeting2")
            )
            .build();
    }

    private static RuntimeWiring createProject3Wiring() {

        return RuntimeWiring.newRuntimeWiring()
            .type(
                newTypeWiring("Query")
                    .dataFetcher("greeting3", env -> "Hello greeting3")
            )
            .build();
    }

    private static SchemaSource createSchemaSource(
        final String schemaName,
        final IntrospectionRetriever introspectionRetriever,
        final QueryRetriever queryRetriever
    ) {

        return SchemaSource.builder()
            .name(schemaName)
            .introspectionRetriever(introspectionRetriever)
            .queryRetriever(queryRetriever)
            .build();
    }

    private static String runQuery(final GraphQL graphQL, final String query) throws JsonProcessingException {

        final ExecutionInput executionInput = ExecutionInput.newExecutionInput()
            .query(query)
            .build();

        return OBJECT_MAPPER.writeValueAsString(graphQL.execute(executionInput));
    }

    @Test
    void stitchingTest() throws IOException {

        Map<String, Object> expected = Map.of("greeting1", "Hello greeting1", "greeting2", "Hello greeting2");

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
            .query("{greeting1\ngreeting2}")
            .build();

        // Stitching result ----------------------------------------------------
        final GraphQL project1GraphQL = createGraphQL("/greetings/greeting1.graphqls", createProject1Wiring());
        final GraphQL project2GraphQL = createGraphQL("/greetings/greeting2.graphqls", createProject2Wiring());
        final GraphQL project3GraphQL = createGraphQL("/dynamicloading/greeting3.graphqls", createProject3Wiring());

        Mockito.when(this.introspection1Retriever.get(Mockito.any(), Mockito.any()))
            .thenReturn(runQuery(project1GraphQL, IntrospectionQuery.INTROSPECTION_QUERY));

        Mockito.when(this.introspection2Retriever.get(Mockito.any(), Mockito.any()))
            .thenReturn(runQuery(project2GraphQL, IntrospectionQuery.INTROSPECTION_QUERY));

        Mockito.when(this.query1Retriever.get(Mockito.any(), Mockito.any(), Mockito.any()))
            .thenReturn(runQuery(project1GraphQL, "{greeting1}"));

        Mockito.when(this.query2Retriever.get(Mockito.any(), Mockito.any(), Mockito.any()))
            .thenReturn(runQuery(project2GraphQL, "{greeting2}"));

        final Lilo lilo = Lilo.builder()
            .addSource(createSchemaSource(SCHEMA1_NAME, this.introspection1Retriever, this.query1Retriever))
            .addSource(createSchemaSource(SCHEMA2_NAME, this.introspection2Retriever, this.query2Retriever))
            .build();

        ExecutionResult stitchResult = lilo.stitch(executionInput);
        Assertions.assertEquals(expected, stitchResult.getData());

        // After reloading context old expected result won't work.
        Mockito.when(this.introspection2Retriever.get(Mockito.any(), Mockito.any()))
            .thenReturn(runQuery(project3GraphQL, IntrospectionQuery.INTROSPECTION_QUERY));

        lilo.getContext().reload(SCHEMA2_NAME);

        stitchResult = lilo.stitch(executionInput);
        Assertions.assertNotEquals(expected, stitchResult.getData());

        // But new query should work
        Mockito.when(this.query2Retriever.get(Mockito.any(), Mockito.any(), Mockito.any()))
            .thenReturn(runQuery(project3GraphQL, "{greeting3}"));

        executionInput = ExecutionInput.newExecutionInput()
            .query("{greeting1\ngreeting3}")
            .build();

        expected = Map.of("greeting1", "Hello greeting1", "greeting3", "Hello greeting3");

        stitchResult = lilo.stitch(executionInput);
        Assertions.assertEquals(expected, stitchResult.getData());
    }
}