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

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.instrumentation.Instrumentation;
import io.fria.lilo.error.LiloDefaultDataFetcherExceptionHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Lilo {

  private final LiloContext context;

  private Lilo(final @NotNull LiloContext context) {
    this.context = context;
  }

  public static @NotNull LiloBuilder builder() {
    return new LiloBuilder();
  }

  public @NotNull LiloContext getContext() {
    return this.context;
  }

  public @NotNull ExecutionResult stitch(final @NotNull ExecutionInput executionInput) {

    this.invalidateIfFetchingBeforeEveryRequest();

    return this.context.getGraphQL(executionInput).execute(executionInput);
  }

  public @NotNull ExecutionResult stitch(final @NotNull String query) {

    return this.stitch(ExecutionInput.newExecutionInput().query(query).build());
  }

  public @NotNull CompletableFuture<ExecutionResult> stitchAsync(
      final @NotNull ExecutionInput executionInput) {

    this.invalidateIfFetchingBeforeEveryRequest();

    return this.context
        .getGraphQLAsync(executionInput)
        .thenCompose(graphQL -> graphQL.executeAsync(executionInput));
  }

  private void invalidateIfFetchingBeforeEveryRequest() {

    if (IntrospectionFetchingMode.FETCH_BEFORE_EVERY_REQUEST
        == this.context.getIntrospectionFetchingMode()) {
      this.context.invalidateAll();
    }
  }

  public static final class LiloBuilder {

    private final Map<String, SchemaSource> schemaSources = new HashMap<>();
    private DataFetcherExceptionHandler dataFetcherExceptionHandler =
        new LiloDefaultDataFetcherExceptionHandler();
    private IntrospectionFetchingMode introspectionFetchingMode =
        IntrospectionFetchingMode.CACHE_UNTIL_INVALIDATION;
    private boolean retrySchemaLoad = true;
    private Instrumentation instrumentation;
    private @Nullable Integer maxQueryDepth;
    private @Nullable Integer maxQueryComplexity;
    private boolean introspectionEnabled = true;
    private @Nullable Consumer<GraphQL.Builder> graphQLCustomizer;

    @SuppressWarnings("checkstyle:WhitespaceAround")
    private LiloBuilder() {}

    public @NotNull LiloBuilder addSource(final @NotNull SchemaSource schemaSource) {
      this.schemaSources.put(schemaSource.getName(), Objects.requireNonNull(schemaSource));
      return this;
    }

    public @NotNull Lilo build() {

      return new Lilo(
          new LiloContext(
              this.dataFetcherExceptionHandler,
              this.introspectionFetchingMode,
              this.retrySchemaLoad,
              this.instrumentation,
              new LiloContext.RequestLimits(
                  this.maxQueryDepth, this.maxQueryComplexity, this.introspectionEnabled),
              this.graphQLCustomizer,
              this.schemaSources.values().toArray(new SchemaSource[0])));
    }

    public @NotNull LiloBuilder defaultDataFetcherExceptionHandler(
        final @NotNull DataFetcherExceptionHandler defaultDataFetcherExceptionHandler) {
      this.dataFetcherExceptionHandler = defaultDataFetcherExceptionHandler;
      return this;
    }

    /**
     * Customizes the {@link GraphQL.Builder} of the merged gateway schema, for example to add a
     * {@code PreparsedDocumentProvider} or an execution strategy. It is called every time the
     * merged schema is built, after Lilo applied its own settings (the exception handler and the
     * instrumentations), so it can also override them.
     *
     * @param graphQLCustomizer the customizer, or {@code null} for none
     * @return this builder
     */
    @SuppressWarnings("HiddenField")
    public @NotNull LiloBuilder graphQLCustomizer(
        final @Nullable Consumer<GraphQL.Builder> graphQLCustomizer) {
      this.graphQLCustomizer = graphQLCustomizer;
      return this;
    }

    @SuppressWarnings("HiddenField")
    public @NotNull LiloBuilder instrumentation(final @Nullable Instrumentation instrumentation) {
      this.instrumentation = instrumentation;
      return this;
    }

    /**
     * Enables or disables introspection ({@code __schema} and {@code __type}) on the gateway's own
     * schema. It is enabled by default. When disabled, an introspection query gets a GraphQL error
     * instead of data. It is a per-request setting of this Lilo instance (graphql-java's {@code
     * Introspection.INTROSPECTION_DISABLED} context flag), not the JVM-wide switch, and it does not
     * affect the introspection Lilo sends to its sources to load their schemas.
     *
     * @param introspectionEnabled {@code false} to reject introspection queries
     * @return this builder
     */
    @SuppressWarnings("HiddenField")
    public @NotNull LiloBuilder introspectionEnabled(final boolean introspectionEnabled) {
      this.introspectionEnabled = introspectionEnabled;
      return this;
    }

    @SuppressWarnings("HiddenField")
    public @NotNull LiloBuilder introspectionFetchingMode(
        final @NotNull IntrospectionFetchingMode introspectionFetchingMode) {
      this.introspectionFetchingMode = introspectionFetchingMode;
      return this;
    }

    /**
     * Rejects queries nested deeper than the given depth before any source is called, using
     * graphql-java's {@code MaxQueryDepthInstrumentation}. Root fields are at depth 1. A rejected
     * query gets a GraphQL error and no data. Not limited by default.
     *
     * @param maxQueryDepth the maximum depth, at least 1
     * @return this builder
     * @throws IllegalArgumentException if {@code maxQueryDepth} is zero or negative
     */
    @SuppressWarnings("HiddenField")
    public @NotNull LiloBuilder maxQueryDepth(final int maxQueryDepth) {
      if (maxQueryDepth <= 0) {
        throw new IllegalArgumentException("maxQueryDepth must be positive: " + maxQueryDepth);
      }

      this.maxQueryDepth = maxQueryDepth;
      return this;
    }

    /**
     * Rejects queries whose complexity exceeds the given value before any source is called, using
     * graphql-java's {@code MaxQueryComplexityInstrumentation}. Every selected field counts 1. A
     * rejected query gets a GraphQL error and no data. Not limited by default.
     *
     * @param maxQueryComplexity the maximum complexity, at least 1
     * @return this builder
     * @throws IllegalArgumentException if {@code maxQueryComplexity} is zero or negative
     */
    @SuppressWarnings("HiddenField")
    public @NotNull LiloBuilder maxQueryComplexity(final int maxQueryComplexity) {
      if (maxQueryComplexity <= 0) {
        throw new IllegalArgumentException(
            "maxQueryComplexity must be positive: " + maxQueryComplexity);
      }

      this.maxQueryComplexity = maxQueryComplexity;
      return this;
    }

    @SuppressWarnings("HiddenField")
    public @NotNull LiloBuilder retrySchemaLoad(final boolean retrySchemaLoad) {
      this.retrySchemaLoad = retrySchemaLoad;
      return this;
    }
  }
}
