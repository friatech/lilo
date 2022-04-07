package io.fria.lilo;

import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;

public class DummyCoercing implements Coercing<Object, Object> {

    @Override
    public Object parseLiteral(final Object input) throws CoercingParseLiteralException {
        return input;
    }

    @Override
    public Object parseValue(final Object input) throws CoercingParseValueException {
        return input;
    }

    @Override
    public Object serialize(final Object dataFetcherResult) throws CoercingSerializeException {
        return dataFetcherResult;
    }
}
