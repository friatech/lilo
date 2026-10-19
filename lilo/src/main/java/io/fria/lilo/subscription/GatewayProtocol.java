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

import static io.fria.lilo.subscription.GatewayMessages.INVALID_PAYLOAD;
import static io.fria.lilo.subscription.GatewayMessages.MISSING_QUERY;
import static io.fria.lilo.subscription.GatewayMessages.TOO_MANY_OPERATIONS;
import static io.fria.lilo.subscription.GatewayMessages.errors;
import static io.fria.lilo.subscription.GatewayMessages.sendConnectionAck;
import static io.fria.lilo.subscription.GatewayMessages.sendPong;
import static io.fria.lilo.subscription.GatewayMessages.trySendError;

import graphql.ExecutionInput;
import io.fria.lilo.GraphQLRequest;
import io.fria.lilo.JsonUtils;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The graphql-transport-ws side of {@link SubscriptionGatewayHandler}: parses a client message,
 * enforces the protocol's rules and dispatches it to the session registry or the stitcher.
 */
final class GatewayProtocol {

  private static final CompletableFuture<Void> DONE = CompletableFuture.completedFuture(null);

  // Logged under the handler's name: the gateway's classes are one component to an operator
  private static final Logger LOG = LoggerFactory.getLogger(SubscriptionGatewayHandler.class);

  private final @NotNull GatewaySessionRegistry registry;
  private final @NotNull SubscriptionStitcher stitcher;

  GatewayProtocol(
      final @NotNull GatewaySessionRegistry registry,
      final @NotNull SubscriptionStitcher stitcher) {
    this.registry = registry;
    this.stitcher = stitcher;
  }

  /**
   * Handles a client message.
   *
   * @return completes once the message is handled; for a subscribe, once its operation is running
   *     or was rejected
   */
  @NotNull
  CompletableFuture<Void> handle(
      final @NotNull WebSocketSessionWrapper session, final @NotNull String message) {

    Objects.requireNonNull(session);

    final SubscriptionMessage request;
    try {
      request = JsonUtils.toObj(message, SubscriptionMessage.class).orElse(null);
    } catch (final IllegalArgumentException e) {
      // graphql-transport-ws: an invalid or unknown message closes the connection
      this.registry.closeSession(session);
      return DONE;
    }

    final SubscriptionMessageType requestType = request == null ? null : request.getType();

    if (requestType == null) {
      this.registry.closeSession(session);
      return DONE;
    }

    switch (requestType) {
      case connection_init:
        this.handleConnectionInit(session, request);
        break;
      case ping:
        sendPong(session, request.getPayload());
        break;
      case subscribe:
        return this.handleSubscribe(session, request);
      case complete:
        // The client stops a single operation; the connection and other operations stay
        final String operationId = request.getId();
        if (operationId != null) {
          this.registry.cancelOperation(session.getId(), operationId);
        }
        break;
      case pong:
        // pong is a one-way heartbeat answer
        break;
      default:
        // Server-to-client only types (connection_ack, next, error) are invalid from a client
        this.registry.closeSession(session);
        break;
    }

    return DONE;
  }

  private void handleConnectionInit(
      final @NotNull WebSocketSessionWrapper session, final @NotNull SubscriptionMessage request) {

    final SubscriptionConnectionContext context;
    try {
      context = SubscriptionConnectionContext.of(request.getPayload(), session.getAttributes());
    } catch (final RuntimeException e) {
      // e.g. attributes the transport cannot read; the values are not logged, they may be secret
      LOG.debug("Could not read the connection context of session {}", session.getId());
      this.registry.closeSession(session);
      return;
    }

    if (!this.registry.addConnection(session.getId(), context)) {
      // graphql-transport-ws: a second connection_init closes the connection (4429 Too many
      // initialisation requests)
      this.registry.closeSession(session);
      return;
    }

    if (!session.isOpen()) {
      // Closed meanwhile: its close may already have run, so do not keep the context
      this.registry.removeConnection(session.getId(), context);
      return;
    }

    sendConnectionAck(session);
  }

  private @NotNull CompletableFuture<Void> handleSubscribe(
      final @NotNull WebSocketSessionWrapper session, final @NotNull SubscriptionMessage request) {

    final SubscriptionConnectionContext context = this.registry.connection(session.getId());

    if (context == null) {
      // graphql-transport-ws: a subscribe before connection_init closes the connection (4401
      // Unauthorized)
      this.registry.closeSession(session);
      return DONE;
    }

    final String operationId = request.getId();

    if (operationId == null) {
      // graphql-transport-ws: a subscribe must carry an id
      this.registry.closeSession(session);
      return DONE;
    }

    if (this.registry.hasOperation(session.getId(), operationId)) {
      // graphql-transport-ws: a subscribe with an id that is already active closes the
      // connection (4409 Subscriber already exists)
      this.registry.closeSession(session);
      return DONE;
    }

    // Validated before any per-session state is created, so a rejected request leaves none
    final ExecutionInput executionInput =
        executionInputOf(session, operationId, request.getPayload(), context);

    if (executionInput == null) {
      return DONE;
    }

    final GatewaySessionRegistry.Registration registration =
        this.registry.register(session, operationId);
    final GatewayOperation operation = registration.operation();

    if (operation == null) {
      this.reject(session, operationId, registration.rejection());
      return DONE;
    }

    return this.stitcher.submit(operation, executionInput);
  }

  /** Answers a subscribe the registry did not register. */
  private void reject(
      final @NotNull WebSocketSessionWrapper session,
      final @NotNull String operationId,
      final @Nullable GatewaySessionRegistry.Rejection rejection) {

    if (rejection == GatewaySessionRegistry.Rejection.TOO_MANY_OPERATIONS) {
      trySendError(session, operationId, errors(TOO_MANY_OPERATIONS));
    } else {
      this.registry.closeSession(session);
    }
  }

  /**
   * Reads the subscribe payload as a GraphQL request.
   *
   * @return the request to stitch, or null if the payload is not one; the client was told so
   */
  private static @Nullable ExecutionInput executionInputOf(
      final @NotNull WebSocketSessionWrapper session,
      final @NotNull String operationId,
      final @Nullable Object payload,
      final @NotNull SubscriptionConnectionContext context) {

    if (!(payload instanceof Map)) {
      trySendError(session, operationId, errors(INVALID_PAYLOAD));
      return null;
    }

    if (!(((Map<?, ?>) payload).get("query") instanceof String)) {
      trySendError(session, operationId, errors(MISSING_QUERY));
      return null;
    }

    try {
      final GraphQLRequest graphQLRequest =
          JsonUtils.toObj(JsonUtils.toStr(payload), GraphQLRequest.class).orElse(null);

      if (graphQLRequest == null) {
        trySendError(session, operationId, errors(INVALID_PAYLOAD));
        return null;
      }

      // The context reaches the source's SubscriptionRetriever as its localContext
      return graphQLRequest.toExecutionInput(context);
    } catch (final RuntimeException e) {
      // e.g. a blank query or variables of the wrong shape
      trySendError(session, operationId, errors(INVALID_PAYLOAD));
      return null;
    }
  }
}
