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
package io.fria.lilo.samples.spring_boot_subscription.lilo_gateway.handlers;

import io.fria.lilo.subscription.WebSocketSessionWrapper;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/**
 * ClientSessionWrapper encapsulates the session which is bound between gateway and connected
 * GraphQL client.
 *
 * <p>Lilo sends to one session from several threads (the websocket thread, the gateway's stitch
 * threads and the source connections), while {@link WebSocketSession#sendMessage} is not
 * thread-safe. Every wrapper of a session therefore sends through one {@link
 * ConcurrentWebSocketSessionDecorator}, created on first use and kept in the session's attributes.
 * It serialises the writes and closes a session that falls too far behind.
 */
public final class ClientSessionWrapper implements WebSocketSessionWrapper {

  private static final String DECORATOR_ATTRIBUTE = "sample.send.decorator";
  private static final int SEND_TIME_LIMIT_MILLIS = 10_000;
  private static final int BUFFER_SIZE_LIMIT = 512 * 1024;

  private final @NotNull WebSocketSession nativeSession;
  private final @NotNull WebSocketSession sender;

  private ClientSessionWrapper(final @NotNull WebSocketSession nativeSession) {
    this.nativeSession = nativeSession;
    // Spring keeps session attributes in a concurrent map, so every thread gets the same decorator
    this.sender =
        (WebSocketSession)
            nativeSession
                .getAttributes()
                .computeIfAbsent(
                    DECORATOR_ATTRIBUTE,
                    key ->
                        new ConcurrentWebSocketSessionDecorator(
                            nativeSession, SEND_TIME_LIMIT_MILLIS, BUFFER_SIZE_LIMIT));
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
      this.sender.close();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public @NotNull String getId() {
    return this.nativeSession.getId();
  }

  @Override
  public boolean isOpen() {
    return this.nativeSession.isOpen();
  }

  @Override
  public void send(final @NotNull String message) {
    if (!this.sender.isOpen()) {
      // Closed meanwhile: the message is dropped
      return;
    }

    try {
      this.sender.sendMessage(new TextMessage(message));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}
