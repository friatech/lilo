package io.fria.lilo;

import io.fria.lilo.subscription.SubscriptionRetriever;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.reactivestreams.Publisher;

public class DefaultRemoteSubscriptionRetriever implements SubscriptionRetriever {

  private final String schemaUrl;

  public DefaultRemoteSubscriptionRetriever(final @NotNull String schemaUrl) {
    this.schemaUrl = Objects.requireNonNull(schemaUrl);
  }

  @Override
  public void connect(
      @NotNull final LiloContext liloContext,
      @NotNull final SchemaSource schemaSource,
      @Nullable final Object localContext) {}

  @Override
  public @NotNull Publisher<String> subscribe(
      @NotNull final LiloContext liloContext,
      @NotNull final SchemaSource schemaSource,
      @NotNull final GraphQLQuery query,
      @Nullable final Object localContext) {
    return null;
  }
}
