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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.fria.lilo.JsonUtils;
import io.fria.lilo.Lilo;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;

class GatewayWebSocketHandlerTest {

  private static final String INIT = "{\"type\":\"connection_init\"}";

  private final GatewayWebSocketHandler handler =
      new GatewayWebSocketHandler(Lilo.builder().build(), "/graphql");

  private static List<String> types(final RecordingWebSocketSession session) {
    return session.sent().stream()
        .map(frame -> JsonUtils.toMap(frame).orElseThrow().get("type"))
        .map(String::valueOf)
        .toList();
  }

  @Test
  void exposesSubscriptionPath() {
    assertEquals("/graphql", this.handler.getSubscriptionPath());
  }

  @Test
  void connectionInitIsAcknowledged() throws Exception {
    final RecordingWebSocketSession session = new RecordingWebSocketSession();
    this.handler.afterConnectionEstablished(session);

    this.handler.handleMessage(session, new TextMessage(INIT));

    assertEquals(List.of("connection_ack"), types(session));
    assertTrue(session.isOpen());
  }

  @Test
  void pingIsAnsweredWithPong() throws Exception {
    final RecordingWebSocketSession session = new RecordingWebSocketSession();
    this.handler.afterConnectionEstablished(session);

    this.handler.handleMessage(session, new TextMessage(INIT));
    this.handler.handleMessage(session, new TextMessage("{\"type\":\"ping\"}"));

    assertEquals(List.of("connection_ack", "pong"), types(session));
  }

  @Test
  void connectionEstablishedCreatesTheSendLock() throws Exception {
    final RecordingWebSocketSession session = new RecordingWebSocketSession();

    this.handler.afterConnectionEstablished(session);

    assertNotNull(session.getAttributes().get(ClientSessionWrapper.SEND_LOCK_ATTRIBUTE));
  }

  @Test
  void connectionCloseReleasesTheSession() throws Exception {
    final RecordingWebSocketSession first = new RecordingWebSocketSession("same-id");
    this.handler.afterConnectionEstablished(first);
    this.handler.handleMessage(first, new TextMessage(INIT));

    this.handler.afterConnectionClosed(first, CloseStatus.NORMAL);

    // Had the close not been delegated, a second connection_init for the id would close it
    final RecordingWebSocketSession second = new RecordingWebSocketSession("same-id");
    this.handler.afterConnectionEstablished(second);
    this.handler.handleMessage(second, new TextMessage(INIT));
    assertEquals(List.of("connection_ack"), types(second));
    assertTrue(second.isOpen());
  }

  @Test
  void invalidMessageClosesTheSession() throws Exception {
    final RecordingWebSocketSession session = new RecordingWebSocketSession();
    this.handler.afterConnectionEstablished(session);

    this.handler.handleMessage(session, new TextMessage("not json"));

    assertFalse(session.isOpen());
    assertEquals(List.of(), types(session));
  }

  @Test
  void closeOfUnknownSessionIsIgnored() {
    assertDoesNotThrow(() -> this.handler.afterConnectionClosed(null, CloseStatus.NORMAL));
  }
}
