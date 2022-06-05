package io.fria.lilo;

import graphql.ExecutionResult;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AsyncSchemaSource extends SchemaSource<CompletableFuture<ExecutionResult>> {

  @Override
  @NotNull
  CompletableFuture<ExecutionResult> execute(
      @NotNull LiloContext liloContext, @NotNull GraphQLQuery query, @Nullable Object localContext);

}
