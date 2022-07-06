package io.fria.lilo;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

interface IntrospectionRetriever<T> {

  @NotNull
  T get(
      @NotNull LiloContext liloContext,
      @NotNull SchemaSource schemaSource,
      @NotNull String query,
      @Nullable Object localContext);
}
