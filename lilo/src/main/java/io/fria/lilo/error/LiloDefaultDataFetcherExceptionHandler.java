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
package io.fria.lilo.error;

import graphql.GraphQLError;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherExceptionHandlerResult;
import graphql.execution.SimpleDataFetcherExceptionHandler;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;

/**
 * This is the default exception handler provided by Lilo. It can be overridden via @{@link
 * io.fria.lilo.Lilo.LiloBuilder#defaultDataFetcherExceptionHandler} method.
 */
public class LiloDefaultDataFetcherExceptionHandler extends SimpleDataFetcherExceptionHandler {

  @Override
  public @NotNull CompletableFuture<DataFetcherExceptionHandlerResult> handleException(
      final @NotNull DataFetcherExceptionHandlerParameters handlerParameters) {

    final Throwable exception = handlerParameters.getException();

    if (exception instanceof LiloSourceDataFetcherException) {

      final DataFetcherExceptionHandlerResult result =
          DataFetcherExceptionHandlerResult.newResult()
              .errors((List<GraphQLError>) ((LiloSourceDataFetcherException) exception).getErrors())
              .build();

      return CompletableFuture.completedFuture(result);
    }

    return super.handleException(handlerParameters);
  }
}
