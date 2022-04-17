package io.fria.lilo;

import graphql.ExecutionInput;
import java.util.Map;
import java.util.Objects;

public class GraphQLRequest {

  public static final String INTROSPECTION_QUERY =
      "\n"
          + "  query IntrospectionQuery {\n"
          + "    __schema {\n"
          + "      queryType { name }\n"
          + "      mutationType { name }\n"
          + "      subscriptionType { name }\n"
          + "      types {\n"
          + "        ...FullType\n"
          + "      }\n"
          + "      directives {\n"
          + "        name\n"
          + "        description\n"
          + "        locations\n"
          + "        args {\n"
          + "          ...InputValue\n"
          + "        }\n"
          + "      }\n"
          + "    }\n"
          + "  }\n"
          + "\n"
          + "  fragment FullType on __Type {\n"
          + "    kind\n"
          + "    name\n"
          + "    description\n"
          + "    fields(includeDeprecated: true) {\n"
          + "      name\n"
          + "      description\n"
          + "      args {\n"
          + "        ...InputValue\n"
          + "      }\n"
          + "      type {\n"
          + "        ...TypeRef\n"
          + "      }\n"
          + "      isDeprecated\n"
          + "      deprecationReason\n"
          + "    }\n"
          + "    inputFields {\n"
          + "      ...InputValue\n"
          + "    }\n"
          + "    interfaces {\n"
          + "      ...TypeRef\n"
          + "    }\n"
          + "    enumValues(includeDeprecated: true) {\n"
          + "      name\n"
          + "      description\n"
          + "      isDeprecated\n"
          + "      deprecationReason\n"
          + "    }\n"
          + "    possibleTypes {\n"
          + "      ...TypeRef\n"
          + "    }\n"
          + "  }\n"
          + "\n"
          + "  fragment InputValue on __InputValue {\n"
          + "    name\n"
          + "    description\n"
          + "    type { ...TypeRef }\n"
          + "    defaultValue\n"
          + "  }\n"
          + "\n"
          +
          //
          // The depth of the types is actually an arbitrary decision.  It could be any depth in
          // fact.  This depth
          // was taken from GraphIQL
          // https://github.com/graphql/graphiql/blob/master/src/utility/introspectionQueries.js
          // which uses 7 levels and hence could represent a type like say [[[[[Float!]]]]]
          //
          "fragment TypeRef on __Type {\n"
          + "    kind\n"
          + "    name\n"
          + "    ofType {\n"
          + "      kind\n"
          + "      name\n"
          + "      ofType {\n"
          + "        kind\n"
          + "        name\n"
          + "        ofType {\n"
          + "          kind\n"
          + "          name\n"
          + "          ofType {\n"
          + "            kind\n"
          + "            name\n"
          + "            ofType {\n"
          + "              kind\n"
          + "              name\n"
          + "              ofType {\n"
          + "                kind\n"
          + "                name\n"
          + "                ofType {\n"
          + "                  kind\n"
          + "                  name\n"
          + "                }\n"
          + "              }\n"
          + "            }\n"
          + "          }\n"
          + "        }\n"
          + "      }\n"
          + "    }\n"
          + "  }\n"
          + "\n";

  private String query;
  private String operationName;
  private Map<String, Object> variables;

  public GraphQLRequest(
      final String query, final String operationName, final Map<String, Object> variables) {
    this.query = query;
    this.operationName = operationName;
    this.variables = variables;
  }

  public GraphQLRequest() {
    // Default constructor
  }

  public static GraphQLRequestBuilder builder() {
    return new GraphQLRequestBuilder();
  }

  public boolean equals(final Object o) {

    if (o == this) {
      return true;
    }

    if (!(o instanceof GraphQLRequest)) {
      return false;
    }

    final GraphQLRequest other = (GraphQLRequest) o;

    if (!other.canEqual(this)) {
      return false;
    }

    final Object thisQuery = this.getQuery();
    final Object otherQuery = other.getQuery();

    if (!Objects.equals(thisQuery, otherQuery)) {
      return false;
    }

    final Object thisOperationName = this.getOperationName();
    final Object otherOperationName = other.getOperationName();

    if (!Objects.equals(thisOperationName, otherOperationName)) {
      return false;
    }

    final Object thisVariables = this.getVariables();
    final Object otherVariables = other.getVariables();

    return Objects.equals(thisVariables, otherVariables);
  }

  public String getOperationName() {
    return this.operationName;
  }

  public void setOperationName(final String operationName) {
    this.operationName = operationName;
  }

  public String getQuery() {
    return this.query;
  }

  public void setQuery(final String query) {
    this.query = query;
  }

  public Map<String, Object> getVariables() {
    return this.variables;
  }

  public void setVariables(final Map<String, Object> variables) {
    this.variables = variables;
  }

  public int hashCode() {
    return Objects.hash(this.query, this.operationName, this.variables);
  }

  public ExecutionInput toExecutionInput(final Object localContext) {

    final ExecutionInput.Builder builder = ExecutionInput.newExecutionInput().query(this.query);

    if (this.operationName != null) {
      builder.operationName(this.operationName);
    }

    if (this.variables != null && !this.variables.isEmpty()) {
      builder.variables(this.variables);
    }

    if (localContext != null) {
      builder.localContext(localContext);
    }

    return builder.build();
  }

  public ExecutionInput toExecutionInput() {

    return this.toExecutionInput(null);
  }

  public String toString() {
    return "GraphQLRequest(query="
        + this.getQuery()
        + ", operationName="
        + this.getOperationName()
        + ", variables="
        + this.getVariables()
        + ")";
  }

  protected boolean canEqual(final Object other) {
    return other instanceof GraphQLRequest;
  }

  public static final class GraphQLRequestBuilder {

    private String query;
    private String operationName;
    private Map<String, Object> variables;

    private GraphQLRequestBuilder() {
      // Private constructor
    }

    public GraphQLRequest build() {
      return new GraphQLRequest(this.query, this.operationName, this.variables);
    }

    public GraphQLRequestBuilder operationName(final String operationNameParam) {
      this.operationName = operationNameParam;
      return this;
    }

    public GraphQLRequestBuilder query(final String queryParam) {
      this.query = queryParam;
      return this;
    }

    public String toString() {
      return "GraphQLRequest.GraphQLRequestBuilder(query="
          + this.query
          + ", operationName="
          + this.operationName
          + ", variables="
          + this.variables
          + ")";
    }

    public GraphQLRequestBuilder variables(final Map<String, Object> variablesParam) {
      this.variables = variablesParam;
      return this;
    }
  }
}
