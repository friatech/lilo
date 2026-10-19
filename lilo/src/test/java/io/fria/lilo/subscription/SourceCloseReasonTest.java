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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import io.fria.lilo.GraphQLQuery;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** The close status of a source session is part of the failure it causes (issue #26). */
class SourceCloseReasonTest {

  private static String failureMessage(final String closeReason) {
    final SubscriptionSourcePublisher publisher = new SubscriptionSourcePublisher();
    final SubscriptionSourceHandler handler =
        new SubscriptionSourceHandler(mock(GraphQLQuery.class), publisher);

    handler.handleSessionClose(new RecordingSession("source"), closeReason);

    return assertThrows(
            SubscriptionSourceException.class,
            () -> publisher.getFlux().blockLast(Duration.ofSeconds(2)))
        .getMessage();
  }

  @Test
  void closeReasonIsAppended() {
    assertEquals(
        "Subscription source closed the connection: 1009 The decoded text message was too big for"
            + " the output buffer and the endpoint does not support partial messages",
        failureMessage(
            "1009 The decoded text message was too big for the output buffer and the endpoint does"
                + " not support partial messages"));
  }

  @Test
  void missingReasonKeepsTheGenericMessage() {
    assertEquals("Subscription source closed the connection", failureMessage(null));
    assertEquals("Subscription source closed the connection", failureMessage("  "));
  }

  @Test
  void reasonIsBoundedAndSanitised() {
    final String message = failureMessage("1006 line\nbreak " + "x".repeat(300));
    assertEquals(
        "Subscription source closed the connection: 1006 line break " + "x".repeat(200 - 16),
        message);
  }
}
