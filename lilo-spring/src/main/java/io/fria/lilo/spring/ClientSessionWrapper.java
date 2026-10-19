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
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.SessionLimitExceededException;

/**
 * ClientSessionWrapper encapsulates the session which is bound between gateway and connected
 * GraphQL client.
 *
 * <p>Frames for one session are sent from several threads (the websocket thread, the gateway's
 * stitch executor, source socket threads) while {@link WebSocketSession#sendMessage} is not
 * thread-safe. A wrapper sends in one of two ways:
 *
 * <ul>
 *   <li>Through a {@link ConcurrentWebSocketSessionDecorator}, when one is stored in the session's
 *       attributes under {@link #DECORATOR_ATTRIBUTE}. {@link GatewayWebSocketHandler} decorates
 *       every client session once, when the connection is established. The decorator serialises the
 *       writes without blocking: a sender that finds another thread writing queues its frame and
 *       returns, and the writing thread flushes it. When the session exceeds the decorator's send
 *       time or buffer size limit, the session is closed with {@link
 *       CloseStatus#SESSION_NOT_RELIABLE}, which releases its operations through {@code
 *       afterConnectionClosed}.
 *   <li>Otherwise, under a lock kept in the session's attributes under {@link #SEND_LOCK_ATTRIBUTE}
 *       (or the session itself when its attributes cannot be modified), so every wrapper of the
 *       same session shares it. Concurrent senders wait for each other. This serves the gateway's
 *       source connections, which carry little traffic.
 * </ul>
 */
public final class ClientSessionWrapper implements WebSocketSessionWrapper {

  /** Session attribute holding the lock that serialises the sends of an undecorated session. */
  public static final String SEND_LOCK_ATTRIBUTE = "lilo.send.lock";

  /** Session attribute holding the decorator that serialises and bounds the sends of a session. */
  public static final String DECORATOR_ATTRIBUTE = "lilo.send.decorator";

  private final @NotNull WebSocketSession nativeSession;
  private final @Nullable ConcurrentWebSocketSessionDecorator decorator;
  private final @Nullable Object sendLock;

  private ClientSessionWrapper(final @NotNull WebSocketSession nativeSession) {
    this.nativeSession = nativeSession;
    this.decorator = decoratorOf(nativeSession);
    this.sendLock = this.decorator == null ? sendLock(nativeSession) : null;
  }

  private static @Nullable ConcurrentWebSocketSessionDecorator decoratorOf(
      final @NotNull WebSocketSession nativeSession) {
    final Object decorator = nativeSession.getAttributes().get(DECORATOR_ATTRIBUTE);
    return decorator instanceof ConcurrentWebSocketSessionDecorator
        ? (ConcurrentWebSocketSessionDecorator) decorator
        : null;
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
   * Decorates the session with a {@link ConcurrentWebSocketSessionDecorator}, once: the decorator
   * is stored in the session's attributes, and later calls, as well as {@link #wrap}, use the
   * stored one. A session whose attributes cannot be modified stays undecorated and sends under a
   * lock.
   *
   * @param nativeSession Session object provided by Spring
   * @param sendTimeLimitMillis how long a send may take before the session is closed
   * @param bufferSizeLimit how many bytes may wait to be sent before the session is closed
   * @return Session Wrapper object which sends through the decorator
   */
  static @NotNull ClientSessionWrapper decorate(
      final @NotNull WebSocketSession nativeSession,
      final int sendTimeLimitMillis,
      final int bufferSizeLimit) {
    try {
      nativeSession
          .getAttributes()
          .computeIfAbsent(
              DECORATOR_ATTRIBUTE,
              key ->
                  new ConcurrentWebSocketSessionDecorator(
                      nativeSession, sendTimeLimitMillis, bufferSizeLimit));
    } catch (final UnsupportedOperationException e) {
      // Falls back to sending under a lock
    }
    return new ClientSessionWrapper(nativeSession);
  }

  /**
   * wrap method helps you create a instance from native spring WebSocketSession. The wrapper sends
   * through the session's decorator if {@link GatewayWebSocketHandler} decorated it.
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
      if (this.decorator == null) {
        this.nativeSession.close();
      } else {
        this.decorator.close(CloseStatus.NORMAL);
      }
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
   * Sends a text frame if the session is open. A send to a session that closed meanwhile is
   * dropped.
   *
   * <p>A decorated session never blocks the caller while another thread writes: the frame is queued
   * and flushed by the writing thread. If the session exceeds its send time or buffer size limit,
   * it is closed with {@link CloseStatus#SESSION_NOT_RELIABLE} and the frame is dropped. The limits
   * are checked only when a send finds another thread writing; the thread that is writing is bound
   * only by the container's own blocking send timeout.
   *
   * <p>An undecorated session sends under its lock: concurrent callers wait for each other.
   *
   * @param message websocket string message
   */
  @Override
  public void send(final @NotNull String message) {
    if (this.decorator != null) {
      this.sendDecorated(this.decorator, message);
      return;
    }

    synchronized (Objects.requireNonNull(this.sendLock)) {
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

  private void sendDecorated(
      final @NotNull ConcurrentWebSocketSessionDecorator session, final @NotNull String message) {
    try {
      if (session.isOpen()) {
        session.sendMessage(new TextMessage(message));
      }
    } catch (final SessionLimitExceededException e) {
      // The client does not keep up: close it, as Spring's own handlers do; its close releases
      // its operations
      try {
        session.close(e.getStatus());
      } catch (final IOException closeFailure) {
        throw new RuntimeException(closeFailure);
      }
    } catch (final IllegalStateException e) {
      // Session is closed but still tries to send
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}
