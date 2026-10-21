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
package io.fria.lilo;

import graphql.GraphQL;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.instrumentation.Instrumentation;
import java.util.Objects;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The engine settings of a {@link LiloContext}, which are everything but its sources. */
final class LiloSettings {

  private final DataFetcherExceptionHandler dataFetcherExceptionHandler;
  private final IntrospectionFetchingMode introspectionFetchingMode;
  private final boolean retrySchemaLoad;
  private final @Nullable Instrumentation instrumentation;
  private final RequestLimits requestLimits;
  private final @Nullable Consumer<GraphQL.Builder> graphQLCustomizer;
  private final @Nullable String schemaDescription;

  LiloSettings(
      final @NotNull DataFetcherExceptionHandler dataFetcherExceptionHandler,
      final @NotNull IntrospectionFetchingMode introspectionFetchingMode,
      final boolean retrySchemaLoad,
      final @Nullable Instrumentation instrumentation,
      final @NotNull RequestLimits requestLimits,
      final @Nullable Consumer<GraphQL.Builder> graphQLCustomizer,
      final @Nullable String schemaDescription) {
    this.dataFetcherExceptionHandler = Objects.requireNonNull(dataFetcherExceptionHandler);
    this.introspectionFetchingMode = Objects.requireNonNull(introspectionFetchingMode);
    this.retrySchemaLoad = retrySchemaLoad;
    this.instrumentation = instrumentation;
    this.requestLimits = Objects.requireNonNull(requestLimits);
    this.graphQLCustomizer = graphQLCustomizer;
    this.schemaDescription = schemaDescription;
  }

  @NotNull
  DataFetcherExceptionHandler getDataFetcherExceptionHandler() {
    return this.dataFetcherExceptionHandler;
  }

  @NotNull
  IntrospectionFetchingMode getIntrospectionFetchingMode() {
    return this.introspectionFetchingMode;
  }

  boolean isRetrySchemaLoad() {
    return this.retrySchemaLoad;
  }

  @Nullable
  Instrumentation getInstrumentation() {
    return this.instrumentation;
  }

  @NotNull
  RequestLimits getRequestLimits() {
    return this.requestLimits;
  }

  @Nullable
  Consumer<GraphQL.Builder> getGraphQLCustomizer() {
    return this.graphQLCustomizer;
  }

  @Nullable
  String getSchemaDescription() {
    return this.schemaDescription;
  }
}
