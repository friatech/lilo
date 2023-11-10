package io.fria.lilo.samples.spring_boot_subscription.lilo_gateway;

import io.fria.lilo.subscription.SubscriptionSourcePublisher;
import io.fria.lilo.subscription.WebSocketSessionWrapper;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * ClientSessionWrapper encapsulates the session which is bound between gateway and connected
 * GraphQL client.
 */
public final class ClientSessionWrapper implements WebSocketSessionWrapper {

  private final @NotNull WebSocketSession nativeSession;
  private @Nullable SubscriptionSourcePublisher publisher;

  private ClientSessionWrapper(final @NotNull WebSocketSession nativeSession) {
    this.nativeSession = nativeSession;
  }

  static @Nullable ClientSessionWrapper wrap(final @Nullable WebSocketSession nativeSession) {

    if (nativeSession == null) {
      return null;
    }

    return new ClientSessionWrapper(nativeSession);
  }

  @Override
  public void close() {
    try {
      this.nativeSession.close();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean isOpen() {
    return this.nativeSession.isOpen();
  }

  @Override
  public @Nullable SubscriptionSourcePublisher getPublisher() {
    return this.publisher;
  }

  @Override
  public void send(final @NotNull String message) {
    try {
      this.nativeSession.sendMessage(new TextMessage(message));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void setPublisher(@Nullable final SubscriptionSourcePublisher publisher) {
    this.publisher = publisher;
  }
}
