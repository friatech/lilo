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
import io.fria.lilo.subscription.SubscriptionMessage;
import io.fria.lilo.subscription.SubscriptionRetriever;
import io.fria.lilo.subscription.SubscriptionSourceHandler;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import reactor.core.publisher.Sinks;

public class SubscriptionRetrieverImpl implements SubscriptionRetriever {

  private final @NotNull String subscriptionWsUrl;
  private final @NotNull WebSocketClient webSocketClient;
  private final SubscriptionSourceHandler liloHandler = new SubscriptionSourceHandler();

  public SubscriptionRetrieverImpl(final @NotNull String subscriptionWsUrl) {
    this.subscriptionWsUrl = subscriptionWsUrl;
    this.webSocketClient = new StandardWebSocketClient();
  }

  @Override
  public @NotNull Publisher<Object> sendQuery(
      @NotNull final LiloContext liloContext,
      @NotNull final SchemaSource schemaSource,
      @NotNull final GraphQLQuery query,
      @Nullable final Object localContext) {

    final Sinks.Many<Object> sink = Sinks.many().multicast().onBackpressureBuffer();
    final WebSocketSession   session;

    try {
      final WebSocketHttpHeaders httpHeaders = new WebSocketHttpHeaders();
      httpHeaders.add("Sec-WebSocket-Protocol", "graphql-transport-ws");
      session =
          this.webSocketClient
              .execute(
                  new AbstractWebSocketHandler() {

                    @Override
                    public void afterConnectionClosed(
                        final WebSocketSession session, final CloseStatus status) {
                      sink.tryEmitComplete();
                    }

                    @Override
                    protected void handleTextMessage(
                        final @NotNull WebSocketSession session,
                        final @NotNull TextMessage message) {

                      try {
                        final SubscriptionMessage subscriptionMessage =
                            SubscriptionRetrieverImpl.this.liloHandler.convertToSubscriptionMessage(
                                message.getPayload());

                        if ("connection_ack".equals(subscriptionMessage.getType())) {
                          SubscriptionRetrieverImpl.this.liloHandler.sendQuery(
                              query, m -> session.sendMessage(new TextMessage(m)));
                        } else if ("next".equals(subscriptionMessage.getType())) {
                          sink.tryEmitNext(subscriptionMessage.getPayload());
                        }
                      } catch (final Exception e) {
                        throw new RuntimeException(e);
                      }
                    }
                  },
                  httpHeaders,
                  new URI(this.subscriptionWsUrl))
              .get();

      if (session != null) {
        final WebSocketSession finalSession = session;
        this.liloHandler.startHandShaking(m -> finalSession.sendMessage(new TextMessage(m)));
      }
    } catch (final ExecutionException e) {
      // pass
    } catch (final URISyntaxException | InterruptedException | IOException e) {
      throw new RuntimeException(e);
    }

    return sink.asFlux();
  }
}
