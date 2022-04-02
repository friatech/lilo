package io.firat.lilo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionInput;
import graphql.GraphQL;
import graphql.introspection.IntrospectionQuery;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

@ExtendWith(MockitoExtension.class)
class LiloTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String       SCHEMA1_NAME  = "project1";
    private static final String       SCHEMA1_URL   = "http://localhost:8081/graphql";
    private static final String       SCHEMA2_NAME  = "project2";
    private static final String       SCHEMA2_URL   = "http://localhost:8082/graphql";

    @Mock
    private IntrospectionRetriever introspectionRetriever;

    @Mock
    private QueryRetriever queryRetriever;

    private static GraphQL createGraphQL(final String schemaDefinitionPath, final RuntimeWiring runtimeWiring) throws IOException {

        final InputStream resourceAsStream = LiloTest.class.getResourceAsStream(schemaDefinitionPath);
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

    private static String runQuery(final GraphQL graphQL, final String query) throws JsonProcessingException {

        final ExecutionInput executionInput = ExecutionInput.newExecutionInput()
            .query(query)
            .build();

        return OBJECT_MAPPER.writeValueAsString(graphQL.execute(executionInput));
    }

    @Test
    void basicStitchingTest() throws IOException {

        final GraphQL project1GraphQL = createGraphQL("/greeting1.graphqls", createProject1Wiring());
        final GraphQL project2GraphQL = createGraphQL("/greeting2.graphqls", createProject2Wiring());

        Mockito.when(this.introspectionRetriever.get(Mockito.eq(SCHEMA1_URL)))
            .thenReturn(runQuery(project1GraphQL, IntrospectionQuery.INTROSPECTION_QUERY));

        Mockito.when(this.introspectionRetriever.get(Mockito.eq(SCHEMA2_URL)))
            .thenReturn(runQuery(project2GraphQL, IntrospectionQuery.INTROSPECTION_QUERY));

        Mockito.when(this.queryRetriever.get(Mockito.eq(SCHEMA1_URL), Mockito.any()))
            .thenReturn(runQuery(project1GraphQL, "{greeting1}"));

        Mockito.when(this.queryRetriever.get(Mockito.eq(SCHEMA2_URL), Mockito.any()))
            .thenReturn(runQuery(project2GraphQL, "{greeting2}"));

        final Lilo lilo = Lilo.builder()
            .addSource(this.createSchemaSource(SCHEMA1_NAME, SCHEMA1_URL))
            .addSource(this.createSchemaSource(SCHEMA2_NAME, SCHEMA2_URL))
            .build();

        final ExecutionInput executionInput = ExecutionInput.newExecutionInput()
            .query("{greeting1\ngreeting2}")
            .build();

        final HashMap<Object, Object> result = lilo.stitch(executionInput);
        System.out.println(result);
    }

    private SchemaSource createSchemaSource(final String schemaName, final String schemaUrl) {

        return SchemaSource.builder()
            .name(schemaName)
            .url(schemaUrl)
            .introspectionRetriever(this.introspectionRetriever)
            .queryRetriever(this.queryRetriever)
            .build();
    }
}