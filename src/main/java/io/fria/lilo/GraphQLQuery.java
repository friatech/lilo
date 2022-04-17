package io.fria.lilo;

import graphql.language.Field;
import graphql.language.OperationDefinition;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public class GraphQLQuery {

  private final String query;
  private final OperationDefinition.Operation operationType;
  private final Field queryNode;

  public GraphQLQuery(
      @NotNull final String query,
      @NotNull final OperationDefinition.Operation operationType,
      @NotNull final Field queryNode) {
    this.query = Objects.requireNonNull(query);
    this.operationType = Objects.requireNonNull(operationType);
    this.queryNode = Objects.requireNonNull(queryNode);
  }

  @NotNull
  public static GraphQLQuery.GraphQLQueryBuilder builder() {
    return new GraphQLQuery.GraphQLQueryBuilder();
  }

  @NotNull
  public OperationDefinition.Operation getOperationType() {
    return this.operationType;
  }

  @NotNull
  public String getQuery() {
    return this.query;
  }

  @NotNull
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

    @NotNull
    public GraphQLQuery build() {
      return new GraphQLQuery(this.query, this.operationType, this.queryNode);
    }

    @NotNull
    public GraphQLQuery.GraphQLQueryBuilder operationType(
        final OperationDefinition.Operation operationTypeParam) {
      this.operationType = operationTypeParam;
      return this;
    }

    @NotNull
    public GraphQLQuery.GraphQLQueryBuilder query(final String queryParam) {
      this.query = queryParam;
      return this;
    }

    @NotNull
    public GraphQLQuery.GraphQLQueryBuilder queryNode(final Field queryNodeParam) {
      this.queryNode = queryNodeParam;
      return this;
    }
  }
}
