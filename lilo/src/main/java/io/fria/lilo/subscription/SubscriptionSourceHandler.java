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
package io.fria.lilo.subscription;

import static io.fria.lilo.subscription.SubscriptionMessageType.complete;
import static io.fria.lilo.subscription.SubscriptionMessageType.connection_init;
import static io.fria.lilo.subscription.SubscriptionMessageType.error;
import static io.fria.lilo.subscription.SubscriptionMessageType.next;
import static io.fria.lilo.subscription.SubscriptionMessageType.ping;
import static io.fria.lilo.subscription.SubscriptionMessageType.pong;
import static io.fria.lilo.subscription.SubscriptionMessageType.subscribe;

import io.fria.lilo.GraphQLQuery;
import io.fria.lilo.JsonUtils;
import java.util.HashMap;
import java.util.Objects;
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
      final @NotNull WebSocketSessionWrapper session, final @NotNull String message) {

    Objects.requireNonNull(session);

    final SubscriptionMessage request;
    try {
      request = JsonUtils.toObj(message, SubscriptionMessage.class).orElse(null);
    } catch (final IllegalArgumentException e) {
      // Invalid or unknown message from the source: give up on this connection
      this.closeSource(session);
      return;
    }

    if (request == null) {
      this.closeSource(session);
      return;
    }

    final SubscriptionMessageType requestType = request.getType();

    if (SubscriptionMessageType.connection_ack == requestType) {
      final SubscriptionMessage response = new SubscriptionMessage();
      response.setId(UUID.randomUUID().toString());
      response.setType(subscribe);

      response.setPayload(Objects.requireNonNull(this.query.getRequest()));
      Objects.requireNonNull(session).send(JsonUtils.toStr(response));
    } else if (next == requestType) {
      this.publisher.send(Objects.requireNonNull(request.getPayload()));

      this.publisher
          .getFlux()
          .doOnComplete(
              () -> {
                final SubscriptionMessage completeMessage = new SubscriptionMessage();
                completeMessage.setId(request.getId());
                completeMessage.setType(complete);

                Objects.requireNonNull(session).send(JsonUtils.toStr(completeMessage));
              })
          .subscribe();
    } else if (ping == requestType) {
      // Echo the ping payload back, if any
      final SubscriptionMessage response = new SubscriptionMessage();
      response.setType(pong);
      response.setPayload(request.getPayload());
      session.send(JsonUtils.toStr(response));
    } else if (complete == requestType) {
      // Source finished the operation
      this.publisher.close();
    } else if (error == requestType) {
      this.publisher.error(
          new IllegalStateException("Source error: " + JsonUtils.toStr(request.getPayload())));
    }
  }

  private void closeSource(final @NotNull WebSocketSessionWrapper session) {
    if (session.isOpen()) {
      session.close();
    }
    this.publisher.close();
  }

  public void handleSessionClose(final @Nullable WebSocketSessionWrapper session) {
    this.publisher.close();
  }

  public void handleSessionStart(final @NotNull WebSocketSessionWrapper session) {

    final SubscriptionMessage initMessage = new SubscriptionMessage();
    initMessage.setType(connection_init);
    initMessage.setPayload(new HashMap<>());

    Objects.requireNonNull(session).send(JsonUtils.toStr(initMessage));
  }
}
