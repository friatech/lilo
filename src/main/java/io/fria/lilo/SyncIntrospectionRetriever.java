package io.fria.lilo;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface SyncIntrospectionRetriever extends IntrospectionRetriever<String> {

  @Override
  @NotNull
  String get(
      @NotNull LiloContext liloContext,
      @NotNull SchemaSource schemaSource,
      @NotNull String query,
      @Nullable Object localContext);
}
