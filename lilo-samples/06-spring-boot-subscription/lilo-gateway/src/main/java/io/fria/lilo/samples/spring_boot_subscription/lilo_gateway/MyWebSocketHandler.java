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

import graphql.execution.reactive.SubscriptionPublisher;
import io.fria.lilo.JsonUtils;
import io.fria.lilo.Lilo;
import io.fria.lilo.subscription.GraphQLSubscriptionMessage;
import io.fria.lilo.subscription.LiloSubscriptionDefaultServerHandler;
import java.io.IOException;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import reactor.core.publisher.Flux;

@Component
public class MyWebSocketHandler extends AbstractWebSocketHandler {

  private final Lilo lilo;
  private final LiloSubscriptionDefaultServerHandler defaultHandler;

  public MyWebSocketHandler(final @NotNull Lilo lilo) {
    this.lilo = lilo;
    this.defaultHandler = new LiloSubscriptionDefaultServerHandler(this.lilo);
  }

  @Override
  protected void handleTextMessage(
      final @NotNull WebSocketSession session, final @NotNull TextMessage message)
      throws IOException {

    final GraphQLSubscriptionMessage response =
        this.defaultHandler.createResponse(message.getPayload());

    if (response == null) {
      throw new IllegalArgumentException("Incorrect message content");
    }

    if ("next".equals(response.getType())) {
      final Map<String, Object> payload = (Map<String, Object>) response.getPayload();
      final Map<String, Object> data = (Map<String, Object>) payload.get("data");
      // TODO: data inside data. that's suspicious

      final SubscriptionPublisher publisher = (SubscriptionPublisher) data.get("data");
      final Flux<Object> upstreamPublisher = (Flux<Object>) publisher.getUpstreamPublisher();
      upstreamPublisher.subscribe(
          o -> {
            final GraphQLSubscriptionMessage subscriptionMessage = new GraphQLSubscriptionMessage();
            subscriptionMessage.setId(response.getId());
            subscriptionMessage.setType("next");
            subscriptionMessage.setPayload(o);
            try {
              session.sendMessage(new TextMessage(JsonUtils.toStr(subscriptionMessage)));
            } catch (final IOException e) {
              throw new RuntimeException(e);
            }
          });
    } else {
      session.sendMessage(new TextMessage(JsonUtils.toStr(response)));
    }
  }
}
