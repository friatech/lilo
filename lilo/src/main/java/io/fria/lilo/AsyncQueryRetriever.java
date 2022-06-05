package io.fria.lilo;

import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AsyncQueryRetriever extends QueryRetriever<CompletableFuture<String>> {

  @Override
  @NotNull
  CompletableFuture<String> get(
      @NotNull LiloContext liloContext,
      @NotNull SchemaSource schemaSource,
      @NotNull GraphQLQuery query,
      @Nullable Object localContext);
}
