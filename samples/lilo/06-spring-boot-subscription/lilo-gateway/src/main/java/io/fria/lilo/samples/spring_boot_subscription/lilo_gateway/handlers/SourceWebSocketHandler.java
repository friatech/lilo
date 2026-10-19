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

import io.fria.lilo.GraphQLQuery;
import io.fria.lilo.subscription.SubscriptionSourceHandler;
import io.fria.lilo.subscription.SubscriptionSourcePublisher;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

public class SourceWebSocketHandler extends AbstractWebSocketHandler {

  /**
   * Largest message accepted from the source, in characters (not bytes). The container hands a
   * message bigger than its inbound buffer (8 KiB by default on Tomcat) to this handler in
   * fragments, which are joined here up to this limit. Size it to the largest real event.
   */
  private static final int MAX_MESSAGE_CHARS = 1024 * 1024;

  private final @NotNull GraphQLQuery query;
  private final @NotNull SubscriptionSourcePublisher publisher;

  // The container delivers the messages of one session one at a time
  private final @NotNull StringBuilder fragments = new StringBuilder();
  private boolean oversized;

  private @Nullable SubscriptionSourceHandler sourceHandler;

  public SourceWebSocketHandler(
      final @NotNull GraphQLQuery query, final @NotNull SubscriptionSourcePublisher publisher) {
    this.query = query;
    this.publisher = publisher;
  }

  @Override
  public void afterConnectionEstablished(final @NotNull WebSocketSession nativeSession) {
    this.sourceHandler = new SubscriptionSourceHandler(this.query, this.publisher);
    this.sourceHandler.handleSessionStart(ClientSessionWrapper.wrap(nativeSession));
  }

  @Override
  public void afterConnectionClosed(
      final @Nullable WebSocketSession nativeSession, final @NotNull CloseStatus status) {

    if (this.sourceHandler == null) {
      throw new IllegalStateException("Source Handler must be initialized");
    }

    this.fragments.setLength(0);
    // The close status (e.g. 1009) becomes part of the failure the gateway logs
    final String reason = status.getReason() == null ? "" : " " + status.getReason();
    this.sourceHandler.handleSessionClose(
        ClientSessionWrapper.wrap(nativeSession), status.getCode() + reason);
  }

  /** Large events arrive in fragments instead of closing the socket with 1009. */
  @Override
  public boolean supportsPartialMessages() {
    return true;
  }

  @Override
  public void handleTextMessage(
      final @NotNull WebSocketSession nativeSession, final @NotNull TextMessage message)
      throws IOException {

    if (this.sourceHandler == null) {
      throw new IllegalStateException("Source Handler must be initialized");
    }

    if (this.oversized) {
      return; // The session is ending: drop everything that follows
    }

    if (this.fragments.length() + message.getPayloadLength() > MAX_MESSAGE_CHARS) {
      this.oversized = true;
      this.fragments.setLength(0);
      this.publisher.error(
          new IllegalStateException("Source message exceeds " + MAX_MESSAGE_CHARS + " characters"));
      if (nativeSession.isOpen()) {
        nativeSession.close(CloseStatus.TOO_BIG_TO_PROCESS);
      }
      return;
    }

    this.fragments.append(message.getPayload());

    if (message.isLast()) {
      final String payload = this.fragments.toString();
      this.fragments.setLength(0);
      this.sourceHandler.handleMessage(ClientSessionWrapper.wrap(nativeSession), payload);
    }
  }
}
