package io.firat.lilo;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

class GraphQLTest {

    @Test
    void testGraphQL() throws IOException {

        final InputStream resourceAsStream = this.getClass().getResourceAsStream("/greetings.graphqls");
        Assertions.assertNotNull(resourceAsStream);

        final String        schemaDefinitionText = new String(resourceAsStream.readAllBytes());
        final GraphQLSchema graphQLSchema        = this.buildSchema(schemaDefinitionText);
        final GraphQL       graphQL              = GraphQL.newGraphQL(graphQLSchema).build();

        final ExecutionInput executionInput = ExecutionInput.newExecutionInput()
            .query("{greeting1\ngreeting2}")
            .build();

        final ExecutionResult result = graphQL.execute(executionInput);
        final Map<String, String> expected = Map.of("greeting1", "Hello greeting1", "greeting2", "Hello greeting2");
        Assertions.assertEquals(expected, result.getData());
    }

    private GraphQLSchema buildSchema(final String schemaDefinitionText) {

        final TypeDefinitionRegistry typeRegistry    = new SchemaParser().parse(schemaDefinitionText);
        final RuntimeWiring          runtimeWiring   = this.buildWiring();
        final SchemaGenerator        schemaGenerator = new SchemaGenerator();
        return schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);
    }

    private RuntimeWiring buildWiring() {
        return RuntimeWiring.newRuntimeWiring()
            .type(
                newTypeWiring("Query")
                    .dataFetcher("greeting1", env -> "Hello greeting1")
                    .dataFetcher("greeting2", env -> "Hello greeting2")
            )
            .build();
    }
}
