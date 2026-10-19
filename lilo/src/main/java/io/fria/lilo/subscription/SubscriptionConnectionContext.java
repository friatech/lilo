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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What a client sent when it opened a subscription connection to the gateway. The {@link
 * SubscriptionGatewayHandler} builds one per websocket session and passes it as the {@code
 * localContext} of every subscription it stitches for that session, so it reaches the {@code
 * localContext} argument of {@link SubscriptionRetriever#sendQuery}. A retriever can forward
 * credentials from it to the source, for example:
 *
 * <pre>{@code
 * (liloContext, schemaSource, query, publisher, localContext) -> {
 *   final Map<String, Object> initPayload =
 *       localContext instanceof SubscriptionConnectionContext
 *           ? ((SubscriptionConnectionContext) localContext).getConnectionInitPayload()
 *           : Map.of();
 *   // open the source connection with
 *   // new SubscriptionSourceHandler(query, publisher, initPayload)
 * }
 * }</pre>
 *
 * <p>It holds:
 *
 * <ul>
 *   <li>the {@linkplain #getConnectionInitPayload() connection_init payload}: the JSON object the
 *       client sent as the payload of its {@code connection_init} message (graphql-transport-ws),
 *       commonly carrying an {@code Authorization} entry. It is empty when the client sent no
 *       payload or a payload that is not a JSON object.
 *   <li>the {@linkplain #getHandshakeHeaders() handshake headers}: the HTTP headers of the
 *       websocket upgrade request, taken from the session attribute {@value #HEADERS_ATTRIBUTE}
 *       that the transport binding sets (lilo-spring's {@code GatewayHandshakeInterceptor} stores
 *       the first value of each header under its lower-cased name). It is empty when the transport
 *       sets no such attribute.
 * </ul>
 *
 * <p>Both maps are unmodifiable. They may carry credentials: do not log them.
 */
public final class SubscriptionConnectionContext {

  /**
   * Session attribute under which a transport binding stores the handshake request headers, as a
   * {@code Map<String, String>} of header name to value.
   */
  public static final String HEADERS_ATTRIBUTE = "lilo.handshake.headers";

  private final @NotNull Map<String, Object> connectionInitPayload;
  private final @NotNull Map<String, String> handshakeHeaders;

  /**
   * Creates a context from copies of the given maps.
   *
   * @param connectionInitPayload the payload of the client's {@code connection_init}
   * @param handshakeHeaders the headers of the websocket upgrade request
   */
  public SubscriptionConnectionContext(
      final @NotNull Map<String, ?> connectionInitPayload,
      final @NotNull Map<String, String> handshakeHeaders) {
    // LinkedHashMap rather than Map.copyOf: a JSON payload may hold null values
    this.connectionInitPayload =
        Collections.unmodifiableMap(new LinkedHashMap<>(connectionInitPayload));
    this.handshakeHeaders = Collections.unmodifiableMap(new LinkedHashMap<>(handshakeHeaders));
  }

  /**
   * Builds a context from untrusted input: the {@code connection_init} payload as parsed from JSON
   * and the session attributes. Anything not of the expected shape is left out.
   *
   * @param payload the {@code connection_init} payload, if any
   * @param attributes the session attributes
   * @return a context, never null
   */
  static @NotNull SubscriptionConnectionContext of(
      final @Nullable Object payload, final @NotNull Map<String, Object> attributes) {

    final Map<String, Object> initPayload = new LinkedHashMap<>();

    if (payload instanceof Map) {
      for (final Map.Entry<?, ?> entry : ((Map<?, ?>) payload).entrySet()) {
        if (entry.getKey() instanceof String) {
          initPayload.put((String) entry.getKey(), entry.getValue());
        }
      }
    }

    final Map<String, String> headers = new LinkedHashMap<>();
    final Object headersAttribute = attributes.get(HEADERS_ATTRIBUTE);

    if (headersAttribute instanceof Map) {
      for (final Map.Entry<?, ?> entry : ((Map<?, ?>) headersAttribute).entrySet()) {
        if (entry.getKey() instanceof String && entry.getValue() instanceof String) {
          headers.put((String) entry.getKey(), (String) entry.getValue());
        }
      }
    }

    return new SubscriptionConnectionContext(initPayload, headers);
  }

  /**
   * Returns the payload of the client's {@code connection_init} message.
   *
   * @return unmodifiable map, empty when the client sent no JSON object
   */
  public @NotNull Map<String, Object> getConnectionInitPayload() {
    return this.connectionInitPayload;
  }

  /**
   * Returns the headers of the websocket upgrade request.
   *
   * @return unmodifiable map of header name to value, empty when the transport provided none
   */
  public @NotNull Map<String, String> getHandshakeHeaders() {
    return this.handshakeHeaders;
  }
}
