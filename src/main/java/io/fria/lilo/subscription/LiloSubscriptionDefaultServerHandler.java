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

import io.fria.lilo.GraphQLRequest;
import io.fria.lilo.JsonUtils;
import io.fria.lilo.Lilo;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LiloSubscriptionDefaultServerHandler implements LiloSubscriptionHandler {

  private final Lilo lilo;

  public LiloSubscriptionDefaultServerHandler(final @NotNull Lilo lilo) {
    this.lilo = lilo;
  }

  @Override
  public @NotNull Object handleMessage(final @NotNull String wsMessage) {

    final GraphQLSubscriptionMessage response = this.createResponse(wsMessage);

    if (response == null) {
      throw new IllegalArgumentException("Incorrect message content");
    }

    if ("next".equals(response.getType())) {
      final Map<String, Object> payload = (Map<String, Object>) response.getPayload();
      final Map<String, Object> data = (Map<String, Object>) payload.get("data");
      // TODO: data inside data. that's suspicious
      return data.get("data");
    }

    return JsonUtils.toStr(response);
  }

  private @Nullable GraphQLSubscriptionMessage createResponse(final @NotNull String wsMessage) {
    final Optional<GraphQLSubscriptionMessage> requestOptional =
        JsonUtils.toObj(wsMessage, GraphQLSubscriptionMessage.class);

    if (requestOptional.isEmpty()) {
      return null;
    }

    final GraphQLSubscriptionMessage request = requestOptional.get();

    if ("connection_init".equals(request.getType())) {
      final GraphQLSubscriptionMessage response = new GraphQLSubscriptionMessage();
      response.setType("connection_ack");
      response.setPayload(new HashMap<>());
      return response;
    } else if ("subscribe".equals(request.getType())) {
      final Optional<GraphQLRequest> graphQLRequest =
          JsonUtils.toObj(JsonUtils.toStr(request.getPayload()), GraphQLRequest.class);

      if (graphQLRequest.isEmpty()) {
        return null;
      }

      final GraphQLSubscriptionMessage response = new GraphQLSubscriptionMessage();
      response.setId(request.getId());
      response.setType("next");

      final HashMap<Object, Object> payloadMap = new HashMap<>();
      payloadMap.put(
          "data", this.lilo.stitch(graphQLRequest.get().toExecutionInput()).toSpecification());
      response.setPayload(payloadMap);

      return response;
    }

    return null;
  }
}
