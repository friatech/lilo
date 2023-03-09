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
