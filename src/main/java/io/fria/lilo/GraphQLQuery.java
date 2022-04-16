package io.fria.lilo;

import graphql.language.Field;
import graphql.language.OperationDefinition;
import java.util.Objects;

public class GraphQLQuery {

  private final String query;
  private final OperationDefinition.Operation operationType;
  private final Field queryNode;

  public GraphQLQuery(
      final String query,
      final OperationDefinition.Operation operationType,
      final Field queryNode) {
    this.query = Objects.requireNonNull(query);
    this.operationType = Objects.requireNonNull(operationType);
    this.queryNode = Objects.requireNonNull(queryNode);
  }

  public static GraphQLQuery.GraphQLQueryBuilder builder() {
    return new GraphQLQuery.GraphQLQueryBuilder();
  }

  public OperationDefinition.Operation getOperationType() {
    return this.operationType;
  }

  public String getQuery() {
    return this.query;
  }

  public Field getQueryNode() {
    return this.queryNode;
  }

  public static final class GraphQLQueryBuilder {

    private String query;
    private OperationDefinition.Operation operationType;
    private Field queryNode;

    private GraphQLQueryBuilder() {
      // Private constructor
    }

    public GraphQLQuery build() {
      return new GraphQLQuery(this.query, this.operationType, this.queryNode);
    }

    public GraphQLQuery.GraphQLQueryBuilder operationType(
        final OperationDefinition.Operation operationTypeParam) {
      this.operationType = operationTypeParam;
      return this;
    }

    public GraphQLQuery.GraphQLQueryBuilder query(final String queryParam) {
      this.query = queryParam;
      return this;
    }

    public GraphQLQuery.GraphQLQueryBuilder queryNode(final Field queryNodeParam) {
      this.queryNode = queryNodeParam;
      return this;
    }

    public String toString() {
      return "GraphQLQuery.GraphQLQueryBuilder(query="
          + this.query
          + ", operationType="
          + this.operationType
          + ", queryNode="
          + this.queryNode
          + ")";
    }
  }
}
