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

import graphql.language.Field;
import graphql.language.OperationDefinition;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class GraphQLQuery {

  private @NotNull final String query;
  private @NotNull final GraphQLRequest request;
  private @NotNull final OperationDefinition.Operation operationType;
  private @NotNull final Field queryNode;
  private @NotNull final Map<String, Object> arguments;
  private @Nullable final Map<String, Object> variables;

  public GraphQLQuery(
      final @NotNull String query,
      final @NotNull OperationDefinition.Operation operationType,
      final @NotNull Field queryNode,
      final @NotNull Map<String, Object> arguments,
      final @Nullable Map<String, Object> variables) {
    this.query = Objects.requireNonNull(query);
    this.request = JsonUtils.toObj(query, GraphQLRequest.class).orElseThrow();
    this.operationType = Objects.requireNonNull(operationType);
    this.queryNode = Objects.requireNonNull(queryNode);
    // Defensive unmodifiable copies; LinkedHashMap keeps the iteration order and, unlike
    // Map.copyOf, tolerates null values (explicit null arguments / variables are valid GraphQL)
    this.arguments =
        Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(arguments)));
    this.variables =
        variables == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(variables));
  }

  public static @NotNull GraphQLQuery.GraphQLQueryBuilder builder() {
    return new GraphQLQuery.GraphQLQueryBuilder();
  }

  public @NotNull Map<String, Object> getArguments() {
    return this.arguments;
  }

  public @NotNull OperationDefinition.Operation getOperationType() {
    return this.operationType;
  }

  public @NotNull String getQuery() {
    return this.query;
  }

  public @NotNull Field getQueryNode() {
    return this.queryNode;
  }

  public @NotNull GraphQLRequest getRequest() {
    return GraphQLRequest.copyOf(this.request);
  }

  public @Nullable Map<String, Object> getVariables() {
    return this.variables;
  }

  public static final class GraphQLQueryBuilder {

    private @Nullable String query;
    private @Nullable OperationDefinition.Operation operationType;
    private @Nullable Field queryNode;
    private @Nullable Map<String, Object> arguments;
    private @Nullable Map<String, Object> variables;

    private GraphQLQueryBuilder() {
      // Private constructor
    }

    public @NotNull GraphQLQueryBuilder arguments(
        final @NotNull Map<String, Object> argumentsParam) {
      this.arguments =
          argumentsParam == null
              ? null
              : Collections.unmodifiableMap(new LinkedHashMap<>(argumentsParam));
      return this;
    }

    public @NotNull GraphQLQuery build() {
      return new GraphQLQuery(
          Objects.requireNonNull(this.query, "query must be set"),
          Objects.requireNonNull(this.operationType, "operationType must be set"),
          Objects.requireNonNull(this.queryNode, "queryNode must be set"),
          Objects.requireNonNull(this.arguments, "arguments must be set"),
          this.variables);
    }

    public @NotNull GraphQLQuery.GraphQLQueryBuilder operationType(
        final @NotNull OperationDefinition.Operation operationTypeParam) {
      this.operationType = operationTypeParam;
      return this;
    }

    public @NotNull GraphQLQuery.GraphQLQueryBuilder query(final @NotNull String queryParam) {
      this.query = queryParam;
      return this;
    }

    public @NotNull GraphQLQuery.GraphQLQueryBuilder queryNode(
        final @NotNull Field queryNodeParam) {
      this.queryNode = queryNodeParam;
      return this;
    }

    public @NotNull GraphQLQuery.GraphQLQueryBuilder variables(
        final @Nullable Map<String, Object> variablesMap) {
      this.variables =
          variablesMap == null
              ? null
              : Collections.unmodifiableMap(new LinkedHashMap<>(variablesMap));
      return this;
    }
  }
}
