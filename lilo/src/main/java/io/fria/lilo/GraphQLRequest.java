/*
 * Copyright 2022-2024 the original author or authors.
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

import com.fasterxml.jackson.annotation.JsonInclude;
import graphql.ExecutionInput;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class GraphQLRequest {

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
  private Map<String, Object> extensions;

  public GraphQLRequest(
      final @NotNull String query,
      final @Nullable String operationName,
      final @Nullable Map<String, Object> variables) {
    this(query, operationName, variables, null);
  }

  /**
   * Creates a request with protocol extensions, such as an Automatic Persisted Queries hash. The
   * variables and extensions are copied into unmodifiable maps that keep their iteration order and
   * may hold null values. Absent extensions are left out of the request's JSON, so a request
   * forwarded to a remote source keeps its previous shape.
   *
   * @param query GraphQL query text
   * @param operationName name of the operation to run, or null for the only one
   * @param variables variable values, or null for none
   * @param extensions request extensions, or null for none; they reach the {@link ExecutionInput}
   *     only when not empty
   */
  public GraphQLRequest(
      final @NotNull String query,
      final @Nullable String operationName,
      final @Nullable Map<String, Object> variables,
      final @Nullable Map<String, Object> extensions) {
    this.query = Objects.requireNonNull(query);
    this.operationName = operationName;
    this.variables = variables == null ? null : Maps.nullTolerantCopy(variables);
    this.extensions = extensions == null ? null : Maps.nullTolerantCopy(extensions);
  }

  public GraphQLRequest() {
    // Default constructor
  }

  /**
   * Creates a copy of the given request. Unlike the public constructor it does not validate the
   * query, so a deserialized request without a query can still be copied.
   */
  static @NotNull GraphQLRequest copyOf(final @NotNull GraphQLRequest source) {

    final GraphQLRequest copy = new GraphQLRequest();
    copy.query = source.query;
    copy.setOperationName(source.operationName);
    copy.setVariables(source.variables);
    copy.setExtensions(source.extensions);

    return copy;
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

    return Objects.equals(this.query, that.query)
        && Objects.equals(this.operationName, that.operationName)
        && Objects.equals(this.variables, that.variables)
        && Objects.equals(this.extensions, that.extensions);
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
    // The stored map is already an unmodifiable copy; the extra read-only view only makes that
    // explicit for static analysis and costs no copying
    return this.variables == null ? null : Collections.unmodifiableMap(this.variables);
  }

  public void setVariables(final @Nullable Map<String, Object> variables) {
    // Defensive unmodifiable copy; LinkedHashMap keeps the iteration order and, unlike
    // Map.copyOf, tolerates null values
    this.variables = variables == null ? null : Maps.nullTolerantCopy(variables);
  }

  // Absent extensions are left out of the JSON so requests forwarded to remote sources keep their
  // exact previous shape
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public @Nullable Map<String, Object> getExtensions() {
    return this.extensions == null ? null : Collections.unmodifiableMap(this.extensions);
  }

  public void setExtensions(final @Nullable Map<String, Object> extensions) {
    this.extensions = extensions == null ? null : Maps.nullTolerantCopy(extensions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.query, this.operationName, this.variables, this.extensions);
  }

  public @NotNull ExecutionInput toExecutionInput(final @Nullable Object localContext) {

    if (this.query == null || this.query.trim().isEmpty()) {
      throw new IllegalArgumentException("GraphQL request must contain a query");
    }

    final ExecutionInput.Builder builder = ExecutionInput.newExecutionInput().query(this.query);

    if (this.operationName != null) {
      builder.operationName(this.operationName);
    }

    if (this.variables != null && !this.variables.isEmpty()) {
      builder.variables(this.variables);
    }

    if (this.extensions != null && !this.extensions.isEmpty()) {
      builder.extensions(this.extensions);
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
        + ", extensions="
        + this.getExtensions()
        + ")";
  }

  public static final class GraphQLRequestBuilder {

    private String query;
    private String operationName;
    private Map<String, Object> variables;
    private Map<String, Object> extensions;

    private GraphQLRequestBuilder() {
      // Private constructor
    }

    public @NotNull GraphQLRequest build() {
      return new GraphQLRequest(this.query, this.operationName, this.variables, this.extensions);
    }

    public @NotNull GraphQLRequestBuilder extensions(
        final @Nullable Map<String, Object> extensionsParam) {
      this.extensions = extensionsParam == null ? null : Maps.nullTolerantCopy(extensionsParam);
      return this;
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
      this.variables = variablesParam == null ? null : Maps.nullTolerantCopy(variablesParam);
      return this;
    }
  }
}
