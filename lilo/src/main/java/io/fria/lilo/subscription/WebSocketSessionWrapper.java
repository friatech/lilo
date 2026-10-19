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
import org.jetbrains.annotations.NotNull;

/**
 * WebSocketSessionWrapper encapsulates the basic session operations for making communication
 * framework-agnostic.
 *
 * <p>Lilo sends to one session from several threads at the same time: the websocket thread that
 * delivered a client message, the gateway's stitch executor, and the threads of the source
 * connections that deliver events. An implementation must therefore make {@link #send} thread-safe
 * for one session, for example by serialising the writes, and should keep it from blocking
 * indefinitely on a stalled client; it may drop the message or close the session instead.
 */
public interface WebSocketSessionWrapper {

  /** Closes the websocket session */
  void close();

  /**
   * Returns session specific identifier
   *
   * @return unique session id
   */
  String getId();

  /**
   * indicates that session is open or not
   *
   * @return true for open session, false for closed session
   */
  boolean isOpen();

  /**
   * Returns what the transport attached to the session, such as the handshake request headers under
   * {@link SubscriptionConnectionContext#HEADERS_ATTRIBUTE}.
   *
   * <p>A binding for a transport other than lilo-spring that wants the handshake headers to reach
   * {@link SubscriptionConnectionContext#getHandshakeHeaders()} stores them under {@link
   * SubscriptionConnectionContext#HEADERS_ATTRIBUTE} as a {@code Map<String, String>} of header
   * name to value, with lower-cased names (one value per header) as lilo-spring does; entries that
   * are not string to string are ignored.
   *
   * <p>The returned map may be the transport's live attribute map: callers must neither retain nor
   * mutate it, and should copy what they need.
   *
   * @return session attributes, empty by default
   */
  default @NotNull Map<String, Object> getAttributes() {
    return Map.of();
  }

  /**
   * Sends a string message. It may be called concurrently from several threads for the same session
   * and must be thread-safe; see the type's documentation.
   *
   * @param message websocket string message
   */
  void send(@NotNull String message);
}
