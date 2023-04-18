/*
 * Copyright 2023 the original author or authors.
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
package io.fria.lilo;

import graphql.ExecutionInput;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
      final @NotNull String query,
      final @Nullable String operationName,
      final @Nullable Map<String, Object> variables) {
    this.query = Objects.requireNonNull(query);
    this.operationName = operationName;
    this.variables = variables;
  }

  public GraphQLRequest() {
    // Default constructor
  }

  public static @NotNull GraphQLRequestBuilder builder() {
    return new GraphQLRequestBuilder();
  }

  @Override
  public boolean equals(final @Nullable Object o) {

    if (this == o) {
      return true;
    }

    if (!(o instanceof GraphQLRequest)) {
      return false;
    }

    final GraphQLRequest that = (GraphQLRequest) o;

    return this.query.equals(that.query)
        && Objects.equals(this.operationName, that.operationName)
        && Objects.equals(this.variables, that.variables);
  }

  public @Nullable String getOperationName() {
    return this.operationName;
  }

  public void setOperationName(final @Nullable String operationName) {
    this.operationName = operationName;
  }

  public @NotNull String getQuery() {
    return this.query;
  }

  public void setQuery(final @NotNull String query) {
    this.query = Objects.requireNonNull(query);
  }

  public @Nullable Map<String, Object> getVariables() {
    return this.variables;
  }

  public void setVariables(final @Nullable Map<String, Object> variables) {
    this.variables = variables;
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.query, this.operationName, this.variables);
  }

  public @NotNull ExecutionInput toExecutionInput(final @Nullable Object localContext) {

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

  public @NotNull ExecutionInput toExecutionInput() {
    return this.toExecutionInput(null);
  }

  @Override
  public @NotNull String toString() {
    return "GraphQLRequest(query="
        + this.getQuery()
        + ", operationName="
        + this.getOperationName()
        + ", variables="
        + this.getVariables()
        + ")";
  }

  public static final class GraphQLRequestBuilder {

    private String query;
    private String operationName;
    private Map<String, Object> variables;

    private GraphQLRequestBuilder() {
      // Private constructor
    }

    public @NotNull GraphQLRequest build() {
      return new GraphQLRequest(this.query, this.operationName, this.variables);
    }

    public @NotNull GraphQLRequestBuilder operationName(final @Nullable String operationNameParam) {
      this.operationName = operationNameParam;
      return this;
    }

    public @NotNull GraphQLRequestBuilder query(final @NotNull String queryParam) {
      this.query = Objects.requireNonNull(queryParam);
      return this;
    }

    public @NotNull GraphQLRequestBuilder variables(
        final @Nullable Map<String, Object> variablesParam) {
      this.variables = variablesParam;
      return this;
    }
  }
}
