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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The registry's per-session operation cap and the ownership of a session's operations. */
class GatewaySessionRegistryTest {

  private static GatewayOperation registered(
      final GatewaySessionRegistry registry, final RecordingSession session, final String id) {
    final GatewaySessionRegistry.Registration registration = registry.register(session, id);
    assertNull(registration.rejection());
    return registration.operation();
  }

  @Test
  void registersUpToTheCapThenRejectsWithTooManyOperations() {
    final GatewaySessionRegistry registry = new GatewaySessionRegistry(2);
    final RecordingSession session = new RecordingSession();

    assertNotNull(registered(registry, session, "a"));
    assertNotNull(registered(registry, session, "b"));

    final GatewaySessionRegistry.Registration third = registry.register(session, "c");
    assertNull(third.operation());
    assertEquals(GatewaySessionRegistry.Rejection.TOO_MANY_OPERATIONS, third.rejection());
    assertFalse(registry.hasOperation(session.getId(), "c"));
  }

  @Test
  void aCompletedOperationWhoseStitchIsStillRunningKeepsCountingAgainstTheCap() {
    final GatewaySessionRegistry registry = new GatewaySessionRegistry(1);
    final RecordingSession session = new RecordingSession();
    final GatewayOperation first = registered(registry, session, "a");

    first.state().stitchSubmitted();
    registry.cancelOperation(session.getId(), "a");

    // No registered operation is left, yet the running stitch still holds a pool thread
    assertFalse(registry.hasOperation(session.getId(), "a"));
    assertEquals(
        GatewaySessionRegistry.Rejection.TOO_MANY_OPERATIONS,
        registry.register(session, "b").rejection());

    first.state().stitchFinished();
    assertNotNull(registered(registry, session, "b"));
  }

  @Test
  void capIsTheLargerOfOperationsAndInFlightStitchesNotTheirSum() {
    final GatewaySessionRegistry registry = new GatewaySessionRegistry(2);
    final RecordingSession session = new RecordingSession();
    final GatewayOperation first = registered(registry, session, "a");

    // One registered operation with its own stitch in flight: usage is 1, not 2
    first.state().stitchSubmitted();

    assertNotNull(registered(registry, session, "b"));
    assertEquals(
        GatewaySessionRegistry.Rejection.TOO_MANY_OPERATIONS,
        registry.register(session, "c").rejection());
  }

  @Test
  void aDuplicateIdClosesTheSessionAndKeepsTheFirstOperation() {
    final GatewaySessionRegistry registry = new GatewaySessionRegistry(5);
    final RecordingSession session = new RecordingSession();
    final GatewayOperation first = registered(registry, session, "a");

    final GatewaySessionRegistry.Registration duplicate = registry.register(session, "a");

    assertNull(duplicate.operation());
    assertEquals(GatewaySessionRegistry.Rejection.CLOSE_SESSION, duplicate.rejection());
    assertTrue(first.isRegistered());
  }

  @Test
  void aClosedSessionIsRejectedAndLeavesNothingRegistered() {
    final GatewaySessionRegistry registry = new GatewaySessionRegistry(5);
    final RecordingSession session = new RecordingSession();
    session.close();

    final GatewaySessionRegistry.Registration registration = registry.register(session, "a");

    assertEquals(GatewaySessionRegistry.Rejection.CLOSE_SESSION, registration.rejection());
    assertFalse(registry.hasOperation(session.getId(), "a"));
  }

  @Test
  void closeSessionCancelsAndUnregistersEveryOperation() {
    final GatewaySessionRegistry registry = new GatewaySessionRegistry(5);
    final RecordingSession session = new RecordingSession();
    final GatewayOperation a = registered(registry, session, "a");
    final GatewayOperation b = registered(registry, session, "b");
    final SubscriptionSourcePublisher upstream = new SubscriptionSourcePublisher();
    assertTrue(b.attach(upstream));

    registry.closeSession(session);

    assertFalse(session.isOpen());
    assertFalse(registry.tracksSession(session.getId()));
    assertFalse(a.isRegistered());
    assertFalse(b.isRegistered());
    assertFalse(a.attach(new SubscriptionSourcePublisher()), "cancelled");
    assertTrue(upstream.getTermination().toFuture().isDone(), "upstream closed");
  }

  @Test
  void releaseOnlyRemovesTheOperationItself() {
    final GatewaySessionRegistry registry = new GatewaySessionRegistry(5);
    final RecordingSession session = new RecordingSession();
    final GatewayOperation operation = registered(registry, session, "a");

    assertTrue(operation.release());
    assertFalse(operation.release());
    assertFalse(operation.isRegistered());
  }
}
