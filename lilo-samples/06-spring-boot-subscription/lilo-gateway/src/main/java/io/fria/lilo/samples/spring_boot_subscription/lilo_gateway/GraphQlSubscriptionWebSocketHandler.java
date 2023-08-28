/*
 * Copyright 2023 the original author or authors.
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
package io.fria.lilo.samples.spring_boot_subscription.lilo_gateway;

import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

@Component
public class GraphQlSubscriptionWebSocketHandler extends AbstractWebSocketHandler {

  private final GraphQlService graphQlService;

  public GraphQlSubscriptionWebSocketHandler(final GraphQlService graphQlService) {
    this.graphQlService = graphQlService;
  }

  @Override
  protected void handleTextMessage(
      final @NotNull WebSocketSession session, final @NotNull TextMessage message)
      throws IOException {

    this.graphQlService.handleMessage(session, message);
  }
}
