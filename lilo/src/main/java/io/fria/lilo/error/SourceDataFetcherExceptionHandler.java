package io.fria.lilo.error;

import graphql.GraphQLError;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherExceptionHandlerResult;
import graphql.execution.SimpleDataFetcherExceptionHandler;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class SourceDataFetcherExceptionHandler extends SimpleDataFetcherExceptionHandler {

  @Override
  public @NotNull DataFetcherExceptionHandlerResult onException(
      final @NotNull DataFetcherExceptionHandlerParameters handlerParameters) {

    final Throwable exception = handlerParameters.getException();

    if (exception instanceof SourceDataFetcherException) {
      return DataFetcherExceptionHandlerResult.newResult()
          .errors((List<GraphQLError>) ((SourceDataFetcherException) exception).getErrors())
          .build();
    }

    return super.onException(handlerParameters);
  }
}
