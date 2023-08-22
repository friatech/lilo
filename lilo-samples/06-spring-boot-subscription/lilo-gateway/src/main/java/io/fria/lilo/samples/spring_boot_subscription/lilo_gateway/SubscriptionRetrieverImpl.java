package io.fria.lilo.samples.spring_boot_subscription.lilo_gateway;

import io.fria.lilo.GraphQLQuery;
import io.fria.lilo.LiloContext;
import io.fria.lilo.SchemaSource;
import io.fria.lilo.subscription.SubscriptionRetriever;
import java.net.URI;
import java.net.URISyntaxException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.StandardWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Mono;

public class SubscriptionRetrieverImpl implements SubscriptionRetriever, WebSocketHandler {

  private final @NotNull String           schemaUrl;
  private final @NotNull WebSocketClient  webSocketClient;
  private                WebSocketSession session;

  public SubscriptionRetrieverImpl(final String schemaUrl) {
    this.schemaUrl = (schemaUrl + "/graphql").replaceAll("http", "ws");
    this.webSocketClient = new StandardWebSocketClient();
  }

  @Override
  public void connect(@NotNull final LiloContext liloContext, @NotNull final SchemaSource schemaSource, @Nullable final Object localContext) {

    try {
      this.webSocketClient.execute(new URI(this.schemaUrl), this).block();
    } catch (final URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Mono<Void> handle(final WebSocketSession session) {
    this.session = session;
    return Mono.empty();
  }

  @Override
  public @NotNull Publisher<String> subscribe(
    @NotNull final LiloContext liloContext,
    @NotNull final SchemaSource schemaSource,
    @NotNull final GraphQLQuery query,
    @Nullable final Object localContext) {

    final GraphQlStringPublisher  publisherToGateway    = new GraphQlStringPublisher();
    final GraphQlMessagePublisher publisherToRemoteSite = new GraphQlMessagePublisher(this.session, query, publisherToGateway);

    this.session.send(publisherToRemoteSite).block();

//    this.session.textMessage(query.getQuery())
//
//    session.receive()
//      .subscribe(new Consumer<WebSocketMessage>() {
//        @Override
//        public void accept(final WebSocketMessage webSocketMessage) {
//
//        }
//      });
//
//
//
//    final Publisher<WebSocketMessage> publisher = new Publisher<>() {
//
//
//      @Override
//      public void subscribe(final Subscriber<? super WebSocketMessage> subscriber) {
//        subscriber.onNext();
//      }
//    };
//
//    this.session.send(publisher).block();
//
//    return publisher;
    return publisherToGateway;
  }
}

class GraphQlStringPublisher implements Publisher<String> {

  public GraphQlStringPublisher() {
  }

  @Override
  public void subscribe(final Subscriber<? super String> s) {

  }
}

class GraphQlMessagePublisher implements Publisher<WebSocketMessage> {

  private final GraphQlStringPublisher outputPublisher;
  private final WebSocketSession session;
  private final GraphQLQuery query;

  public GraphQlMessagePublisher(final WebSocketSession session, final @NotNull GraphQLQuery query, final GraphQlStringPublisher outputPublisher) {
    this.session = session;
    this.query = query;
    this.outputPublisher = outputPublisher;
  }

  @Override
  public void subscribe(final Subscriber<? super WebSocketMessage> subscriber) {

  }
}
