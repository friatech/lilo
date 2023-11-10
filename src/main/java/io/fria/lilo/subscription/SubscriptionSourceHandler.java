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

import io.fria.lilo.GraphQLQuery;
import io.fria.lilo.JsonUtils;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SubscriptionSourceHandler {

  private final @NotNull GraphQLQuery query;
  private final @NotNull SubscriptionSourcePublisher publisher;

  public SubscriptionSourceHandler(
      final @NotNull GraphQLQuery query, final @NotNull SubscriptionSourcePublisher publisher) {
    this.query = query;
    this.publisher = publisher;
  }

  public void handleMessage(
      final @Nullable WebSocketSessionWrapper session, final @NotNull String message) {

    Objects.requireNonNull(session);

    final Optional<SubscriptionMessage> requestOptional =
        JsonUtils.toObj(message, SubscriptionMessage.class);

    if (requestOptional.isEmpty()) {
      return;
    }

    final SubscriptionMessage request = requestOptional.get();

    if ("connection_ack".equals(request.getType())) {
      final SubscriptionMessage response = new SubscriptionMessage();
      response.setId(UUID.randomUUID().toString());
      response.setType("subscribe");

      response.setPayload(Objects.requireNonNull(this.query.getRequest()));
      Objects.requireNonNull(session).send(JsonUtils.toStr(response));
    } else if ("next".equals(request.getType())) {
      this.publisher.send(Objects.requireNonNull(request.getPayload()));

      this.publisher
          .getFlux()
          .doOnComplete(
              () -> {
                final SubscriptionMessage completeMessage = new SubscriptionMessage();
                completeMessage.setId(request.getId());
                completeMessage.setType("complete");
                completeMessage.setPayload(new HashMap<>());

                Objects.requireNonNull(session).send(JsonUtils.toStr(completeMessage));
              })
          .subscribe();
    }
  }

  public void handleSessionClose(final @Nullable WebSocketSessionWrapper session) {
    this.publisher.close();
  }

  public void handleSessionStart(final @Nullable WebSocketSessionWrapper session) {

    final SubscriptionMessage initMessage = new SubscriptionMessage();
    initMessage.setType("connection_init");
    initMessage.setPayload(new HashMap<>());

    Objects.requireNonNull(session).send(JsonUtils.toStr(initMessage));
  }
}
