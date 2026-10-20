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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class of the default HTTP retrievers. Requests are POSTed as JSON with {@code
 * java.net.http.HttpClient}; one client is created lazily per retriever instance (and options
 * instance) and reused, since it is thread-safe.
 */
public abstract class AbstractDefaultRemoteRetriever {

  private static final int MAX_ERROR_BODY_CHARS = 512;
  private static final int HTTP_OK_MIN = 200;
  private static final int HTTP_OK_MAX = 299;
  private static final String APPLICATION_JSON = "application/json";

  private final AtomicReference<ClientHolder> clientHolder = new AtomicReference<>();

  /**
   * Posts {@code query} to {@code schemaUrl} with {@link RemoteRetrieverOptions#defaults()} and no
   * extra headers.
   *
   * @param schemaUrl an {@code http} or {@code https} URL
   * @param query the JSON request body
   * @return the response body
   */
  protected @NotNull String retrieve(final @NotNull String schemaUrl, final @NotNull String query) {
    return this.retrieve(schemaUrl, query, RemoteRetrieverOptions.defaults(), Map.of());
  }

  /**
   * Posts {@code query} to {@code schemaUrl} as JSON.
   *
   * <p>A 2xx response body is returned as is. A non-2xx response body is returned when it is a
   * GraphQL response carrying {@code errors}, so the engine can surface them; otherwise an {@link
   * IllegalStateException} with the status code and the start of the body is thrown. Header values
   * are never included in exception messages.
   *
   * @param schemaUrl an {@code http} or {@code https} URL
   * @param query the JSON request body
   * @param options timeouts, size limit and redirect policy
   * @param headers extra request headers, which may override {@code Content-Type} and {@code
   *     Accept}
   * @return the response body
   * @throws IllegalArgumentException if the URL is not an absolute {@code http}/{@code https} URL
   *     or a header is invalid
   * @throws IllegalStateException if the request fails, times out, is interrupted, the response is
   *     larger than {@link RemoteRetrieverOptions#getMaxResponseBytes()} or is a non-GraphQL error
   */
  protected @NotNull String retrieve(
      final @NotNull String schemaUrl,
      final @NotNull String query,
      final @NotNull RemoteRetrieverOptions options,
      final @Nullable Map<String, String> headers) {

    Objects.requireNonNull(query);
    Objects.requireNonNull(options);

    final URI uri = toHttpUri(schemaUrl);
    final HttpRequest request = buildRequest(uri, query, options, headers);
    final long maxBytes = options.getMaxResponseBytes();

    final CompletableFuture<HttpResponse<byte[]>> future =
        this.client(options)
            .sendAsync(request, responseInfo -> new BoundedBodySubscriber(maxBytes));

    final HttpResponse<byte[]> response;

    try {
      response = future.get(options.getRequestTimeout().toMillis(), TimeUnit.MILLISECONDS);
    } catch (final InterruptedException e) {
      future.cancel(true);
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while calling " + describe(uri), e);
    } catch (final TimeoutException e) {
      future.cancel(true);
      throw timedOut(uri, options, e);
    } catch (final ExecutionException e) {
      throw failed(uri, options, e.getCause() == null ? e : e.getCause());
    }

    return handleResponse(uri, response);
  }

  private static @NotNull HttpRequest buildRequest(
      final @NotNull URI uri,
      final @NotNull String query,
      final @NotNull RemoteRetrieverOptions options,
      final @Nullable Map<String, String> headers) {

    final HttpRequest.Builder builder =
        HttpRequest.newBuilder(uri)
            .timeout(options.getRequestTimeout())
            .header("Content-Type", APPLICATION_JSON)
            .header("Accept", APPLICATION_JSON)
            .POST(HttpRequest.BodyPublishers.ofString(query, StandardCharsets.UTF_8));

    if (headers != null) {
      for (final Map.Entry<String, String> header : headers.entrySet()) {
        final String name = header.getKey();
        final String value = header.getValue();

        if (name == null || value == null) {
          throw new IllegalArgumentException("Request header names and values must not be null");
        }

        try {
          builder.setHeader(name, value);
        } catch (final IllegalArgumentException e) {
          // The cause is dropped on purpose: its message may contain the header value
          throw new IllegalArgumentException("Invalid or restricted request header: " + name);
        }
      }
    }

    return builder.build();
  }

  private static @NotNull String describe(final @NotNull URI uri) {
    // Leaves out user info and query string, which may carry credentials
    final String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
    final String path = uri.getRawPath() == null ? "" : uri.getRawPath();
    return uri.getScheme() + "://" + uri.getHost() + port + path;
  }

  private static @NotNull IllegalStateException failed(
      final @NotNull URI uri,
      final @NotNull RemoteRetrieverOptions options,
      final @NotNull Throwable cause) {

    if (hasCause(cause, HttpTimeoutException.class)) {
      return timedOut(uri, options, cause);
    }

    if (hasCause(cause, BoundedBodySubscriber.ResponseTooLargeException.class)) {
      return new IllegalStateException(
          "Response from "
              + describe(uri)
              + " exceeded maxResponseBytes ("
              + options.getMaxResponseBytes()
              + " bytes)",
          cause);
    }

    return new IllegalStateException("Request to " + describe(uri) + " failed: " + cause, cause);
  }

  private static boolean hasCause(
      final @NotNull Throwable throwable, final @NotNull Class<? extends Throwable> type) {

    for (Throwable current = throwable; current != null; current = current.getCause()) {
      if (type.isInstance(current)) {
        return true;
      }
    }

    return false;
  }

  private static @NotNull String handleResponse(
      final @NotNull URI uri, final @NotNull HttpResponse<byte[]> response) {

    final String body = new String(response.body(), StandardCharsets.UTF_8);
    final int status = response.statusCode();

    if (status >= HTTP_OK_MIN && status <= HTTP_OK_MAX) {
      return body;
    }

    if (isGraphQLErrorResponse(body)) {
      return body;
    }

    final String truncated =
        body.length() > MAX_ERROR_BODY_CHARS
            ? body.substring(0, MAX_ERROR_BODY_CHARS) + "...(truncated)"
            : body;

    throw new IllegalStateException(
        "Remote source " + describe(uri) + " responded with HTTP " + status + ": " + truncated);
  }

  private static boolean isGraphQLErrorResponse(final @NotNull String body) {

    try {
      final Optional<Map<String, Object>> map = JsonUtils.toMap(body);
      return map.isPresent() && map.get().get("errors") instanceof List;
    } catch (final IllegalArgumentException e) {
      return false;
    }
  }

  private static @NotNull IllegalStateException timedOut(
      final @NotNull URI uri,
      final @NotNull RemoteRetrieverOptions options,
      final @NotNull Throwable cause) {

    return new IllegalStateException(
        "Request to "
            + describe(uri)
            + " timed out after "
            + options.getRequestTimeout().toMillis()
            + " ms",
        cause);
  }

  private static @NotNull URI toHttpUri(final @NotNull String schemaUrl) {

    final URI uri = URI.create(Objects.requireNonNull(schemaUrl));
    final String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);

    if (!"http".equals(scheme) && !"https".equals(scheme)) {
      throw new IllegalArgumentException(
          "Only http and https schema URLs are supported, got scheme: '" + scheme + "'");
    }

    if (uri.getHost() == null) {
      throw new IllegalArgumentException("Schema URL has no host");
    }

    return uri;
  }

  private @NotNull HttpClient client(final @NotNull RemoteRetrieverOptions options) {

    final ClientHolder current = this.clientHolder.get();

    if (current != null && current.options == options) {
      return current.client;
    }

    final HttpClient client =
        HttpClient.newBuilder()
            // HTTP/2 would make every plain http POST offer an h2c upgrade, which some servers
            // and proxies mishandle; GraphQL requests gain nothing from it
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(options.getConnectTimeout())
            .followRedirects(
                options.isFollowRedirects()
                    ? HttpClient.Redirect.NORMAL
                    : HttpClient.Redirect.NEVER)
            .build();

    final ClientHolder created = new ClientHolder(options, client);

    if (this.clientHolder.compareAndSet(current, created)) {
      return client;
    }

    // Another thread created a client concurrently; prefer it when the options match
    final ClientHolder winner = this.clientHolder.get();
    return winner != null && winner.options == options ? winner.client : client;
  }

  private static final class ClientHolder {

    private final RemoteRetrieverOptions options;
    private final HttpClient client;

    private ClientHolder(
        final @NotNull RemoteRetrieverOptions options, final @NotNull HttpClient client) {
      this.options = options;
      this.client = client;
    }
  }
}
