package io.fria.lilo;

import graphql.GraphQL;
import static io.fria.lilo.JsonUtils.toObj;
import static io.fria.lilo.JsonUtils.toStr;

public final class TestUtils {

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
        return runQuery(graphQL, toObj(query, GraphQLRequest.class));
    }

    public static String runQuery(final GraphQL graphQL, final GraphQLRequest graphQLRequest) {

        return toStr(graphQL.execute(graphQLRequest.toExecutionInput()));
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
