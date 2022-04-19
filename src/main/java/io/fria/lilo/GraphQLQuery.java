package io.fria.lilo;

import graphql.language.Field;
import graphql.language.OperationDefinition;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GraphQLQuery {

  private final String query;
  private final OperationDefinition.Operation operationType;
  private final Field queryNode;
  private final Map<String, Object> variables;

  public GraphQLQuery(
      @NotNull final String query,
      @NotNull final OperationDefinition.Operation operationType,
      @NotNull final Field queryNode,
      @Nullable final Map<String, Object> variables) {
    this.query = Objects.requireNonNull(query);
    this.operationType = Objects.requireNonNull(operationType);
    this.queryNode = Objects.requireNonNull(queryNode);
    this.variables = variables;
  }

  public static @NotNull GraphQLQuery.GraphQLQueryBuilder builder() {
    return new GraphQLQuery.GraphQLQueryBuilder();
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

  public @Nullable Map<String, Object> getVariables() {
    return this.variables;
  }

  public static final class GraphQLQueryBuilder {

    private String query;
    private OperationDefinition.Operation operationType;
    private Field queryNode;
    private Map<String, Object> variables;

    private GraphQLQueryBuilder() {
      // Private constructor
    }

    public @NotNull GraphQLQuery build() {
      return new GraphQLQuery(this.query, this.operationType, this.queryNode, this.variables);
    }

    public @NotNull GraphQLQuery.GraphQLQueryBuilder operationType(
        @NotNull final OperationDefinition.Operation operationTypeParam) {
      this.operationType = operationTypeParam;
      return this;
    }

    public @NotNull GraphQLQuery.GraphQLQueryBuilder query(@NotNull final String queryParam) {
      this.query = queryParam;
      return this;
    }

    public @NotNull GraphQLQuery.GraphQLQueryBuilder queryNode(
        @NotNull final Field queryNodeParam) {
      this.queryNode = queryNodeParam;
      return this;
    }

    public @NotNull GraphQLQuery.GraphQLQueryBuilder variables(
        @Nullable final Map<String, Object> variablesMap) {
      this.variables = variablesMap;
      return this;
    }
  }
}
