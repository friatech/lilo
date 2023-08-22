package io.fria.lilo.samples.spring_boot_subscription.lilo_gateway;

import io.fria.lilo.GraphQLQuery;
import io.fria.lilo.LiloContext;
import io.fria.lilo.SchemaSource;
import io.fria.lilo.subscription.GraphQLSubscriptionMessage;
import io.fria.lilo.subscription.LiloSubscriptionDefaultClientHandler;
import io.fria.lilo.subscription.SubscriptionRetriever;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import reactor.core.publisher.Sinks;

public class SubscriptionRetrieverImpl extends AbstractWebSocketHandler implements SubscriptionRetriever {

  private final @NotNull String                               subscriptionWsUrl;
  private final @NotNull WebSocketClient                      webSocketClient;
  private                WebSocketSession                     session;
  private final          LiloSubscriptionDefaultClientHandler liloHandler = new LiloSubscriptionDefaultClientHandler();
  private final Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
  private boolean readyForQuerySending;

  public SubscriptionRetrieverImpl(final @NotNull String subscriptionWsUrl) {
    this.subscriptionWsUrl = subscriptionWsUrl;
    this.webSocketClient = new StandardWebSocketClient();
  }

  @Override
  public void connect(@NotNull final LiloContext liloContext, @NotNull final SchemaSource schemaSource, @Nullable final Object localContext) {

    try {
      final WebSocketHttpHeaders httpHeaders = new WebSocketHttpHeaders();
      httpHeaders.add("Sec-WebSocket-Protocol", "graphql-transport-ws");
      httpHeaders.add("Sec-WebSocket-Version", "13");
      httpHeaders.add("Sec-WebSocket-Extensions", "permessage-deflate; client_max_window_bits");
      this.session = this.webSocketClient.execute(this, httpHeaders, new URI(this.subscriptionWsUrl)).get();
      this.liloHandler.startHandShaking(m -> SubscriptionRetrieverImpl.this.session.sendMessage(new TextMessage(m)));
    } catch (final URISyntaxException | InterruptedException | ExecutionException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void sendQuery(
    @NotNull final LiloContext liloContext,
    @NotNull final SchemaSource schemaSource,
    @NotNull final GraphQLQuery query,
    @Nullable final Object localContext) {

    // TODO: A Wait mechanism is needed

    if (this.readyForQuerySending) {
      try {
        this.liloHandler.sendQuery(query, m -> SubscriptionRetrieverImpl.this.session.sendMessage(new TextMessage(m)));
      } catch (final IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  protected void handleTextMessage(final @NotNull WebSocketSession session, final @NotNull TextMessage message) throws Exception {

    final GraphQLSubscriptionMessage subscriptionMessage = this.liloHandler.convertToSubscriptionMessage(message.getPayload());

    if ("connection_ack".equals(subscriptionMessage.getType())) {
      this.readyForQuerySending = true;
    } else if ("next".equals(subscriptionMessage.getType())) {
      this.sink.tryEmitNext((String) subscriptionMessage.getPayload());
    }
  }

  @Override
  public @NotNull Publisher<String> subscribe(
    @NotNull final LiloContext liloContext,
    @NotNull final SchemaSource schemaSource,
    @Nullable final Object localContext) {

    return this.sink.asFlux();
  }
}
