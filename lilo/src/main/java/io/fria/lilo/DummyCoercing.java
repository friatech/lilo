package io.fria.lilo;

import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.jetbrains.annotations.NotNull;

public class DummyCoercing implements Coercing<Object, Object> {

  @Override
  public @NotNull Object parseLiteral(final @NotNull Object input)
      throws CoercingParseLiteralException {
    return input;
  }

  @Override
  public @NotNull Object parseValue(final @NotNull Object input)
      throws CoercingParseValueException {
    return input;
  }

  @Override
  public @NotNull Object serialize(final @NotNull Object dataFetcherResult)
      throws CoercingSerializeException {
    return dataFetcherResult;
  }
}
