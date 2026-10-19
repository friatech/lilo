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

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * This class provides code completion support for IDEs when using lilo properties in
 * application.yml/properties file. Also provides a programmatic approach to accessing this
 * configuration from other classes.
 */
@ConfigurationProperties(prefix = "lilo")
public class LiloConfigProperties {

  private @Nullable String graphQlPath = "/graphql";
  private @Nullable String subscriptionPath = "/graphql";
  private @NotNull List<String> allowedOrigins = List.of("*");
  private @NotNull Duration sendTimeLimit = GatewayLimits.DEFAULT_SEND_TIME_LIMIT;
  private int sendBufferSizeLimit = GatewayLimits.DEFAULT_SEND_BUFFER_SIZE_LIMIT;
  private @Nullable Integer subscriptionPoolSize;
  private @Nullable Integer maxOperationsPerSession;

  /**
   * Origin patterns the subscription websocket endpoint accepts, as passed to Spring's {@code
   * setAllowedOriginPatterns}: e.g. {@code https://app.example.com} or {@code
   * https://*.example.com}. By default, it's {@code *}, which accepts every origin. An empty list
   * accepts same-origin requests only.
   *
   * @return allowed origin patterns of the subscription endpoint
   */
  public @NotNull List<String> getAllowedOrigins() {
    return this.allowedOrigins;
  }

  /**
   * GraphQL path which serves query and mutation operations. By default, it's /graphql
   *
   * @return GraphQL query/mutation path
   */
  public @Nullable String getGraphQlPath() {
    return this.graphQlPath;
  }

  /**
   * GraphQL subscription Path, WebSocket handler will be bound to this path. By default, it's
   * /graphql
   *
   * @return GraphQL Subscription path
   */
  public @Nullable String getSubscriptionPath() {
    return this.subscriptionPath;
  }

  /**
   * How long a send to a subscription client may take before the client's session is closed as not
   * reliable. By default, it's 10 seconds. It is checked only when another frame is sent to the
   * client while a send is in progress; the container's own blocking send timeout remains the
   * backstop for the thread that is writing.
   *
   * @return send time limit of subscription client sessions
   */
  public @NotNull Duration getSendTimeLimit() {
    return this.sendTimeLimit;
  }

  /**
   * How many bytes may wait to be sent to a subscription client before the client's session is
   * closed as not reliable. By default, it's 524288 (512 KiB). It is checked only when another
   * frame is sent to the client while a send is in progress.
   *
   * @return send buffer size limit of subscription client sessions, in bytes
   */
  public int getSendBufferSizeLimit() {
    return this.sendBufferSizeLimit;
  }

  /**
   * Core size of the pool that stitches subscriptions. The pool grows to four times that and queues
   * up to 256 more requests; a subscribe beyond that is answered with an {@code error} message. If
   * not set, it's max(4, available processors), the default of {@code SubscriptionGatewayHandler}.
   *
   * @return core size of the subscription stitch pool, or null if not set
   */
  public @Nullable Integer getSubscriptionPoolSize() {
    return this.subscriptionPoolSize;
  }

  /**
   * How many operations one subscription client session may have at the same time. A subscribe
   * beyond that is answered with an {@code error} message and the session stays open. If not set,
   * it's 50, the default of {@code SubscriptionGatewayHandler}.
   *
   * @return most operations of one client session, or null if not set
   */
  public @Nullable Integer getMaxOperationsPerSession() {
    return this.maxOperationsPerSession;
  }

  /**
   * setter for the core size of the subscription stitch pool. If not set, it's max(4, available
   * processors).
   *
   * @param subscriptionPoolSize new core size; positive, or null for the default
   */
  public void setSubscriptionPoolSize(final @Nullable Integer subscriptionPoolSize) {
    if (subscriptionPoolSize != null) {
      GatewayLimits.validSubscriptionPoolSize(subscriptionPoolSize);
    }
    this.subscriptionPoolSize = subscriptionPoolSize;
  }

  /**
   * setter for the most operations one subscription client session may have at the same time. If
   * not set, it's 50.
   *
   * @param maxOperationsPerSession new cap; positive, or null for the default
   */
  public void setMaxOperationsPerSession(final @Nullable Integer maxOperationsPerSession) {
    if (maxOperationsPerSession != null) {
      GatewayLimits.validMaxOperationsPerSession(maxOperationsPerSession);
    }
    this.maxOperationsPerSession = maxOperationsPerSession;
  }

  /**
   * setter for the send time limit of subscription client sessions. If not set, it's 10 seconds.
   *
   * @param sendTimeLimit new send time limit; positive, at most {@link Integer#MAX_VALUE} ms
   */
  public void setSendTimeLimit(final @NotNull Duration sendTimeLimit) {
    GatewayLimits.validSendTimeLimitMillis(sendTimeLimit);
    this.sendTimeLimit = sendTimeLimit;
  }

  /**
   * setter for the send buffer size limit of subscription client sessions. If not set, it's 524288
   * bytes (512 KiB).
   *
   * @param sendBufferSizeLimit new send buffer size limit in bytes; positive
   */
  public void setSendBufferSizeLimit(final int sendBufferSizeLimit) {
    this.sendBufferSizeLimit = GatewayLimits.validSendBufferSizeLimit(sendBufferSizeLimit);
  }

  /**
   * setter for the origin patterns the subscription websocket endpoint accepts. If not set, it's
   * {@code *}, which accepts every origin. An empty list accepts same-origin requests only.
   *
   * @param allowedOrigins new allowed origin patterns, e.g. {@code https://app.example.com}
   */
  public void setAllowedOrigins(final @NotNull List<String> allowedOrigins) {
    this.allowedOrigins = List.copyOf(Objects.requireNonNull(allowedOrigins));
  }

  /**
   * setter for GraphQL query and mutation path. If not set, it's /graphql
   *
   * @param graphQlPath new GraphQL query / mutation path
   */
  public void setGraphQlPath(final @NotNull String graphQlPath) {
    this.graphQlPath = graphQlPath;
  }

  /**
   * setter for GraphQL subscription path. If not set, it's /graphql
   *
   * @param subscriptionPath new GraphQL subscription path
   */
  public void setSubscriptionPath(final @NotNull String subscriptionPath) {
    this.subscriptionPath = subscriptionPath;
  }
}
