package io.fria.lilo;

import graphql.language.Field;
import graphql.language.OperationDefinition;

public class GraphQLQuery {

  private final String query;
  private final OperationDefinition.Operation operationType;
  private final Field queryNode;

  public GraphQLQuery(
      final String query,
      final OperationDefinition.Operation operationType,
      final Field queryNode) {
    this.query = query;
    this.operationType = operationType;
    this.queryNode = queryNode;
  }

  public String getQuery() {
    return this.query;
  }

  public OperationDefinition.Operation getOperationType() {
    return this.operationType;
  }

  public Field getQueryNode() {
    return this.queryNode;
  }
}
