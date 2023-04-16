package io.fria.lilo;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface QueryRetriever<T> {

  @NotNull
  T get(
      @NotNull LiloContext liloContext,
      @NotNull SchemaSource schemaSource,
      @NotNull GraphQLQuery query,
      @Nullable Object localContext);
}
