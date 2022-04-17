package io.fria.lilo;

import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.jetbrains.annotations.NotNull;

public class DummyCoercing implements Coercing<Object, Object> {

  @NotNull
  @Override
  public Object parseLiteral(@NotNull final Object input) throws CoercingParseLiteralException {
    return input;
  }

  @NotNull
  @Override
  public Object parseValue(@NotNull final Object input) throws CoercingParseValueException {
    return input;
  }

  @NotNull
  @Override
  public Object serialize(@NotNull final Object dataFetcherResult)
      throws CoercingSerializeException {
    return dataFetcherResult;
  }
}
