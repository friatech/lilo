/*
 * Copyright 2022-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fria.lilo.spring;

import io.fria.lilo.subscription.WebSocketSessionWrapper;
import java.io.IOException;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * ClientSessionWrapper encapsulates the session which is bound between gateway and connected
 * GraphQL client.
 *
 * <p>Frames for one session are sent from several threads (the websocket thread, the gateway's
 * stitch executor, source socket threads) while {@link WebSocketSession#sendMessage} is not
 * thread-safe. {@link #send} therefore serialises the sends of one native session on a lock kept in
 * the session's attributes under {@link #SEND_LOCK_ATTRIBUTE}, so every wrapper of the same session
 * shares it.
 */
public final class ClientSessionWrapper implements WebSocketSessionWrapper {

  /** Session attribute holding the lock that serialises the sends of the session. */
  public static final String SEND_LOCK_ATTRIBUTE = "lilo.send.lock";

  private final @NotNull WebSocketSession nativeSession;
  private final @NotNull Object sendLock;

  private ClientSessionWrapper(final @NotNull WebSocketSession nativeSession) {
    this.nativeSession = nativeSession;
    this.sendLock = sendLock(nativeSession);
  }

  /**
   * Returns the send lock of the session, creating it on first use. Spring's sessions keep their
   * attributes in a concurrent map, so concurrent first uses still agree on one lock; the handlers
   * create it when the connection is established, before any other thread sends. A session whose
   * attributes cannot be modified is its own lock.
   */
  private static @NotNull Object sendLock(final @NotNull WebSocketSession nativeSession) {
    try {
      return nativeSession
          .getAttributes()
          .computeIfAbsent(SEND_LOCK_ATTRIBUTE, key -> new Object());
    } catch (final UnsupportedOperationException e) {
      return nativeSession;
    }
  }

  /**
   * wrap method helps you create a instance from native spring WebSocketSession
   *
   * @param nativeSession Session object provided by Spring
   * @return Session Wrapper object which contains spring session
   */
  public static @Nullable ClientSessionWrapper wrap(
      final @Nullable WebSocketSession nativeSession) {

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

  /**
   * Returns the attributes of the native session, which include the handshake headers stored by
   * {@link GatewayHandshakeInterceptor}.
   *
   * @return the native session's attributes
   */
  @Override
  public @NotNull Map<String, Object> getAttributes() {
    return this.nativeSession.getAttributes();
  }

  @Override
  public @NotNull String getId() {
    return this.nativeSession.getId();
  }

  @Override
  public boolean isOpen() {
    return this.nativeSession.isOpen();
  }

  /**
   * Sends a text frame if the session is open. Sends of one session never overlap: concurrent
   * callers wait for each other. A send to a session that closed meanwhile is dropped.
   *
   * @param message websocket string message
   */
  @Override
  public void send(final @NotNull String message) {
    synchronized (this.sendLock) {
      try {
        if (this.nativeSession.isOpen()) {
          this.nativeSession.sendMessage(new TextMessage(message));
        }
      } catch (final IllegalStateException e) {
        // Session is closed but still tries to send
      } catch (final IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
