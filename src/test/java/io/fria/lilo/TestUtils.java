package io.fria.lilo;

import graphql.GraphQL;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import java.io.IOException;
import java.io.InputStream;
import static io.fria.lilo.JsonUtils.toObj;
import static io.fria.lilo.JsonUtils.toStr;

public final class TestUtils {

  private TestUtils() {}

  public static GraphQL createGraphQL(
      final String schemaDefinitionPath, final RuntimeWiring runtimeWiring) throws IOException {

    final var schemaDefinitionText = loadResource(schemaDefinitionPath);
    final var typeRegistry = new SchemaParser().parse(schemaDefinitionText);
    final var schemaGenerator = new SchemaGenerator();
    final var graphQLSchema = schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);

    return GraphQL.newGraphQL(graphQLSchema).build();
  }

  public static SchemaSource createSchemaSource(
      final String schemaName,
      final IntrospectionRetriever introspectionRetriever,
      final QueryRetriever queryRetriever) {

    return SchemaSource.builder()
        .name(schemaName)
        .introspectionRetriever(introspectionRetriever)
        .queryRetriever(queryRetriever)
        .build();
  }

  public static String loadResource(final String path) {

    try {
      final InputStream stream = TestUtils.class.getResourceAsStream(path);

      if (stream != null) {
        return new String(stream.readAllBytes());
      }
    } catch (final IOException e) {
      // pass to the exception
    }

    throw new IllegalArgumentException(String.format("Resource %s not found", path));
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
    public String get(
        final LiloContext liloContext,
        final SchemaSource schemaSource,
        final String query,
        final Object context) {
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
    public String get(
        final LiloContext liloContext,
        final SchemaSource schemaSource,
        final GraphQLQuery query,
        final Object context) {
      return runQuery(this.graphQL, query.getQuery());
    }

    public void setGraphQL(final GraphQL graphQL) {
      this.graphQL = graphQL;
    }
  }
}
