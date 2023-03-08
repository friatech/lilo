package io.fria.lilo.error;

import graphql.GraphQLError;
import graphql.GraphQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
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

    final String[] strings = errors.get(0).getMessage().split(" : ");

    return Arrays.stream(strings)
        .distinct()
        .filter(e -> !e.startsWith("Exception while fetching data"))
        .collect(Collectors.joining(" : "));
  }

  public @NotNull List<? extends GraphQLError> getErrors() {
    return this.errors;
  }
}
