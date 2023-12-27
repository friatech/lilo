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

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("InlineMeSuggester")
public class DummyCoercing implements Coercing<Object, Object> {

  @Override
  @Deprecated
  public @NotNull Object parseLiteral(final @NotNull Object input)
      throws CoercingParseLiteralException {
    return input;
  }

  @Override
  public @Nullable Object parseLiteral(
      @NotNull final Value<?> input,
      @NotNull final CoercedVariables variables,
      @NotNull final GraphQLContext graphQLContext,
      @NotNull final Locale locale)
      throws CoercingParseLiteralException {
    return input;
  }

  @Override
  @Deprecated
  public @NotNull Object parseValue(final @NotNull Object input)
      throws CoercingParseValueException {
    return input;
  }

  @Override
  public @Nullable Object parseValue(
      @NotNull final Object input,
      @NotNull final GraphQLContext graphQLContext,
      @NotNull final Locale locale)
      throws CoercingParseValueException {
    return input;
  }

  @Override
  @Deprecated
  public @NotNull Object serialize(final @NotNull Object dataFetcherResult)
      throws CoercingSerializeException {
    return dataFetcherResult;
  }

  @Override
  public @Nullable Object serialize(
      @NotNull final Object dataFetcherResult,
      @NotNull final GraphQLContext graphQLContext,
      @NotNull final Locale locale)
      throws CoercingSerializeException {
    return dataFetcherResult;
  }
}
