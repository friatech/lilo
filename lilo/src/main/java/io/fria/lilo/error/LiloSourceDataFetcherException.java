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
package io.fria.lilo.error;

import graphql.GraphQLError;
import graphql.GraphQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * It is fired when a data fetcher catches errors as a returned result from remote schema source.
 */
public class LiloSourceDataFetcherException extends GraphQLException implements LiloException {

  private final List<? extends GraphQLError> errors;

  public LiloSourceDataFetcherException(final @NotNull List<? extends GraphQLError> errors) {
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
