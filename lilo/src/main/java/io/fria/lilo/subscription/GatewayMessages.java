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
import static io.fria.lilo.subscription.SubscriptionMessageType.connection_ack;
import static io.fria.lilo.subscription.SubscriptionMessageType.error;
import static io.fria.lilo.subscription.SubscriptionMessageType.next;
import static io.fria.lilo.subscription.SubscriptionMessageType.pong;

import io.fria.lilo.JsonUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The messages {@link SubscriptionGatewayHandler} sends to a client, the error texts it uses and
 * how it logs a failed operation.
 */
final class GatewayMessages {

  static final String INVALID_PAYLOAD = "Subscribe payload is not a valid GraphQL request";
  static final String MISSING_QUERY = "Subscribe payload has no query";
  static final String EXECUTION_FAILED = "Subscription could not be executed";
  static final String NOT_A_SUBSCRIPTION = "Operation did not produce a subscription";
  static final String TOO_MANY_REQUESTS = "Too many concurrent subscription requests";
  static final String TOO_MANY_OPERATIONS = "Too many subscriptions on this connection";

  // Logged under the handler's name: the gateway's classes are one component to an operator
  private static final Logger LOG = LoggerFactory.getLogger(SubscriptionGatewayHandler.class);

  private GatewayMessages() {}

  static @NotNull String safe(final @Nullable String text) {
    return SubscriptionSourceException.bound(String.valueOf(text));
  }

  static @NotNull List<Map<String, Object>> errors(final @NotNull String message) {
    return List.of(Map.of("message", message));
  }

  static @NotNull List<Map<String, Object>> errorsOf(final @NotNull Throwable throwable) {
    if (throwable instanceof SubscriptionSourceException) {
      return ((SubscriptionSourceException) throwable).getErrors();
    }
    return SubscriptionSourceException.genericErrors();
  }

  static void sendConnectionAck(final @NotNull WebSocketSessionWrapper session) {
    final var response =
        SubscriptionMessage.builder().type(connection_ack).payload(new HashMap<>()).build();
    session.send(JsonUtils.toStr(response));
  }

  static void sendPong(
      final @NotNull WebSocketSessionWrapper session, final @Nullable Object payload) {
    // Echo the ping payload back, if any
    final SubscriptionMessage response = new SubscriptionMessage();
    response.setType(pong);
    response.setPayload(payload);
    session.send(JsonUtils.toStr(response));
  }

  static void sendNext(
      final @NotNull WebSocketSessionWrapper session,
      final @NotNull String operationId,
      final @NotNull Object payload) {

    final var subscriptionMessage =
        SubscriptionMessage.builder().id(operationId).type(next).payload(payload).build();
    session.send(JsonUtils.toStr(subscriptionMessage));
  }

  static void trySendComplete(
      final @NotNull WebSocketSessionWrapper session, final @NotNull String operationId) {

    final SubscriptionMessage completeMessage = new SubscriptionMessage();
    completeMessage.setId(operationId);
    completeMessage.setType(complete);

    try {
      session.send(JsonUtils.toStr(completeMessage));
    } catch (final RuntimeException sendFailure) {
      LOG.debug("Could not send complete for {}", safe(operationId), sendFailure);
    }
  }

  static void trySendError(
      final @NotNull WebSocketSessionWrapper session,
      final @NotNull String operationId,
      final @NotNull List<Map<String, Object>> errors) {
    try {
      sendError(session, operationId, errors);
    } catch (final RuntimeException e) {
      LOG.debug("Could not send error for {}", safe(operationId), e);
    }
  }

  /**
   * Logs an operation that failed once it ran. An error the source reported (e.g. an authorization
   * failure) is forwarded to the client and is logged at DEBUG only. Anything else, a protocol
   * violation, a source socket that closed (e.g. with 1009) or a failure outside the core such as
   * an oversized source message, reaches the client only as a generic error, so its cause is logged
   * at WARN; this is the only place it is logged.
   */
  static void logFailure(final @NotNull String operationId, final @NotNull Throwable e) {
    if (e instanceof SubscriptionSourceException
        && ((SubscriptionSourceException) e).isReportedBySource()) {
      LOG.debug("Subscription {} failed at its source: {}", safe(operationId), safe(e.toString()));
    } else {
      LOG.warn("Subscription {} failed: {}", safe(operationId), safe(String.valueOf(e)));
    }
    LOG.debug("Subscription failure", e);
  }

  private static void sendError(
      final @NotNull WebSocketSessionWrapper session,
      final @NotNull String operationId,
      final @NotNull List<Map<String, Object>> errors) {

    final SubscriptionMessage errorMessage =
        SubscriptionMessage.builder().id(operationId).type(error).payload(errors).build();
    session.send(JsonUtils.toStr(errorMessage));
  }
}
