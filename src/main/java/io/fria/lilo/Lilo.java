package io.fria.lilo;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.execution.DataFetcherExceptionHandler;
import io.fria.lilo.error.SourceDataFetcherExceptionHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public final class Lilo {

  private final LiloContext context;

  private Lilo(@NotNull final LiloContext context) {
    this.context = context;
  }

  public static LiloBuilder builder() {
    return new LiloBuilder();
  }

  @NotNull
  public LiloContext getContext() {
    return this.context;
  }

  @NotNull
  public ExecutionResult stitch(@NotNull final ExecutionInput executionInput) {

    if (IntrospectionFetchingMode.FETCH_BEFORE_EVERY_REQUEST
        == this.context.getIntrospectionFetchingMode()) {
      this.context.invalidateAll();
    }

    return this.context.getGraphQL(executionInput).execute(executionInput);
  }

  public static final class LiloBuilder {

    private final Map<String, SchemaSource> schemaSources = new HashMap<>();
    private DataFetcherExceptionHandler dataFetcherExceptionHandler =
        new SourceDataFetcherExceptionHandler();
    private IntrospectionFetchingMode introspectionFetchingMode =
        IntrospectionFetchingMode.CACHE_UNTIL_INVALIDATION;

    @SuppressWarnings("checkstyle:WhitespaceAround")
    private LiloBuilder() {}

    @NotNull
    public LiloBuilder addSource(@NotNull final SchemaSource schemaSource) {
      this.schemaSources.put(schemaSource.getName(), Objects.requireNonNull(schemaSource));
      return this;
    }

    @NotNull
    public Lilo build() {

      return new Lilo(
          new LiloContext(
              this.dataFetcherExceptionHandler,
              this.introspectionFetchingMode,
              this.schemaSources.values().toArray(new SchemaSource[0])));
    }

    @NotNull
    public LiloBuilder defaultDataFetcherExceptionHandler(
        @NotNull final DataFetcherExceptionHandler defaultDataFetcherExceptionHandler) {
      this.dataFetcherExceptionHandler = defaultDataFetcherExceptionHandler;
      return this;
    }

    @NotNull
    public LiloBuilder introspectionFetchingMode(
        @NotNull final IntrospectionFetchingMode introspectionFetchingMode) {
      this.introspectionFetchingMode = introspectionFetchingMode;
      return this;
    }
  }
}
