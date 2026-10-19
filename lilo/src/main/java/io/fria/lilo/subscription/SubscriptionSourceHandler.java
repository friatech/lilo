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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * SubscriptionSourceHandler drives one subscription operation against a remote source over its own
 * websocket session, and feeds what the source sends into a {@link SubscriptionSourcePublisher}.
 *
 * <p>The handler watches the publisher's {@link SubscriptionSourcePublisher#getTermination()
 * termination} once, when the source acknowledges the connection. When the publisher ends because
 * the gateway no longer needs the operation, the source is sent {@code complete}; when the source
 * itself ended it (with {@code complete} or {@code error}), it is not told again. Either way the
 * source session is closed afterwards, as it serves only this operation.
 */
public class SubscriptionSourceHandler {

  private final @NotNull GraphQLQuery query;
  private final @NotNull SubscriptionSourcePublisher publisher;
  private final @NotNull String operationId = UUID.randomUUID().toString();
  private final @NotNull AtomicBoolean subscribed = new AtomicBoolean();
  private final @NotNull Map<String, Object> connectionInitPayload;

  /** Set once the source ended the operation itself, so it is not sent a complete for it. */
  private volatile boolean endedBySource;

  /**
   * Creates a handler whose {@code connection_init} carries an empty payload.
   *
   * @param query the subscription to run against the source
   * @param publisher publisher that receives what the source sends
   */
  public SubscriptionSourceHandler(
      final @NotNull GraphQLQuery query, final @NotNull SubscriptionSourcePublisher publisher) {
    this(query, publisher, Map.of());
  }

  /**
   * Creates a handler whose {@code connection_init} carries the given payload, e.g. the credentials
   * the source expects. Use {@link SubscriptionConnectionContext} to forward what the gateway's
   * client sent.
   *
   * @param query the subscription to run against the source
   * @param publisher publisher that receives what the source sends
   * @param connectionInitPayload payload of the {@code connection_init} sent to the source; copied
   */
  public SubscriptionSourceHandler(
      final @NotNull GraphQLQuery query,
      final @NotNull SubscriptionSourcePublisher publisher,
      final @NotNull Map<String, Object> connectionInitPayload) {
    this.query = query;
    this.publisher = publisher;
    // LinkedHashMap rather than Map.copyOf: a JSON payload may hold null values
    this.connectionInitPayload =
        Collections.unmodifiableMap(new LinkedHashMap<>(connectionInitPayload));
  }

  public void handleMessage(
      final @NotNull WebSocketSessionWrapper session, final @NotNull String message) {

    Objects.requireNonNull(session);

    final SubscriptionMessage request;
    try {
      request = JsonUtils.toObj(message, SubscriptionMessage.class).orElse(null);
    } catch (final IllegalArgumentException e) {
      // Invalid or unknown message from the source: give up on this connection
      this.closeSource(session, "Subscription source sent an invalid message");
      return;
    }

    if (request == null || request.getType() == null) {
      this.closeSource(session, "Subscription source sent an invalid message");
      return;
    }

    final SubscriptionMessageType requestType = request.getType();

    if (SubscriptionMessageType.connection_ack == requestType) {
      this.subscribe(session);
    } else if (next == requestType) {
      final Object payload = request.getPayload();

      if (payload == null) {
        this.closeSource(session, "Subscription source sent a next message without payload");
        return;
      }

      this.publisher.send(payload);
    } else if (ping == requestType) {
      // Echo the ping payload back, if any
      final SubscriptionMessage response = new SubscriptionMessage();
      response.setType(pong);
      response.setPayload(request.getPayload());
      session.send(JsonUtils.toStr(response));
    } else if (complete == requestType) {
      // Source finished the operation
      this.endedBySource = true;
      this.publisher.close();
    } else if (error == requestType) {
      this.endedBySource = true;
      this.publisher.error(SubscriptionSourceException.fromPayload(request.getPayload()));
    }
  }

  private void subscribe(final @NotNull WebSocketSessionWrapper session) {

    if (!this.subscribed.compareAndSet(false, true)) {
      // A repeated connection_ack starts nothing new
      return;
    }

    final SubscriptionMessage response = new SubscriptionMessage();
    response.setId(this.operationId);
    response.setType(subscribe);
    response.setPayload(Objects.requireNonNull(this.query.getRequest()));
    session.send(JsonUtils.toStr(response));

    // Fires once, immediately if the gateway already gave up on the operation
    this.publisher.getTermination().doOnTerminate(() -> this.finish(session)).subscribe();
  }

  private void finish(final @NotNull WebSocketSessionWrapper session) {

    if (!this.endedBySource && session.isOpen()) {
      final SubscriptionMessage completeMessage = new SubscriptionMessage();
      completeMessage.setId(this.operationId);
      completeMessage.setType(complete);
      session.send(JsonUtils.toStr(completeMessage));
    }

    if (session.isOpen()) {
      session.close();
    }
  }

  private void closeSource(
      final @NotNull WebSocketSessionWrapper session, final @NotNull String reason) {
    this.endedBySource = true;
    if (session.isOpen()) {
      session.close();
    }
    // A protocol violation is a failure, not a normal end of the operation
    this.publisher.error(SubscriptionSourceException.protocolViolation(reason));
  }

  public void handleSessionClose(final @Nullable WebSocketSessionWrapper session) {
    this.endedBySource = true;
    this.publisher.close();
  }

  public void handleSessionStart(final @NotNull WebSocketSessionWrapper session) {

    final SubscriptionMessage initMessage = new SubscriptionMessage();
    initMessage.setType(connection_init);
    initMessage.setPayload(this.connectionInitPayload);

    Objects.requireNonNull(session).send(JsonUtils.toStr(initMessage));
  }
}
