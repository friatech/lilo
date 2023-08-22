package io.fria.lilo.samples.spring_boot_subscription.lilo_gateway;

import io.fria.lilo.JsonUtils;
import io.fria.lilo.Lilo;
import io.fria.lilo.subscription.GraphQLSubscriptionMessage;
import io.fria.lilo.subscription.LiloSubscriptionDefaultClientHandler;
import io.fria.lilo.subscription.LiloSubscriptionDefaultServerHandler;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

@Controller
public class SampleWebSocketSender extends AbstractWebSocketHandler {

  private final WebSocketClient webSocketClient;
  private final WebSocketSession session;
  private final Lilo                                 lilo;
  private final LiloSubscriptionDefaultClientHandler defaultHandler;

  public SampleWebSocketSender(final Lilo lilo) {

    this.lilo = lilo;
    this.defaultHandler = new LiloSubscriptionDefaultClientHandler(this.lilo);
    this.webSocketClient = new StandardWebSocketClient();

    new LiloSubscriptionDefaultServerHandler(this.lilo);

    try {
      final WebSocketHttpHeaders httpHeaders = new WebSocketHttpHeaders();
      httpHeaders.add("Sec-WebSocket-Protocol", "graphql-transport-ws");
      httpHeaders.add("Sec-WebSocket-Version", "13");
      httpHeaders.add("Sec-WebSocket-Extensions", "permessage-deflate; client_max_window_bits");
      this.session = this.webSocketClient.execute(this, httpHeaders, new URI("ws://localhost:8081/graphql")).get();
      this.startHandShaking();
    } catch (final URISyntaxException | InterruptedException | ExecutionException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  @GetMapping("/send")
  public @NotNull void send() {

  }

  @Override
  protected void handleTextMessage(final WebSocketSession session, final TextMessage message) throws IOException {

    final GraphQLSubscriptionMessage subscriptionMessage = this.convertToSubscriptionMessage(message);

    if ("connection_ack".equals(subscriptionMessage.getType())) {
      this.sendQuery();
    } else if ("next".equals(subscriptionMessage.getType())) {
      System.out.println(subscriptionMessage.getPayload());
    }
  }

  private void sendQuery() throws IOException {
    final GraphQLSubscriptionMessage response = new GraphQLSubscriptionMessage();
    response.setId(UUID.randomUUID().toString());
    response.setType("subscribe");

    final HashMap<String, Object> payload = new HashMap<>();
    payload.put("query", "subscription {\n  greeting1Subscription\n}");
    payload.put("variables", new HashMap<>());

    response.setPayload(payload);
    this.session.sendMessage(new TextMessage(JsonUtils.toStr(response)));
  }

  private GraphQLSubscriptionMessage convertToSubscriptionMessage(final TextMessage message) {

    final Optional<GraphQLSubscriptionMessage> requestOptional =
      JsonUtils.toObj(message.getPayload(), GraphQLSubscriptionMessage.class);

      return requestOptional.orElse(null);
  }

  private void startHandShaking() throws IOException {
    this.session.sendMessage(new TextMessage("{\"type\":\"connection_init\",\"payload\":{}}"));
  }
}
