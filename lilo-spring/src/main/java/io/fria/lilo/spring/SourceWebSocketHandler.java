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

import io.fria.lilo.GraphQLQuery;
import io.fria.lilo.subscription.SubscriptionSourceHandler;
import io.fria.lilo.subscription.SubscriptionSourcePublisher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

/**
 * SourceWebSocketHandler class manages websocket communication between GraphQL gateway and remote
 * schema sources. Unlike GatewayWebSocketHandler it's not a singleton object and it will be created
 * for every single GraphQL subscription request. Mostly, it can be used inside a
 * SubscriptionRetriever implementation.
 */
public class SourceWebSocketHandler extends AbstractWebSocketHandler {

  private final @NotNull GraphQLQuery query;
  private final @NotNull SubscriptionSourcePublisher publisher;
  private @Nullable SubscriptionSourceHandler sourceHandler;

  /**
   * Creates a SourceWebSocketHandler instance
   *
   * @param query incoming GraphQL subscription query
   * @param publisher publisher object for subscribing the websocket stream data
   */
  public SourceWebSocketHandler(
      final @NotNull GraphQLQuery query, final @NotNull SubscriptionSourcePublisher publisher) {
    this.query = query;
    this.publisher = publisher;
  }

  /**
   * This method delegates session initialization to Lilo SubscriptionSourceHandler
   *
   * @param nativeSession Session object provided by Spring
   */
  @Override
  public void afterConnectionEstablished(final @NotNull WebSocketSession nativeSession) {
    this.sourceHandler = new SubscriptionSourceHandler(this.query, this.publisher);
    this.sourceHandler.handleSessionStart(ClientSessionWrapper.wrap(nativeSession));
  }

  /**
   * This method delegates session close operations to Lilo SubscriptionSourceHandler
   *
   * @param nativeSession Session object provided by Spring
   * @param closeStatus Session close status, not used by Lilo
   */
  @Override
  public void afterConnectionClosed(
      final @Nullable WebSocketSession nativeSession, final @NotNull CloseStatus closeStatus) {

    if (this.sourceHandler == null) {
      throw new IllegalStateException("Source Handler must be initialized");
    }

    this.sourceHandler.handleSessionClose(ClientSessionWrapper.wrap(nativeSession));
  }

  @Override
  public void handleTextMessage(
      final @NotNull WebSocketSession nativeSession, final @NotNull TextMessage message) {

    if (this.sourceHandler == null) {
      throw new IllegalStateException("Source Handler must be initialized");
    }

    this.sourceHandler.handleMessage(
        ClientSessionWrapper.wrap(nativeSession), message.getPayload());
  }
}
