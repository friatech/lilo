package io.fria.lilo;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.execution.DataFetcherExceptionHandler;
import io.fria.lilo.error.SourceDataFetcherExceptionHandler;
import java.util.HashMap;
import java.util.Map;

public final class Lilo {

  private final LiloContext context;

  private Lilo(final LiloContext context) {
    this.context = context;
  }

  public static LiloBuilder builder() {
    return new LiloBuilder();
  }

  public LiloContext getContext() {
    return this.context;
  }

  public ExecutionResult stitch(final ExecutionInput executionInput) {
    return this.context.getGraphQL(executionInput).execute(executionInput);
  }

  public static final class LiloBuilder {

    private final Map<String, SchemaSource> schemaSources = new HashMap<>();
    private DataFetcherExceptionHandler dataFetcherExceptionHandler =
        new SourceDataFetcherExceptionHandler();

    @SuppressWarnings("checkstyle:WhitespaceAround")
    private LiloBuilder() {}

    public LiloBuilder addSource(final SchemaSource schemaSource) {
      this.schemaSources.put(schemaSource.getName(), schemaSource);
      return this;
    }

    public Lilo build() {

      return new Lilo(
          new LiloContext(
              this.dataFetcherExceptionHandler,
              this.schemaSources.values().toArray(new SchemaSource[0])));
    }

    public LiloBuilder defaultDataFetcherExceptionHandler(
        final DataFetcherExceptionHandler defaultDataFetcherExceptionHandler) {
      this.dataFetcherExceptionHandler = defaultDataFetcherExceptionHandler;
      return this;
    }
  }
}
