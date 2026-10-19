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

import io.fria.lilo.Lilo;
import io.fria.lilo.subscription.SubscriptionGatewayHandler;
import java.time.Duration;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

/**
 * GatewayWebSocketHandler manages websocket session interaction between Gateway and connected
 * GraphQL client.
 */
public class GatewayWebSocketHandler extends AbstractWebSocketHandler {

  /** Default of the send time limit of a client session. */
  public static final Duration DEFAULT_SEND_TIME_LIMIT = GatewayLimits.DEFAULT_SEND_TIME_LIMIT;

  /** Default of the send buffer size limit of a client session, in bytes. */
  public static final int DEFAULT_SEND_BUFFER_SIZE_LIMIT =
      GatewayLimits.DEFAULT_SEND_BUFFER_SIZE_LIMIT;

  private final @NotNull SubscriptionGatewayHandler gatewayHandler;
  private final @NotNull String subscriptionPath;
  private final int sendTimeLimitMillis;
  private final int sendBufferSizeLimit;

  /**
   * Constructor creates a GatewayWebSocketHandler which manages websocket communication between
   * GraphQL clients and Lilo GraphQL Gateway, with the default send limits.
   *
   * @param lilo Lilo object instance or bean
   * @param subscriptionPath subscription path for handling subscription requests. Mostly it's
   *     /graphql
   */
  public GatewayWebSocketHandler(final @NotNull Lilo lilo, final @NotNull String subscriptionPath) {
    this(
        lilo,
        subscriptionPath,
        (int) DEFAULT_SEND_TIME_LIMIT.toMillis(),
        DEFAULT_SEND_BUFFER_SIZE_LIMIT);
  }

  /**
   * Constructor creates a GatewayWebSocketHandler which manages websocket communication between
   * GraphQL clients and Lilo GraphQL Gateway.
   *
   * <p>Every client session is decorated with a {@link
   * org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator} using the given
   * limits: frames sent while another thread writes to the session are queued instead of blocking
   * the sender, and a session whose write takes longer than {@code sendTimeLimit} or whose queue
   * grows beyond {@code sendBufferSizeLimit} bytes is closed as not reliable. The limits are
   * checked only when another send arrives; the container's own blocking send timeout remains the
   * backstop for the single thread that is writing. The thread that hits a limit closes the session
   * and may block on the container's native close, which sends a close frame and is bounded by the
   * container's blocking send timeout (e.g. 20 seconds on Tomcat).
   *
   * @param lilo Lilo object instance or bean
   * @param subscriptionPath subscription path for handling subscription requests. Mostly it's
   *     /graphql
   * @param sendTimeLimit how long a send to a client may take; positive, at most {@link
   *     Integer#MAX_VALUE} milliseconds
   * @param sendBufferSizeLimit how many bytes may wait to be sent to a client; positive
   */
  public GatewayWebSocketHandler(
      final @NotNull Lilo lilo,
      final @NotNull String subscriptionPath,
      final @NotNull Duration sendTimeLimit,
      final int sendBufferSizeLimit) {
    // Validated before the object exists, so an invalid limit leaves no partial instance
    this(
        lilo,
        subscriptionPath,
        GatewayLimits.validSendTimeLimitMillis(sendTimeLimit),
        GatewayLimits.validSendBufferSizeLimit(sendBufferSizeLimit));
  }

  private GatewayWebSocketHandler(
      final @NotNull Lilo lilo,
      final @NotNull String subscriptionPath,
      final int sendTimeLimitMillis,
      final int sendBufferSizeLimit) {
    this.sendTimeLimitMillis = sendTimeLimitMillis;
    this.sendBufferSizeLimit = sendBufferSizeLimit;
    this.gatewayHandler = new SubscriptionGatewayHandler(lilo);
    this.subscriptionPath = subscriptionPath;
  }

  /**
   * Prepares the session before anything is sent to it: it is decorated here, once, on the
   * websocket thread, before the gateway sends to it from other threads.
   *
   * @param nativeSession Session object provided by Spring
   */
  @Override
  public void afterConnectionEstablished(final @NotNull WebSocketSession nativeSession) {
    ClientSessionWrapper.decorate(
        nativeSession, this.sendTimeLimitMillis, this.sendBufferSizeLimit);
  }

  @Override
  public void afterConnectionClosed(
      final @Nullable WebSocketSession nativeSession, final @NotNull CloseStatus status) {
    this.gatewayHandler.handleSessionClose(ClientSessionWrapper.wrap(nativeSession));
  }

  @Override
  protected void handleTextMessage(
      final @NotNull WebSocketSession nativeSession, final @NotNull TextMessage message) {
    this.gatewayHandler.handleMessage(
        Objects.requireNonNull(ClientSessionWrapper.wrap(nativeSession)), message.getPayload());
  }

  /**
   * Getter for stored GraphQL subscription path for serving GraphQL subscription path. Mostly it's
   * /graphql
   *
   * @return actual value of subscription path
   */
  public @NotNull String getSubscriptionPath() {
    return this.subscriptionPath;
  }

  /**
   * Getter for the send time limit of client sessions
   *
   * @return how long a send to a client may take
   */
  public @NotNull Duration getSendTimeLimit() {
    return Duration.ofMillis(this.sendTimeLimitMillis);
  }

  /**
   * Getter for the send buffer size limit of client sessions
   *
   * @return how many bytes may wait to be sent to a client
   */
  public int getSendBufferSizeLimit() {
    return this.sendBufferSizeLimit;
  }
}
