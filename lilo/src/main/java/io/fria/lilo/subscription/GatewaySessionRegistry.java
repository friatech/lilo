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
   * Registers a new operation of the session, before it is stitched. Nothing is sent here: the
   * caller answers a rejected request as the returned registration says.
   *
   * @return the registered operation, or why it was not registered
   */
  @NotNull
  Registration register(
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
    if (Math.max(operations.size(), state.stitchesInFlight()) >= this.maxOperationsPerSession) {
      return Registration.rejected(Rejection.TOO_MANY_OPERATIONS);
    }

    if (operations.putIfAbsent(operationId, operation) != null) {
      // Lost a race against a concurrent subscribe with the same id
      return Registration.rejected(Rejection.CLOSE_SESSION);
    }

    if (this.isDetached(operation)) {
      // The session was closed meanwhile. Its close may already have drained this map before the
      // operation was added, so remove it here rather than rely on closeSession finding it.
      operation.release();
      return Registration.rejected(Rejection.CLOSE_SESSION);
    }

    return new Registration(operation, null);
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

  /** Why a subscribe was not registered. */
  enum Rejection {
    /** The session is at its cap: answer with an {@code error} message, the session stays open. */
    TOO_MANY_OPERATIONS,
    /** A lost race against a subscribe with the same id, or a session closed meanwhile. */
    CLOSE_SESSION
  }

  /** Outcome of {@link #register}: either the registered operation or why there is none. */
  static final class Registration {

    private final @Nullable GatewayOperation operation;
    private final @Nullable Rejection rejection;

    private Registration(
        final @Nullable GatewayOperation operation, final @Nullable Rejection rejection) {
      this.operation = operation;
      this.rejection = rejection;
    }

    private static @NotNull Registration rejected(final @NotNull Rejection rejection) {
      return new Registration(null, rejection);
    }

    /** The registered operation, or null if the subscribe was rejected. */
    @Nullable
    GatewayOperation operation() {
      return this.operation;
    }

    /** Why the subscribe was rejected, or null if it was registered. */
    @Nullable
    Rejection rejection() {
      return this.rejection;
    }
  }

  /** Per-session state: the registered operations and the stitches still running for them. */
  static final class SessionState {

    /** operation id -> operation */
    final @NotNull Map<String, GatewayOperation> operations = new ConcurrentHashMap<>();

    /** Stitches submitted and not yet finished, including those of completed operations. */
    private final @NotNull AtomicInteger inFlight = new AtomicInteger();

    /** Counts a stitch before it is submitted. */
    void stitchSubmitted() {
      this.inFlight.incrementAndGet();
    }

    /** Releases a counted stitch: exactly once, when it finished or was never started. */
    void stitchFinished() {
      this.inFlight.decrementAndGet();
    }

    int stitchesInFlight() {
      return this.inFlight.get();
    }
  }
}
