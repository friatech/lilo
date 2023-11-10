/*
 * Copyright 2023 the original author or authors.
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
package io.fria.lilo.samples.spring_boot_subscription.lilo_gateway;

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
  public @NotNull String getId() {
    return this.nativeSession.getId();
  }

  @Override
  public void send(final @NotNull String message) {
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
