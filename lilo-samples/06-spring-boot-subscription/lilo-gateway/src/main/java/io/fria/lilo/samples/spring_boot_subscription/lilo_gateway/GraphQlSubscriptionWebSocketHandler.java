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
import io.fria.lilo.GraphQLRequest;
import io.fria.lilo.JsonUtils;
import io.fria.lilo.Lilo;
import io.fria.lilo.subscription.GraphQLSubscriptionMessage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
public class GraphQlSubscriptionWebSocketHandler extends AbstractWebSocketHandler {

  private final Lilo lilo;
  private @NotNull final Map<String, Sinks.Many<Object>> connectionPublishers =
      new ConcurrentHashMap<>();

  public GraphQlSubscriptionWebSocketHandler(final @NotNull Lilo lilo) {
    this.lilo = lilo;
  }

  @Override
  protected void handleTextMessage(
      final @NotNull WebSocketSession session, final @NotNull TextMessage message)
      throws IOException {

    // --- Request

    final String wsMessage = message.getPayload();

    final Optional<GraphQLSubscriptionMessage> requestOptional =
        JsonUtils.toObj(wsMessage, GraphQLSubscriptionMessage.class);

    if (requestOptional.isEmpty()) {
      return;
    }

    final GraphQLSubscriptionMessage request = requestOptional.get();

    if ("connection_init".equals(request.getType())) {
      final GraphQLSubscriptionMessage response = new GraphQLSubscriptionMessage();
      response.setType("connection_ack");
      response.setPayload(new HashMap<>());
      this.sendResponse(session, response);
    } else if ("subscribe".equals(request.getType())) {
      final Optional<GraphQLRequest> graphQLRequest =
          JsonUtils.toObj(JsonUtils.toStr(request.getPayload()), GraphQLRequest.class);

      if (graphQLRequest.isEmpty()) {
        return;
      }

      final GraphQLSubscriptionMessage response = new GraphQLSubscriptionMessage();
      response.setId(request.getId());
      response.setType("next");

      final Map<String, Object> stitchResult =
          this.lilo.stitch(graphQLRequest.get().toExecutionInput()).toSpecification();
      final SubscriptionPublisher publisher = (SubscriptionPublisher) stitchResult.get("data");

      this.connectionPublishers.put(
          session.getId(), (Sinks.Many<Object>) publisher.getUpstreamPublisher());

      final HashMap<Object, Object> payloadMap = new HashMap<>();
      payloadMap.put("data", stitchResult);
      response.setPayload(payloadMap);

      this.sendResponse(session, response);
    } else if ("complete".equals(request.getType())) {
      try {
        session.close(CloseStatus.NORMAL);

        if (this.connectionPublishers.containsKey(session.getId())) {
          this.connectionPublishers.get(session.getId()).tryEmitComplete();
          this.connectionPublishers.remove(session.getId());
        }
      } catch (final IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private void sendResponse(
      final @NotNull WebSocketSession session, final @NotNull GraphQLSubscriptionMessage response)
      throws IOException {

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
          },
          throwable -> {},
          () -> {
            try {
              session.close(CloseStatus.NORMAL);
            } catch (final IOException e) {
              throw new RuntimeException(e);
            }
          });
    } else {
      session.sendMessage(new TextMessage(JsonUtils.toStr(response)));
    }
  }
}
