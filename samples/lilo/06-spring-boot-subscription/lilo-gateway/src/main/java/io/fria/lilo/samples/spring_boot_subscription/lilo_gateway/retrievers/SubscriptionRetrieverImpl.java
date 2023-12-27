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
package io.fria.lilo.samples.spring_boot_subscription.lilo_gateway.retrievers;

import io.fria.lilo.GraphQLQuery;
import io.fria.lilo.LiloContext;
import io.fria.lilo.SchemaSource;
import io.fria.lilo.samples.spring_boot_subscription.lilo_gateway.handlers.SourceWebSocketHandler;
import io.fria.lilo.subscription.SubscriptionRetriever;
import io.fria.lilo.subscription.SubscriptionSourcePublisher;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

public class SubscriptionRetrieverImpl implements SubscriptionRetriever {

  private final @NotNull String subscriptionWsUrl;
  private final @NotNull WebSocketClient webSocketClient;

  public SubscriptionRetrieverImpl(final @NotNull String subscriptionWsUrl) {
    this.subscriptionWsUrl = subscriptionWsUrl;
    this.webSocketClient = new StandardWebSocketClient();
  }

  @Override
  public void sendQuery(
      @NotNull final LiloContext liloContext,
      @NotNull final SchemaSource schemaSource,
      @NotNull final GraphQLQuery query,
      @NotNull final SubscriptionSourcePublisher publisher,
      @Nullable final Object localContext) {

    try {
      final WebSocketHttpHeaders httpHeaders = new WebSocketHttpHeaders();
      httpHeaders.add("Sec-WebSocket-Protocol", "graphql-transport-ws");
      this.webSocketClient
          .execute(
              new SourceWebSocketHandler(query, publisher),
              httpHeaders,
              new URI(this.subscriptionWsUrl))
          .get();
    } catch (final ExecutionException e) {
      // pass
    } catch (final URISyntaxException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
