package io.fria.lilo;

import graphql.GraphQLError;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherExceptionHandlerResult;
import graphql.execution.SimpleDataFetcherExceptionHandler;
import java.util.List;

class SourceDataFetcherExceptionHandler extends SimpleDataFetcherExceptionHandler {

    @Override
    public DataFetcherExceptionHandlerResult onException(final DataFetcherExceptionHandlerParameters handlerParameters) {

        final Throwable exception = handlerParameters.getException();

        if (exception instanceof SourceDataFetcherException) {
            return DataFetcherExceptionHandlerResult
                .newResult()
                .errors((List<GraphQLError>) ((SourceDataFetcherException) exception).getErrors())
                .build();
        }

        return super.onException(handlerParameters);
    }
}
