/*
 * Copyright 2023 the original author or authors.
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
