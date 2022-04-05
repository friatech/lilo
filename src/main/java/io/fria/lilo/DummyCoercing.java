package io.fria.lilo;

import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;

public class DummyCoercing implements Coercing<Object, Object> {

    @Override
    public Object parseLiteral(final Object input) throws CoercingParseLiteralException {
        throw new CoercingParseValueException("This is a dummy implementation");
    }

    @Override
    public Object parseValue(final Object input) throws CoercingParseValueException {
        throw new CoercingParseValueException("This is a dummy implementation");
    }

    @Override
    public Object serialize(final Object dataFetcherResult) throws CoercingSerializeException {
        return dataFetcherResult;
    }
}
