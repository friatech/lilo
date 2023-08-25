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
import io.fria.lilo.subscription.GraphQLSubscriptionMessage;
import io.fria.lilo.subscription.LiloSubscriptionDefaultClientHandler;
import io.fria.lilo.subscription.SubscriptionRetriever;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.reactivestreams.Publisher;
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
  private final LiloSubscriptionDefaultClientHandler liloHandler =
      new LiloSubscriptionDefaultClientHandler();

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

    final Sinks.Many<Object> sink = Sinks.many().unicast().onBackpressureBuffer();

    try {
      final WebSocketHttpHeaders httpHeaders = new WebSocketHttpHeaders();
      httpHeaders.add("Sec-WebSocket-Protocol", "graphql-transport-ws");
      final WebSocketSession session =
          this.webSocketClient
              .execute(
                  new AbstractWebSocketHandler() {
                    @Override
                    protected void handleTextMessage(
                        final @NotNull WebSocketSession session, final @NotNull TextMessage message)
                        throws Exception {

                      final GraphQLSubscriptionMessage subscriptionMessage =
                          SubscriptionRetrieverImpl.this.liloHandler.convertToSubscriptionMessage(
                              message.getPayload());

                      if ("connection_ack".equals(subscriptionMessage.getType())) {
                        SubscriptionRetrieverImpl.this.liloHandler.sendQuery(
                            query, m -> session.sendMessage(new TextMessage(m)));
                      } else if ("next".equals(subscriptionMessage.getType())) {
                        sink.tryEmitNext(subscriptionMessage.getPayload());
                      }
                    }
                  },
                  httpHeaders,
                  new URI(this.subscriptionWsUrl))
              .get();
      this.liloHandler.startHandShaking(m -> session.sendMessage(new TextMessage(m)));
    } catch (final URISyntaxException | InterruptedException | ExecutionException | IOException e) {
      throw new RuntimeException(e);
    }

    return sink.asFlux();
  }
}
