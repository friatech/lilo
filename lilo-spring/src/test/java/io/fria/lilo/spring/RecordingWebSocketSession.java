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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Spring {@link WebSocketSession} test double that records the text frames sent to it. Like a real
 * container session, it is not meant to be entered by two senders at once: it records an overlap
 * when that happens. Each send lingers until a second sender enters or a short timeout passes, so
 * unserialised concurrent senders are caught reliably.
 */
final class RecordingWebSocketSession implements WebSocketSession {

  private static final long LINGER_MILLIS = 20;

  private final @NotNull String id;
  private final @NotNull Map<String, Object> attributes = new ConcurrentHashMap<>();
  private final @NotNull List<String> sent = new CopyOnWriteArrayList<>();
  private final @NotNull AtomicBoolean open = new AtomicBoolean(true);
  private final @NotNull AtomicInteger inFlight = new AtomicInteger();
  private final @NotNull AtomicBoolean overlapped = new AtomicBoolean();
  private final @NotNull CountDownLatch secondSender = new CountDownLatch(1);
  private volatile boolean linger;
  private volatile @Nullable CountDownLatch release;
  private final @NotNull CountDownLatch firstSendEntered = new CountDownLatch(1);
  private volatile @Nullable CloseStatus closeStatus;
  private final @NotNull CountDownLatch closed = new CountDownLatch(1);
  private volatile @Nullable Exception closeFailure;

  RecordingWebSocketSession() {
    this("spring-session");
  }

  RecordingWebSocketSession(final @NotNull String id) {
    this.id = id;
  }

  /** Makes every send wait briefly for a concurrent sender, to expose missing serialisation. */
  void linger() {
    this.linger = true;
  }

  /**
   * Makes every send block until the given latch is released, like a client that stopped reading.
   */
  void blockSendsUntil(final @NotNull CountDownLatch releaseLatch) {
    this.release = releaseLatch;
  }

  /** Waits until a send entered this session, e.g. one that is now blocked. */
  boolean awaitFirstSend(final long timeout, final @NotNull TimeUnit unit)
      throws InterruptedException {
    return this.firstSendEntered.await(timeout, unit);
  }

  /** Makes close fail with the given IOException or RuntimeException, leaving the session open. */
  void failCloseWith(final @NotNull Exception failure) {
    this.closeFailure = failure;
  }

  /** Waits until the session is closed. */
  boolean awaitClosed(final long timeout, final @NotNull TimeUnit unit)
      throws InterruptedException {
    return this.closed.await(timeout, unit);
  }

  /** The status the session was closed with, or null while it is open. */
  @Nullable
  CloseStatus closeStatus() {
    return this.closeStatus;
  }

  boolean overlapped() {
    return this.overlapped.get();
  }

  @NotNull
  List<String> sent() {
    return this.sent;
  }

  @Override
  public void sendMessage(final @NotNull WebSocketMessage<?> message) throws IOException {
    if (this.inFlight.incrementAndGet() > 1) {
      this.overlapped.set(true);
      this.secondSender.countDown();
    }
    this.firstSendEntered.countDown();
    try {
      final CountDownLatch releaseLatch = this.release;
      if (releaseLatch != null && !releaseLatch.await(30, TimeUnit.SECONDS)) {
        throw new IOException("send was never released");
      }
      if (this.linger) {
        this.secondSender.await(LINGER_MILLIS, TimeUnit.MILLISECONDS);
      }
      this.sent.add(((TextMessage) message).getPayload());
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException(e);
    } finally {
      this.inFlight.decrementAndGet();
    }
  }

  @Override
  public @NotNull String getId() {
    return this.id;
  }

  @Override
  public @Nullable URI getUri() {
    return null;
  }

  @Override
  public @NotNull HttpHeaders getHandshakeHeaders() {
    return new HttpHeaders();
  }

  @Override
  public @NotNull Map<String, Object> getAttributes() {
    return this.attributes;
  }

  @Override
  public @Nullable Principal getPrincipal() {
    return null;
  }

  @Override
  public @Nullable InetSocketAddress getLocalAddress() {
    return null;
  }

  @Override
  public @Nullable InetSocketAddress getRemoteAddress() {
    return null;
  }

  @Override
  public @Nullable String getAcceptedProtocol() {
    return null;
  }

  @Override
  public void setTextMessageSizeLimit(final int messageSizeLimit) {
    // Not limited
  }

  @Override
  public int getTextMessageSizeLimit() {
    return Integer.MAX_VALUE;
  }

  @Override
  public void setBinaryMessageSizeLimit(final int messageSizeLimit) {
    // Not limited
  }

  @Override
  public int getBinaryMessageSizeLimit() {
    return Integer.MAX_VALUE;
  }

  @Override
  public @NotNull List<WebSocketExtension> getExtensions() {
    return List.of();
  }

  @Override
  public boolean isOpen() {
    return this.open.get();
  }

  @Override
  public void close() throws IOException {
    this.close(CloseStatus.NORMAL);
  }

  @Override
  public void close(final @NotNull CloseStatus status) throws IOException {
    final Exception failure = this.closeFailure;
    if (failure instanceof IOException) {
      throw (IOException) failure;
    }
    if (failure instanceof RuntimeException) {
      throw (RuntimeException) failure;
    }
    if (this.open.compareAndSet(true, false)) {
      this.closeStatus = status;
      this.closed.countDown();
    }
  }
}
