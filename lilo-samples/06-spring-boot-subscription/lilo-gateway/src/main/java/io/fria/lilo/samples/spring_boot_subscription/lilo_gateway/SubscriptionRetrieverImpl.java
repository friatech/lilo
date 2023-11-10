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

import io.fria.lilo.GraphQLQuery;
import io.fria.lilo.LiloContext;
import io.fria.lilo.SchemaSource;
import io.fria.lilo.subscription.SessionAdapter;
import io.fria.lilo.subscription.SubscriptionMessage;
import io.fria.lilo.subscription.SubscriptionRetriever;
import io.fria.lilo.subscription.SubscriptionSourceUtils;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

public class SubscriptionRetrieverImpl extends SubscriptionRetriever {

  private final @NotNull String subscriptionWsUrl;
  private final @NotNull WebSocketClient webSocketClient;

  SubscriptionRetrieverImpl(final @NotNull String subscriptionWsUrl) {
    this.subscriptionWsUrl = subscriptionWsUrl;
    this.webSocketClient = new StandardWebSocketClient();
  }

  @Override
  public void sendQuery(
      @NotNull final LiloContext liloContext2,
      @NotNull final SchemaSource schemaSource2,
      @NotNull final GraphQLQuery query,
      @NotNull final SessionAdapter sourceSessionAdapter,
      @Nullable final Object localContext2) {

    final WebSocketSession session;

    try {
      final WebSocketHttpHeaders httpHeaders = new WebSocketHttpHeaders();
      httpHeaders.add("Sec-WebSocket-Protocol", "graphql-transport-ws");
      session =
          this.webSocketClient
              .execute(
                  new SourceWebSocketHandler(sourceSessionAdapter, query),
                  httpHeaders,
                  new URI(this.subscriptionWsUrl))
              .get();

      if (session != null) {
        SubscriptionSourceUtils.startHandShakingWithSource(
            m -> session.sendMessage(new TextMessage(m)));
      }
    } catch (final ExecutionException e) {
      // pass
    } catch (final URISyntaxException | InterruptedException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static class SourceWebSocketHandler extends AbstractWebSocketHandler {

    private final @NotNull SessionAdapter sourceSessionAdapter;
    private final @NotNull GraphQLQuery query;

    SourceWebSocketHandler(
        final @NotNull SessionAdapter sourceSessionAdapter, final @NotNull GraphQLQuery query) {
      this.sourceSessionAdapter = sourceSessionAdapter;
      this.query = query;
    }

    @Override
    public void afterConnectionClosed(
        final @NotNull WebSocketSession session, final @NotNull CloseStatus status) {
      this.sourceSessionAdapter.closeSession();
    }

    @Override
    protected void handleTextMessage(
        final @NotNull WebSocketSession sourceSession, final @NotNull TextMessage message) {

      try {
        final SubscriptionMessage subscriptionMessage =
            SubscriptionSourceUtils.convertToSubscriptionMessage(message.getPayload());

        if ("connection_ack".equals(subscriptionMessage.getType())) {
          SubscriptionSourceUtils.sendQueryToSource(
              this.query, m -> sourceSession.sendMessage(new TextMessage(m)));
        } else if ("next".equals(subscriptionMessage.getType())) {
          this.sourceSessionAdapter.sendMessage(
              Objects.requireNonNull(subscriptionMessage.getPayload()).toString());
        }
      } catch (final Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
}
