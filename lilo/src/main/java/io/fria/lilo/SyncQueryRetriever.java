package io.fria.lilo;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface SyncQueryRetriever extends QueryRetriever<String> {

  @Override
  @NotNull
  String get(
      @NotNull LiloContext liloContext,
      @NotNull SchemaSource schemaSource,
      @NotNull GraphQLQuery query,
      @Nullable Object localContext);
}
