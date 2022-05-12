package io.fria.lilo;

import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.jetbrains.annotations.NotNull;

public class DummyCoercing implements Coercing<Object, Object> {

  @NotNull
  @Override
  public Object parseLiteral(final @NotNull Object input) throws CoercingParseLiteralException {
    return input;
  }

  @NotNull
  @Override
  public Object parseValue(final @NotNull Object input) throws CoercingParseValueException {
    return input;
  }

  @NotNull
  @Override
  public Object serialize(final @NotNull Object dataFetcherResult)
      throws CoercingSerializeException {
    return dataFetcherResult;
  }
}
