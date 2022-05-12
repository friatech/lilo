package io.fria.lilo.error;

import graphql.GraphQLError;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public class SourceDataFetcherException extends RuntimeException {

  private final List<? extends GraphQLError> errors;

  public SourceDataFetcherException(final @NotNull List<? extends GraphQLError> errors) {
    this.errors = Objects.requireNonNull(errors);
  }

  public @NotNull List<? extends GraphQLError> getErrors() {
    return this.errors;
  }
}
