package io.fria.lilo;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.GraphQL;

public final class TestUtils {

    private static final ObjectMapper OBJECT_MAPPER = createMapper();

    private TestUtils() {
    }

    public static SchemaSource createSchemaSource(
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

    public static String runQuery(final GraphQL graphQL, final String query) {

        try {
            return runQuery(graphQL, OBJECT_MAPPER.readValue(query, GraphQLRequest.class));
        } catch (final Exception e) {
            throw new IllegalArgumentException("Serialization Exception");
        }
    }

    public static String runQuery(final GraphQL graphQL, final GraphQLRequest graphQLRequest) {

        try {
            return OBJECT_MAPPER.writeValueAsString(graphQL.execute(graphQLRequest.toExecutionInput()));
        } catch (final Exception e) {
            throw new IllegalArgumentException("Serialization Exception");
        }
    }

    private static ObjectMapper createMapper() {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    public static class TestIntrospectionRetriever implements IntrospectionRetriever {

        private GraphQL graphQL;

        public TestIntrospectionRetriever(final GraphQL graphQL) {
            this.graphQL = graphQL;
        }

        @Override
        public String get(final LiloContext liloContext, final String query) {
            return runQuery(this.graphQL, query);
        }

        public void setGraphQL(final GraphQL graphQL) {
            this.graphQL = graphQL;
        }
    }

    public static class TestQueryRetriever implements QueryRetriever {

        private GraphQL graphQL;

        public TestQueryRetriever(final GraphQL graphQL) {
            this.graphQL = graphQL;
        }

        @Override
        public String get(final LiloContext liloContext, final String query, final Object context) {
            return runQuery(this.graphQL, query);
        }

        public void setGraphQL(final GraphQL graphQL) {
            this.graphQL = graphQL;
        }
    }
}
