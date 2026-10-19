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

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Failure of a subscription source: either an {@code error} message the source sent or a protocol
 * violation. The source's payload is kept as it was received; {@link #getErrors()} gives the form
 * that is safe to forward to a client.
 */
final class SubscriptionSourceException extends RuntimeException {

  static final String GENERIC_MESSAGE = "Subscription source reported an error";

  private static final long serialVersionUID = 1L;
  private static final int MAX_MESSAGE_LENGTH = 200;

  /** Raw payload of the source's error message; not serialized, as it may be any JSON value. */
  private final transient @Nullable Object payload;

  private SubscriptionSourceException(
      final @NotNull String message, final @Nullable Object payload) {
    super(message);
    this.payload = payload;
  }

  /**
   * Creates an exception for an {@code error} message sent by the source.
   *
   * @param payload the payload of the error message, a GraphQL error array if the source behaves
   * @return the exception, whose message holds at most a bounded part of the first error message
   */
  static @NotNull SubscriptionSourceException fromPayload(final @Nullable Object payload) {
    final List<Map<String, Object>> errors = toGraphQLErrors(payload);

    if (errors == null) {
      return new SubscriptionSourceException(GENERIC_MESSAGE, payload);
    }

    return new SubscriptionSourceException(
        GENERIC_MESSAGE + ": " + bound((String) errors.get(0).get("message")), payload);
  }

  /**
   * Creates an exception for a source that broke the protocol, e.g. sent an unparseable message.
   *
   * @param reason fixed description of the violation
   * @return the exception
   */
  static @NotNull SubscriptionSourceException protocolViolation(final @NotNull String reason) {
    return new SubscriptionSourceException(reason, null);
  }

  /**
   * Makes untrusted text safe for an exception message or a log line: at most 200 characters, with
   * control characters (such as CR and LF) replaced by spaces.
   *
   * @param text text received from a client or a source
   * @return bounded text without control characters
   */
  static @NotNull String bound(final @NotNull String text) {
    final String limited =
        text.length() > MAX_MESSAGE_LENGTH ? text.substring(0, MAX_MESSAGE_LENGTH) : text;
    // Control characters have no business in a log line or exception message
    return limited.replaceAll("\\p{Cntrl}", " ");
  }

  /**
   * Returns the payload as a GraphQL error array, or null if it is not a non-empty list of objects
   * that each carry a string {@code message}.
   */
  @SuppressWarnings("unchecked")
  private static @Nullable List<Map<String, Object>> toGraphQLErrors(
      final @Nullable Object payload) {

    if (!(payload instanceof List) || ((List<?>) payload).isEmpty()) {
      return null;
    }

    for (final Object item : (List<?>) payload) {
      if (!(item instanceof Map) || !(((Map<?, ?>) item).get("message") instanceof String)) {
        return null;
      }
    }

    return (List<Map<String, Object>>) payload;
  }

  /**
   * Returns the GraphQL errors to send to a client: the source's error array as it was received if
   * it is well formed, otherwise a single generic error.
   *
   * @return non-empty list of GraphQL errors
   */
  @NotNull
  List<Map<String, Object>> getErrors() {
    final List<Map<String, Object>> errors = toGraphQLErrors(this.payload);
    return errors == null ? genericErrors() : errors;
  }

  static @NotNull List<Map<String, Object>> genericErrors() {
    return List.of(Map.of("message", GENERIC_MESSAGE));
  }

  /**
   * Returns the payload of the source's error message as it was received.
   *
   * @return raw payload, null for a protocol violation
   */
  @Nullable
  Object getPayload() {
    return this.payload;
  }
}
