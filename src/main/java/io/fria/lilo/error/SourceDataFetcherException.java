package io.fria.lilo.error;

import graphql.GraphQLError;
import java.util.List;

public class SourceDataFetcherException extends RuntimeException {

  private final List<? extends GraphQLError> errors;

  public SourceDataFetcherException(final List<? extends GraphQLError> errors) {
    this.errors = errors;
  }

  public List<? extends GraphQLError> getErrors() {
    return this.errors;
  }
}
