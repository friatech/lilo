package io.fria.lilo;

import graphql.ExecutionResult;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

interface BaseSchemaSource extends SchemaSource {

  @NotNull
  CompletableFuture<ExecutionResult> execute(
      @NotNull LiloContext liloContext, @NotNull GraphQLQuery query, @Nullable Object localContext);

  boolean isSchemaLoaded();

  void loadSchema(@NotNull LiloContext context, @Nullable Object localContext);
}
