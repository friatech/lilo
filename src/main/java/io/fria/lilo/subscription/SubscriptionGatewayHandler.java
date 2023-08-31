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
package io.fria.lilo.subscription;

import graphql.execution.reactive.SubscriptionPublisher;
import io.fria.lilo.GraphQLRequest;
import io.fria.lilo.JsonUtils;
import io.fria.lilo.Lilo;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

public class SubscriptionGatewayHandler {

  private final @NotNull Lilo lilo;

  public SubscriptionGatewayHandler(final @NotNull Lilo lilo) {
    this.lilo = Objects.requireNonNull(lilo);
  }

  public void handleMessage(
      final @NotNull SubscriptionSessionAdapter sessionAdapter, final @NotNull String wsMessage) {

    // > HTTP GET /graphql
    // < HTTP 1.1 101 Switching protocols
    // > {"type":"connection_init","payload":{}}
    // < {"id":null,"type":"connection_ack","payload":{}}
    // >
    // {"id":"ad3cc738-116a-4228-8337-d4b985378890","type":"subscribe","payload":{"query":"subscription {\n  greeting1Subscription\n}","variables":{}}}
    // <
    // {"id":"ad3cc738-116a-4228-8337-d4b985378890","type":"next","payload":{"data":{"greeting1Subscription":"Hi!"}}}
    // <
    // {"id":"ad3cc738-116a-4228-8337-d4b985378890","type":"next","payload":{"data":{"greeting1Subscription":"Bonjour!"}}}
    // <
    // {"id":"ad3cc738-116a-4228-8337-d4b985378890","type":"next","payload":{"data":{"greeting1Subscription":"Hola!"}}}

    final Optional<SubscriptionMessage> requestOptional =
        JsonUtils.toObj(wsMessage, SubscriptionMessage.class);

    if (requestOptional.isEmpty()) {
      return;
    }

    final SubscriptionMessage request = requestOptional.get();

    if ("connection_init".equals(request.getType())) {
      final var response =
          SubscriptionMessage.builder().type("connection_ack").payload(new HashMap<>()).build();
      sessionAdapter.sendMessage(JsonUtils.toStr(response));
    } else if ("subscribe".equals(request.getType())) {
      final GraphQLRequest graphQLRequest = this.payloadToQuery(request.getPayload());
      final Map<String, Object> stitchResult =
          this.lilo.stitch(graphQLRequest.toExecutionInput()).toSpecification();

      final SubscriptionPublisher publisher = (SubscriptionPublisher) stitchResult.get("data");
      sessionAdapter.subscribe(
          publisher.getUpstreamPublisher(), Objects.requireNonNull(request.getId()));
    } else if ("complete".equals(request.getType())) {
      sessionAdapter.closeSession();
    }
  }

  private GraphQLRequest payloadToQuery(final Object payload) {

    final Optional<GraphQLRequest> graphQLRequest =
        JsonUtils.toObj(JsonUtils.toStr(payload), GraphQLRequest.class);

    if (graphQLRequest.isEmpty()) {
      return null;
    }

    return graphQLRequest.get();
  }
}
