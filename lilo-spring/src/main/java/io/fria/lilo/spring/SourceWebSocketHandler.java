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
package io.fria.lilo.spring;

import io.fria.lilo.GraphQLQuery;
import io.fria.lilo.subscription.SubscriptionSourceHandler;
import io.fria.lilo.subscription.SubscriptionSourcePublisher;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

/**
 * SourceWebSocketHandler class manages websocket communication between GraphQL gateway and remote
 * schema sources. Unlike GatewayWebSocketHandler it's not a singleton object and it will be created
 * for every single GraphQL subscription request. Mostly, it can be used inside a
 * SubscriptionRetriever implementation.
 *
 * <p>The handler supports partial text messages and assembles the fragments of a message before it
 * is handled. A jakarta.websocket container delivers a text message that is larger than its inbound
 * buffer (8 KiB by default on Tomcat, for the client container too) only to a handler that supports
 * partial messages; to any other handler it delivers nothing and closes the session with status
 * 1009 (message too big). A subscription event that carries a list of a few hundred objects easily
 * exceeds 8 KiB, so without the assembly such an operation ended as soon as its first event
 * arrived.
 *
 * <p>The size of one message is limited to {@link #DEFAULT_MAX_MESSAGE_SIZE} characters unless
 * another limit is given. The limit counts characters (UTF-16 {@code char}s, as Java strings hold
 * them), not bytes: 10 Mi characters take up to 20 MiB of heap and can be up to about 30 MB of
 * UTF-8 on the wire. A message that grows beyond the limit fails the operation with an error and
 * the source session is closed; every later message on that session is dropped.
 */
public class SourceWebSocketHandler extends AbstractWebSocketHandler {

  /**
   * Default limit on the size of one message from a source: 10 Mi (10 485 760) characters. It
   * counts UTF-16 characters, not bytes: up to 20 MiB of heap, up to about 30 MB of UTF-8.
   */
  public static final int DEFAULT_MAX_MESSAGE_SIZE = 10 * 1024 * 1024;

  private static final System.Logger LOG = System.getLogger(SourceWebSocketHandler.class.getName());

  /**
   * Most characters the fragment buffer keeps allocated between messages. A buffer that grew beyond
   * this for a large fragmented message is released once the message is handled, so that one large
   * event does not keep its buffer for the whole operation.
   */
  private static final int RETAINED_BUFFER_CAPACITY = 64 * 1024;

  private final @NotNull GraphQLQuery query;
  private final @NotNull SubscriptionSourcePublisher publisher;
  private final @NotNull Map<String, Object> connectionInitPayload;
  private final int maxMessageSize;

  /** Fragments of the text message being received; guarded by itself. */
  private final @NotNull StringBuilder fragments = new StringBuilder();

  /**
   * Set once a message exceeded the limit: every later message is dropped (the session is ending);
   * guarded by {@link #fragments}.
   */
  private boolean oversized;

  private @Nullable SubscriptionSourceHandler sourceHandler;

  /**
   * Creates a SourceWebSocketHandler instance
   *
   * @param query incoming GraphQL subscription query
   * @param publisher publisher object for subscribing the websocket stream data
   */
  public SourceWebSocketHandler(
      final @NotNull GraphQLQuery query, final @NotNull SubscriptionSourcePublisher publisher) {
    this(query, publisher, Map.of());
  }

  /**
   * Creates a SourceWebSocketHandler instance whose {@code connection_init} carries the given
   * payload, e.g. credentials taken from a {@link
   * io.fria.lilo.subscription.SubscriptionConnectionContext}
   *
   * @param query incoming GraphQL subscription query
   * @param publisher publisher object for subscribing the websocket stream data
   * @param connectionInitPayload payload of the {@code connection_init} sent to the source
   */
  public SourceWebSocketHandler(
      final @NotNull GraphQLQuery query,
      final @NotNull SubscriptionSourcePublisher publisher,
      final @NotNull Map<String, Object> connectionInitPayload) {
    // The default needs no validation, so this does not go through the validating constructor
    this(DEFAULT_MAX_MESSAGE_SIZE, query, publisher, connectionInitPayload);
  }

  /**
   * Creates a SourceWebSocketHandler instance with its own limit on the size of a message from the
   * source. A message that grows beyond the limit fails the operation with an error and the source
   * session is closed.
   *
   * @param query incoming GraphQL subscription query
   * @param publisher publisher object for subscribing the websocket stream data
   * @param connectionInitPayload payload of the {@code connection_init} sent to the source
   * @param maxMessageSize most characters (UTF-16 {@code char}s, not bytes) one message from the
   *     source may have, greater than zero
   */
  public SourceWebSocketHandler(
      final @NotNull GraphQLQuery query,
      final @NotNull SubscriptionSourcePublisher publisher,
      final @NotNull Map<String, Object> connectionInitPayload,
      final int maxMessageSize) {
    // Validated before the object exists, so an invalid limit leaves no partial instance
    this(validMaxMessageSize(maxMessageSize), query, publisher, connectionInitPayload);
  }

  private SourceWebSocketHandler(
      final int maxMessageSize,
      final @NotNull GraphQLQuery query,
      final @NotNull SubscriptionSourcePublisher publisher,
      final @NotNull Map<String, Object> connectionInitPayload) {
    this.query = query;
    this.publisher = publisher;
    // LinkedHashMap rather than Map.copyOf: a JSON payload may hold null values
    this.connectionInitPayload =
        Collections.unmodifiableMap(new LinkedHashMap<>(connectionInitPayload));
    this.maxMessageSize = maxMessageSize;
  }

  private static int validMaxMessageSize(final int maxMessageSize) {
    if (maxMessageSize <= 0) {
      throw new IllegalArgumentException("maxMessageSize must be greater than zero");
    }
    return maxMessageSize;
  }

  /**
   * This method delegates session initialization to Lilo SubscriptionSourceHandler
   *
   * @param nativeSession Session object provided by Spring
   */
  @Override
  public void afterConnectionEstablished(final @NotNull WebSocketSession nativeSession) {
    this.sourceHandler =
        new SubscriptionSourceHandler(this.query, this.publisher, this.connectionInitPayload);
    // Unlike client sessions, a source session is not decorated: it carries one operation's
    // low-volume traffic (subscribe, pong, complete), so the wrapper's simple send lock suffices
    this.sourceHandler.handleSessionStart(
        Objects.requireNonNull(ClientSessionWrapper.wrap(nativeSession)));
  }

  /**
   * This method delegates session close operations to Lilo SubscriptionSourceHandler. The close
   * status is passed on, so an operation that the source broke off is reported with the reason the
   * container gave, e.g. {@code 1009 The decoded text message was too big ...}.
   *
   * @param nativeSession Session object provided by Spring
   * @param closeStatus Session close status
   */
  @Override
  public void afterConnectionClosed(
      final @Nullable WebSocketSession nativeSession, final @NotNull CloseStatus closeStatus) {

    final SubscriptionSourceHandler handler = this.sourceHandler;

    if (handler == null) {
      throw new IllegalStateException("Source Handler must be initialized");
    }

    final String reason = closeStatus.getReason();
    final String closeReason =
        reason == null || reason.isBlank()
            ? String.valueOf(closeStatus.getCode())
            : closeStatus.getCode() + " " + reason;

    // Messages and the close are delivered one after another, but the lock keeps the buffer's
    // state visible whichever container thread runs this
    synchronized (this.fragments) {
      this.fragments.setLength(0);
      this.fragments.trimToSize();
    }

    handler.handleSessionClose(ClientSessionWrapper.wrap(nativeSession), closeReason);
  }

  /**
   * Accepts text messages in fragments, so that a message larger than the container's inbound
   * buffer reaches the handler instead of closing the session.
   *
   * @return true
   */
  @Override
  public boolean supportsPartialMessages() {
    return true;
  }

  @Override
  public void handleTextMessage(
      final @NotNull WebSocketSession nativeSession, final @NotNull TextMessage message) {

    final SubscriptionSourceHandler handler = this.sourceHandler;

    if (handler == null) {
      throw new IllegalStateException("Source Handler must be initialized");
    }

    final String payload = this.assemble(nativeSession, message);

    if (payload != null) {
      handler.handleMessage(
          Objects.requireNonNull(ClientSessionWrapper.wrap(nativeSession)), payload);
    }
  }

  /**
   * Adds a fragment to the message being received. A message beyond the size limit fails the
   * operation, which is reported after the lock is released: the failure runs gateway callbacks and
   * session sends.
   *
   * @return the complete message once its last fragment arrived, null while it is incomplete or
   *     once a message exceeded the size limit
   */
  private @Nullable String assemble(
      final @NotNull WebSocketSession nativeSession, final @NotNull TextMessage message) {

    final String fragment = message.getPayload();

    synchronized (this.fragments) {
      if (this.oversized) {
        return null;
      }

      // long: the sum of two large lengths must not overflow
      final long length = (long) this.fragments.length() + fragment.length();

      if (length > this.maxMessageSize) {
        this.fragments.setLength(0);
        this.fragments.trimToSize();
        this.oversized = true;
      } else if (this.fragments.length() == 0 && message.isLast()) {
        // The common case: a message that arrived whole
        return fragment;
      } else {
        this.fragments.append(fragment);

        if (!message.isLast()) {
          return null;
        }

        final String payload = this.fragments.toString();
        this.fragments.setLength(0);
        if (this.fragments.capacity() > RETAINED_BUFFER_CAPACITY) {
          this.fragments.trimToSize();
        }
        return payload;
      }
    }

    // Only the oversized branch gets here, once: later messages return above
    this.failOversized(nativeSession);
    return null;
  }

  /** Capacity of the fragment buffer, in characters; for tests. */
  int bufferCapacity() {
    synchronized (this.fragments) {
      return this.fragments.capacity();
    }
  }

  /**
   * Fails the operation and closes the source session. The failure is reported before the close
   * (see SubscriptionSourceHandler); once the operation was subscribed, the failure itself ends the
   * source session, so the close here only covers a session that is still open, e.g. before {@code
   * connection_ack}. The gateway logs the failed operation; this handler does not log it again.
   */
  private void failOversized(final @NotNull WebSocketSession nativeSession) {
    this.publisher.error(
        new IllegalStateException(
            "Subscription source message exceeds " + this.maxMessageSize + " characters"));
    try {
      if (nativeSession.isOpen()) {
        nativeSession.close(CloseStatus.TOO_BIG_TO_PROCESS);
      }
    } catch (final IOException | IllegalStateException e) {
      LOG.log(System.Logger.Level.DEBUG, "Could not close the oversized source session", e);
    }
  }
}
