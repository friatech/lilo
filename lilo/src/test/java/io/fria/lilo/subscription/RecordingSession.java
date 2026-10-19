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

import io.fria.lilo.JsonUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;

/**
 * Test double that records everything sent to it and remembers whether it was closed. It is safe to
 * use from several threads: reactor threads may send while the test thread reads.
 */
final class RecordingSession implements WebSocketSessionWrapper {

  static final String SESSION_ID = "test-session";

  private final @NotNull List<String> sent = new CopyOnWriteArrayList<>();
  private final @NotNull AtomicBoolean open = new AtomicBoolean(true);

  /** Sends connection_init through the gateway handler, as a client would. */
  static @NotNull RecordingSession init(final @NotNull SubscriptionGatewayHandler handler) {
    final RecordingSession session = new RecordingSession();
    handler.handleMessage(session, "{\"type\":\"connection_init\"}");
    return session;
  }

  @Override
  public void close() {
    this.open.set(false);
  }

  @Override
  public String getId() {
    return SESSION_ID;
  }

  @Override
  public boolean isOpen() {
    return this.open.get();
  }

  @Override
  public void send(final @NotNull String message) {
    this.sent.add(message);
  }

  /** Returns a snapshot of the raw messages sent so far, in order. */
  @NotNull
  List<String> sent() {
    return List.copyOf(this.sent);
  }

  /** Returns the sent messages parsed as subscription messages, in order. */
  @NotNull
  List<SubscriptionMessage> sentMessages() {
    final List<SubscriptionMessage> messages = new ArrayList<>();
    for (final String raw : this.sent()) {
      messages.add(JsonUtils.toObj(raw, SubscriptionMessage.class).orElseThrow());
    }
    return messages;
  }
}
