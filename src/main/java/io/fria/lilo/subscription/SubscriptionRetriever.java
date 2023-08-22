package io.fria.lilo.subscription;

import io.fria.lilo.GraphQLQuery;
import io.fria.lilo.LiloContext;
import io.fria.lilo.SchemaSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.reactivestreams.Publisher;

public interface SubscriptionRetriever {

  void connect(
      @NotNull LiloContext liloContext,
      @NotNull SchemaSource schemaSource,
      @Nullable Object localContext);

  void sendQuery(
      @NotNull LiloContext liloContext,
      @NotNull SchemaSource schemaSource,
      @NotNull GraphQLQuery query,
      @Nullable Object localContext);

  @NotNull
  Publisher<String> subscribe(
      @NotNull LiloContext liloContext,
      @NotNull SchemaSource schemaSource,
      @Nullable Object localContext);
}
