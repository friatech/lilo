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

import static io.fria.lilo.subscription.GatewayMessages.TOO_MANY_OPERATIONS;
import static io.fria.lilo.subscription.GatewayMessages.errors;
import static io.fria.lilo.subscription.GatewayMessages.trySendError;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The gateway's client sessions: the connection context of each open session, its registered
 * operations, the stitches still running for them, and the per-session operation cap.
 */
final class GatewaySessionRegistry {

  // Logged under the handler's name: the gateway's classes are one component to an operator
  private static final Logger LOG = LoggerFactory.getLogger(SubscriptionGatewayHandler.class);

  private final int maxOperationsPerSession;

  /** session id -> the session's operations and in-flight stitches */
  private final @NotNull Map<String, SessionState> sessionOperations = new ConcurrentHashMap<>();

  /** session id -> what the client sent at connection_init; present once the session is open */
  private final @NotNull Map<String, SubscriptionConnectionContext> connections =
      new ConcurrentHashMap<>();

  GatewaySessionRegistry(final int maxOperationsPerSession) {
    this.maxOperationsPerSession = maxOperationsPerSession;
  }

  /** Returns whether any state is held for the session. */
  boolean tracksSession(final @NotNull String sessionId) {
    return this.sessionOperations.containsKey(sessionId);
  }

  /** Returns whether a connection context is held for the session. */
  boolean tracksConnection(final @NotNull String sessionId) {
    return this.connections.containsKey(sessionId);
  }

  /** Returns the session's connection context, or null before its connection_init. */
  @Nullable
  SubscriptionConnectionContext connection(final @NotNull String sessionId) {
    return this.connections.get(sessionId);
  }

  /**
   * Keeps the connection context of a session.
   *
   * @return false if the session already has one; nothing is changed then
   */
  boolean addConnection(
      final @NotNull String sessionId, final @NotNull SubscriptionConnectionContext context) {
    return this.connections.putIfAbsent(sessionId, context) == null;
  }

  /** Drops the connection context, if it is still the given one. */
  void removeConnection(
      final @NotNull String sessionId, final @NotNull SubscriptionConnectionContext context) {
    this.connections.remove(sessionId, context);
  }

  /** Returns whether the session has a registered operation with the id. */
  boolean hasOperation(final @NotNull String sessionId, final @NotNull String operationId) {
    final SessionState active = this.sessionOperations.get(sessionId);
    return active != null && active.operations.containsKey(operationId);
  }

  /**
   * Registers a new operation of the session, before it is stitched. A request beyond the cap is
   * answered with an {@code error} message; a lost race or a session closed meanwhile closes the
   * session.
   *
   * @return the registered operation, or null if it was rejected; the caller sends nothing more
   */
  @Nullable
  GatewayOperation register(
      final @NotNull WebSocketSessionWrapper session, final @NotNull String operationId) {

    final SessionState state =
        this.sessionOperations.computeIfAbsent(session.getId(), id -> new SessionState());
    final Map<String, GatewayOperation> operations = state.operations;
    final GatewayOperation operation = new GatewayOperation(operationId, session, state);

    // A session's messages arrive one after the other, so plain checks are enough here. A stitch
    // still running for an operation the client already completed holds a pool thread, so it
    // counts as well: otherwise subscribe followed by complete would bypass the cap. Every
    // in-flight stitch belongs to a registered operation unless that was completed, so the larger
    // of the two numbers is the session's real usage; their sum would count most operations twice.
    if (Math.max(operations.size(), state.inFlight.get()) >= this.maxOperationsPerSession) {
      trySendError(session, operationId, errors(TOO_MANY_OPERATIONS));
      return null;
    }

    if (operations.putIfAbsent(operationId, operation) != null) {
      // Lost a race against a concurrent subscribe with the same id
      this.closeSession(session);
      return null;
    }

    if (this.isDetached(operation)) {
      // The session was closed meanwhile. Its close may already have drained this map before the
      // operation was added, so remove it here rather than rely on closeSession finding it.
      operation.release();
      this.closeSession(session);
      return null;
    }

    return operation;
  }

  /** Whether the operation's session closed or its operation map is no longer the live one. */
  boolean isDetached(final @NotNull GatewayOperation operation) {
    final WebSocketSessionWrapper session = operation.session();
    return !session.isOpen() || this.sessionOperations.get(session.getId()) != operation.state();
  }

  void closeSession(final @NotNull WebSocketSessionWrapper session) {

    try {
      if (session.isOpen()) {
        session.close();
      }
    } catch (final RuntimeException e) {
      // The operations below must be released all the same
      LOG.debug("Could not close session {}", session.getId(), e);
    }

    this.connections.remove(session.getId());
    final SessionState state = this.sessionOperations.remove(session.getId());

    if (state == null) {
      return;
    }

    final Map<String, GatewayOperation> operations = state.operations;

    for (final String operationId : operations.keySet()) {
      final GatewayOperation operation = operations.remove(operationId);
      if (operation != null) {
        operation.cancel();
      }
    }
  }

  void cancelOperation(final @NotNull String sessionId, final @NotNull String operationId) {

    final SessionState state = this.sessionOperations.get(sessionId);

    if (state == null) {
      return;
    }

    // Whoever removes the operation owns its cleanup, so a late complete or close is a no-op
    final GatewayOperation operation = state.operations.remove(operationId);

    if (operation != null) {
      operation.cancel();
    }
  }

  /** Per-session state: the registered operations and the stitches still running for them. */
  static final class SessionState {

    /** operation id -> operation */
    final @NotNull Map<String, GatewayOperation> operations = new ConcurrentHashMap<>();

    /** Stitches submitted and not yet finished, including those of completed operations. */
    final @NotNull AtomicInteger inFlight = new AtomicInteger();
  }
}
