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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

class ClientSessionWrapperTest {

  private static final int SENDERS = 8;
  private static final int FRAMES_PER_SENDER = 5;
  private static final int TIME_LIMIT = 10_000;
  private static final int BUFFER_LIMIT = 512 * 1024;

  private static String frame(final int id) {
    return "{\"id\":\"" + id + "\",\"type\":\"next\",\"payload\":{}}";
  }

  private static ClientSessionWrapper wrap(final WebSocketSession nativeSession) {
    return Objects.requireNonNull(ClientSessionWrapper.wrap(nativeSession));
  }

  private static WebSocketSession mockSession(final boolean open) {
    final WebSocketSession nativeSession = mock(WebSocketSession.class);
    when(nativeSession.isOpen()).thenReturn(open);
    when(nativeSession.getAttributes()).thenReturn(new HashMap<>());
    return nativeSession;
  }

  @Test
  void sendWritesTextMessageWhenOpen() throws IOException {
    final WebSocketSession nativeSession = mockSession(true);

    wrap(nativeSession).send("{\"type\":\"pong\"}");

    final ArgumentCaptor<WebSocketMessage<?>> captor =
        ArgumentCaptor.forClass(WebSocketMessage.class);
    verify(nativeSession).sendMessage(captor.capture());
    final TextMessage message = assertInstanceOf(TextMessage.class, captor.getValue());
    assertEquals("{\"type\":\"pong\"}", message.getPayload());
  }

  @Test
  void sendSkipsClosedSession() throws IOException {
    final WebSocketSession nativeSession = mockSession(false);

    wrap(nativeSession).send("{\"type\":\"pong\"}");

    verify(nativeSession, never()).sendMessage(any());
  }

  @Test
  void sendSwallowsIllegalStateException() throws IOException {
    final WebSocketSession nativeSession = mockSession(true);
    doThrow(new IllegalStateException("closed")).when(nativeSession).sendMessage(any());

    wrap(nativeSession).send("{\"type\":\"pong\"}");

    verify(nativeSession).sendMessage(any());
  }

  @Test
  void sendWrapsIoException() throws IOException {
    final WebSocketSession nativeSession = mockSession(true);
    final IOException failure = new IOException("broken pipe");
    doThrow(failure).when(nativeSession).sendMessage(any());

    final ClientSessionWrapper session = wrap(nativeSession);
    final RuntimeException thrown =
        assertThrows(RuntimeException.class, () -> session.send("{\"type\":\"pong\"}"));
    assertSame(failure, thrown.getCause());
  }

  @Test
  void closeWrapsIoException() throws IOException {
    final WebSocketSession nativeSession = mockSession(true);
    final IOException failure = new IOException("broken pipe");
    doThrow(failure).when(nativeSession).close();

    final ClientSessionWrapper session = wrap(nativeSession);
    final RuntimeException thrown = assertThrows(RuntimeException.class, session::close);
    assertSame(failure, thrown.getCause());
  }

  @Test
  void wrapNullIsNull() {
    assertNull(ClientSessionWrapper.wrap(null));
  }

  @Test
  void delegatesIdOpenAndAttributes() {
    final RecordingWebSocketSession nativeSession = new RecordingWebSocketSession("s-1");
    nativeSession.getAttributes().put("key", "value");

    final ClientSessionWrapper session = wrap(nativeSession);

    assertEquals("s-1", session.getId());
    assertTrue(session.isOpen());
    assertSame(nativeSession.getAttributes(), session.getAttributes());
    assertEquals("value", session.getAttributes().get("key"));
    nativeSession.close();
    assertFalse(session.isOpen());
  }

  @Test
  void wrappersOfOneSessionShareTheSendLock() {
    final RecordingWebSocketSession nativeSession = new RecordingWebSocketSession();

    wrap(nativeSession);
    final Object lock = nativeSession.getAttributes().get(ClientSessionWrapper.SEND_LOCK_ATTRIBUTE);
    wrap(nativeSession);

    assertSame(lock, nativeSession.getAttributes().get(ClientSessionWrapper.SEND_LOCK_ATTRIBUTE));
  }

  @Test
  void sessionWithUnmodifiableAttributesStillSends() throws IOException {
    final WebSocketSession nativeSession = mock(WebSocketSession.class);
    when(nativeSession.isOpen()).thenReturn(true);
    when(nativeSession.getAttributes()).thenReturn(Map.of());

    wrap(nativeSession).send("{\"type\":\"pong\"}");

    verify(nativeSession).sendMessage(any());
  }

  @Test
  void decoratingUnmodifiableAttributesFallsBackToTheLock() throws IOException {
    final WebSocketSession nativeSession = mock(WebSocketSession.class);
    when(nativeSession.isOpen()).thenReturn(true);
    when(nativeSession.getAttributes()).thenReturn(Map.of());

    ClientSessionWrapper.decorate(nativeSession, TIME_LIMIT, BUFFER_LIMIT)
        .send("{\"type\":\"pong\"}");

    // Sent straight to the native session, as no decorator could be stored
    verify(nativeSession).sendMessage(any());
  }

  @Test
  void sessionIsDecoratedOnce() {
    final RecordingWebSocketSession nativeSession = new RecordingWebSocketSession();

    ClientSessionWrapper.decorate(nativeSession, TIME_LIMIT, BUFFER_LIMIT);
    final Object decorator =
        nativeSession.getAttributes().get(ClientSessionWrapper.DECORATOR_ATTRIBUTE);
    ClientSessionWrapper.decorate(nativeSession, TIME_LIMIT, BUFFER_LIMIT);
    wrap(nativeSession);

    assertInstanceOf(ConcurrentWebSocketSessionDecorator.class, decorator);
    assertSame(
        decorator, nativeSession.getAttributes().get(ClientSessionWrapper.DECORATOR_ATTRIBUTE));
    // The decorated path takes no send lock
    assertNull(nativeSession.getAttributes().get(ClientSessionWrapper.SEND_LOCK_ATTRIBUTE));
  }

  @Test
  void sendsToABlockedSessionDoNotBlockOtherSenders() throws Exception {
    final RecordingWebSocketSession nativeSession = new RecordingWebSocketSession();
    final CountDownLatch release = new CountDownLatch(1);
    ClientSessionWrapper.decorate(nativeSession, TIME_LIMIT, BUFFER_LIMIT);
    nativeSession.blockSendsUntil(release);
    final ExecutorService senders = Executors.newFixedThreadPool(3);

    try {
      // The first sender writes and stays blocked, like a client that stopped reading
      final Future<?> blocked = senders.submit(() -> wrap(nativeSession).send(frame(0)));
      assertTrue(nativeSession.awaitFirstSend(5, TimeUnit.SECONDS));

      // Other senders queue their frame and return at once
      senders.submit(() -> wrap(nativeSession).send(frame(1))).get(2, TimeUnit.SECONDS);
      senders.submit(() -> wrap(nativeSession).send(frame(2))).get(2, TimeUnit.SECONDS);
      assertFalse(blocked.isDone());

      // The writing thread flushes the queued frames once the client reads again
      release.countDown();
      blocked.get(5, TimeUnit.SECONDS);
    } finally {
      release.countDown();
      senders.shutdownNow();
    }

    assertEquals(List.of(frame(0), frame(1), frame(2)), nativeSession.sent());
    assertTrue(nativeSession.isOpen());
  }

  @Test
  void exceedingTheBufferSizeLimitClosesTheSession() throws Exception {
    final RecordingWebSocketSession nativeSession = new RecordingWebSocketSession();
    final CountDownLatch release = new CountDownLatch(1);
    // The frame in flight does not count; room for one queued frame, not for two
    ClientSessionWrapper.decorate(nativeSession, TIME_LIMIT, frame(0).length() + 1);
    nativeSession.blockSendsUntil(release);
    final ExecutorService senders = Executors.newFixedThreadPool(2);

    try {
      final Future<?> blocked = senders.submit(() -> wrap(nativeSession).send(frame(0)));
      assertTrue(nativeSession.awaitFirstSend(5, TimeUnit.SECONDS));

      System.err.println(
          "DBG buffer="
              + ((ConcurrentWebSocketSessionDecorator)
                      nativeSession.getAttributes().get(ClientSessionWrapper.DECORATOR_ATTRIBUTE))
                  .getBufferSize()
              + " limit="
              + ((ConcurrentWebSocketSessionDecorator)
                      nativeSession.getAttributes().get(ClientSessionWrapper.DECORATOR_ATTRIBUTE))
                  .getBufferSizeLimit());
      senders.submit(() -> wrap(nativeSession).send(frame(1))).get(2, TimeUnit.SECONDS);
      assertTrue(nativeSession.isOpen());

      // Does not throw: the wrapper closes the session instead
      senders.submit(() -> wrap(nativeSession).send(frame(2))).get(2, TimeUnit.SECONDS);

      assertEquals(CloseStatus.SESSION_NOT_RELIABLE, nativeSession.closeStatus());
      assertFalse(nativeSession.isOpen());
      release.countDown();
      blocked.get(5, TimeUnit.SECONDS);
    } finally {
      release.countDown();
      senders.shutdownNow();
    }

    // Later sends to the closed session are dropped
    wrap(nativeSession).send(frame(3));
    assertFalse(nativeSession.sent().contains(frame(3)));
  }

  @Test
  void decoratedConcurrentSendsAreIntactAndInOrderPerThread() throws Exception {
    final RecordingWebSocketSession nativeSession = new RecordingWebSocketSession();
    nativeSession.linger();
    ClientSessionWrapper.decorate(nativeSession, TIME_LIMIT, BUFFER_LIMIT);
    final CountDownLatch start = new CountDownLatch(1);
    final ExecutorService senders = Executors.newFixedThreadPool(SENDERS);

    try {
      final List<Future<?>> sends =
          IntStream.range(0, SENDERS)
              .<Future<?>>mapToObj(
                  sender ->
                      senders.submit(
                          () -> {
                            start.await();
                            for (int i = 0; i < FRAMES_PER_SENDER; i++) {
                              wrap(nativeSession).send(sender + ":" + i);
                            }
                            return null;
                          }))
              .toList();
      start.countDown();
      for (final Future<?> send : sends) {
        send.get(30, TimeUnit.SECONDS);
      }
    } finally {
      senders.shutdownNow();
    }

    assertFalse(nativeSession.overlapped(), "writes to the session overlapped");
    assertTrue(nativeSession.isOpen());
    assertEquals(SENDERS * FRAMES_PER_SENDER, nativeSession.sent().size());
    for (int sender = 0; sender < SENDERS; sender++) {
      final String prefix = sender + ":";
      final List<String> ofSender =
          nativeSession.sent().stream().filter(frame -> frame.startsWith(prefix)).toList();
      assertEquals(
          IntStream.range(0, FRAMES_PER_SENDER).mapToObj(i -> prefix + i).toList(), ofSender);
    }
  }

  @Test
  void concurrentSendsAreSerialised() throws Exception {
    final RecordingWebSocketSession nativeSession = new RecordingWebSocketSession();
    nativeSession.linger();
    final CountDownLatch start = new CountDownLatch(1);
    final ExecutorService senders = Executors.newFixedThreadPool(SENDERS);

    try {
      final List<Future<?>> sends =
          IntStream.range(0, SENDERS)
              .<Future<?>>mapToObj(
                  i ->
                      senders.submit(
                          () -> {
                            start.await();
                            // A new wrapper per send, as the handlers create one per callback
                            wrap(nativeSession).send("{\"id\":\"" + i + "\",\"type\":\"next\"}");
                            return null;
                          }))
              .toList();
      start.countDown();
      for (final Future<?> send : sends) {
        send.get(10, TimeUnit.SECONDS);
      }
    } finally {
      senders.shutdownNow();
    }

    assertFalse(nativeSession.overlapped(), "sends of one session overlapped");
    final Set<String> expected = new HashSet<>();
    for (int i = 0; i < SENDERS; i++) {
      expected.add("{\"id\":\"" + i + "\",\"type\":\"next\"}");
    }
    assertEquals(SENDERS, nativeSession.sent().size());
    assertEquals(expected, new HashSet<>(nativeSession.sent()));
  }
}
