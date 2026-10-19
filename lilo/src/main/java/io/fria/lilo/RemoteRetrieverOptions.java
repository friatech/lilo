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
package io.fria.lilo;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable HTTP settings for {@link DefaultRemoteQueryRetriever} and {@link
 * DefaultRemoteIntrospectionRetriever}.
 *
 * <p>Use {@link #defaults()} for the defaults or {@link #builder()} to change some of them.
 */
public final class RemoteRetrieverOptions {

  /** Default time allowed to establish a connection: 5 seconds. */
  public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);

  /** Default time allowed for a whole request, from sending it to reading the body: 30 seconds. */
  public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

  /** Default maximum response body size: 10 MiB. */
  public static final long DEFAULT_MAX_RESPONSE_BYTES = 10L * 1024 * 1024;

  private static final BiFunction<LiloContext, Object, Map<String, String>> NO_HEADERS =
      (liloContext, localContext) -> Map.of();

  private static final RemoteRetrieverOptions DEFAULTS = builder().build();

  private final Duration connectTimeout;
  private final Duration requestTimeout;
  private final long maxResponseBytes;
  private final boolean followRedirects;
  private final BiFunction<LiloContext, Object, Map<String, String>> headers;

  private RemoteRetrieverOptions(final @NotNull Builder builder) {
    this.connectTimeout = builder.connectTimeout;
    this.requestTimeout = builder.requestTimeout;
    this.maxResponseBytes = builder.maxResponseBytes;
    this.followRedirects = builder.followRedirects;
    this.headers = builder.headers;
  }

  public static @NotNull Builder builder() {
    return new Builder();
  }

  /**
   * Returns the shared options instance holding every default value.
   *
   * @return the default options
   */
  public static @NotNull RemoteRetrieverOptions defaults() {
    return DEFAULTS;
  }

  /**
   * Time allowed to establish a TCP (and TLS) connection to the remote source.
   *
   * @return the connect timeout, {@link #DEFAULT_CONNECT_TIMEOUT} (5 s) by default
   */
  public @NotNull Duration getConnectTimeout() {
    return this.connectTimeout;
  }

  /**
   * Function that supplies extra HTTP request headers, for example {@code Authorization}. It
   * receives the {@link LiloContext} and the {@code localContext} given to the retriever (which may
   * be {@code null}) and is called once per request. Header values are never logged or included in
   * exception messages.
   *
   * @return the header function, one returning an empty map by default
   */
  public @NotNull BiFunction<LiloContext, Object, Map<String, String>> getHeaders() {
    return this.headers;
  }

  /**
   * Maximum number of response body bytes read from the remote source. A larger response is aborted
   * while it is being read and fails the request.
   *
   * @return the limit, {@link #DEFAULT_MAX_RESPONSE_BYTES} (10 MiB) by default
   */
  public long getMaxResponseBytes() {
    return this.maxResponseBytes;
  }

  /**
   * Time allowed for the whole request: sending it, waiting for the response and reading its body.
   *
   * @return the request timeout, {@link #DEFAULT_REQUEST_TIMEOUT} (30 s) by default
   */
  public @NotNull Duration getRequestTimeout() {
    return this.requestTimeout;
  }

  /**
   * Whether HTTP redirects are followed. A redirect that is not followed is treated as a non-2xx
   * response.
   *
   * @return {@code true} to follow redirects (except HTTPS to HTTP), {@code false} by default
   */
  public boolean isFollowRedirects() {
    return this.followRedirects;
  }

  public static final class Builder {

    private Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;
    private Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;
    private long maxResponseBytes = DEFAULT_MAX_RESPONSE_BYTES;
    private boolean followRedirects;
    private BiFunction<LiloContext, Object, Map<String, String>> headers = NO_HEADERS;

    private Builder() {
      // Use RemoteRetrieverOptions.builder()
    }

    public @NotNull RemoteRetrieverOptions build() {
      return new RemoteRetrieverOptions(this);
    }

    /**
     * Sets the connect timeout; defaults to 5 seconds.
     *
     * @param timeout a positive duration
     * @return this builder
     */
    public @NotNull Builder connectTimeout(final @NotNull Duration timeout) {
      this.connectTimeout = requirePositive(timeout, "connectTimeout");
      return this;
    }

    /**
     * Sets whether redirects are followed; defaults to {@code false}.
     *
     * @param follow {@code true} to follow redirects
     * @return this builder
     */
    public @NotNull Builder followRedirects(final boolean follow) {
      this.followRedirects = follow;
      return this;
    }

    /**
     * Sets the function supplying extra request headers; defaults to one returning an empty map.
     *
     * @param headersFunction receives the {@link LiloContext} and the retriever's {@code
     *     localContext}
     * @return this builder
     */
    public @NotNull Builder headers(
        final @NotNull BiFunction<LiloContext, Object, Map<String, String>> headersFunction) {
      this.headers = Objects.requireNonNull(headersFunction, "headers must not be null");
      return this;
    }

    /**
     * Sets the maximum response body size; defaults to 10 MiB.
     *
     * @param maxBytes a positive number of bytes
     * @return this builder
     */
    public @NotNull Builder maxResponseBytes(final long maxBytes) {
      if (maxBytes <= 0) {
        throw new IllegalArgumentException("maxResponseBytes must be positive");
      }

      this.maxResponseBytes = maxBytes;
      return this;
    }

    /**
     * Sets the request timeout covering the whole exchange; defaults to 30 seconds.
     *
     * @param timeout a positive duration
     * @return this builder
     */
    public @NotNull Builder requestTimeout(final @NotNull Duration timeout) {
      this.requestTimeout = requirePositive(timeout, "requestTimeout");
      return this;
    }

    private static @NotNull Duration requirePositive(
        final @NotNull Duration duration, final @NotNull String name) {

      Objects.requireNonNull(duration, name + " must not be null");

      if (duration.isNegative() || duration.isZero()) {
        throw new IllegalArgumentException(name + " must be positive");
      }

      return duration;
    }
  }
}
