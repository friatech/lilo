package io.fria.lilo.error;

import graphql.GraphQLError;
import graphql.GraphQLException;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SourceDataFetcherException extends GraphQLException {

  private final List<? extends GraphQLError> errors;

  public SourceDataFetcherException(final @NotNull List<? extends GraphQLError> errors) {
    super(extractMessage(Objects.requireNonNull(errors)));
    this.errors = errors;
  }

  private static @Nullable String extractMessage(
      final @NotNull List<? extends GraphQLError> errors) {

    if (errors.isEmpty()) {
      return null;
    }

    final String[] strings = errors.get(0).getMessage().split(":");

    if (strings.length > 1) {
      return strings[strings.length - 1].strip();
    }

    return strings[0].strip();
  }

  public @NotNull List<? extends GraphQLError> getErrors() {
    return this.errors;
  }
}
