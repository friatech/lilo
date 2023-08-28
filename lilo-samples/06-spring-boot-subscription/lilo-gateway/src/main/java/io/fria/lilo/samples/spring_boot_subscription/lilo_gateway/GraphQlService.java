package io.fria.lilo.samples.spring_boot_subscription.lilo_gateway;

import graphql.execution.reactive.SubscriptionPublisher;
import io.fria.lilo.GraphQLRequest;
import io.fria.lilo.JsonUtils;
import io.fria.lilo.Lilo;
import io.fria.lilo.subscription.GraphQLSubscriptionMessage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Service
public class GraphQlService {

  private final Lilo lilo;

  private @NotNull final Map<String, Sinks.Many<Object>> connectionPublishers =
      new ConcurrentHashMap<>();

  public GraphQlService(final Lilo lilo) {
    this.lilo = lilo;
  }

  @Async
  public void handleMessage(
      final @NotNull WebSocketSession session, final @NotNull TextMessage message)
      throws IOException {

    // --- Request

    final String wsMessage = message.getPayload();

    final Optional<GraphQLSubscriptionMessage> requestOptional =
        JsonUtils.toObj(wsMessage, GraphQLSubscriptionMessage.class);

    if (requestOptional.isEmpty()) {
      return;
    }

    final GraphQLSubscriptionMessage request = requestOptional.get();

    if ("connection_init".equals(request.getType())) {
      final GraphQLSubscriptionMessage response = new GraphQLSubscriptionMessage();
      response.setType("connection_ack");
      response.setPayload(new HashMap<>());
      this.sendResponse(session, response);
    } else if ("subscribe".equals(request.getType())) {
      final Optional<GraphQLRequest> graphQLRequest =
          JsonUtils.toObj(JsonUtils.toStr(request.getPayload()), GraphQLRequest.class);

      if (graphQLRequest.isEmpty()) {
        return;
      }

      final GraphQLSubscriptionMessage response = new GraphQLSubscriptionMessage();
      response.setId(request.getId());
      response.setType("next");

      final Map<String, Object> stitchResult =
          this.lilo.stitch(graphQLRequest.get().toExecutionInput()).toSpecification();
      final SubscriptionPublisher publisher = (SubscriptionPublisher) stitchResult.get("data");

      this.connectionPublishers.put(
          session.getId(), (Sinks.Many<Object>) publisher.getUpstreamPublisher());

      final HashMap<Object, Object> payloadMap = new HashMap<>();
      payloadMap.put("data", stitchResult);
      response.setPayload(payloadMap);

      this.sendResponse(session, response);
    } else if ("complete".equals(request.getType())) {
      try {
        session.close(CloseStatus.NORMAL);

        if (this.connectionPublishers.containsKey(session.getId())) {
          this.connectionPublishers.get(session.getId()).tryEmitComplete();
          this.connectionPublishers.remove(session.getId());
        }
      } catch (final IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private void sendResponse(
      final @NotNull WebSocketSession session, final @NotNull GraphQLSubscriptionMessage response)
      throws IOException {

    if ("next".equals(response.getType())) {
      final Map<String, Object> payload = (Map<String, Object>) response.getPayload();
      final Map<String, Object> data = (Map<String, Object>) payload.get("data");
      // TODO: data inside data. that's suspicious

      final SubscriptionPublisher publisher = (SubscriptionPublisher) data.get("data");

      final Flux<Object> upstreamPublisher = (Flux<Object>) publisher.getUpstreamPublisher();

      upstreamPublisher.subscribe(
          o -> {
            final GraphQLSubscriptionMessage subscriptionMessage = new GraphQLSubscriptionMessage();
            subscriptionMessage.setId(response.getId());
            subscriptionMessage.setType("next");
            subscriptionMessage.setPayload(o);
            try {
              session.sendMessage(new TextMessage(JsonUtils.toStr(subscriptionMessage)));
            } catch (final IOException e) {
              throw new RuntimeException(e);
            }
          },
          throwable -> {},
          () -> {
            try {
              session.close(CloseStatus.NORMAL);
            } catch (final IOException e) {
              throw new RuntimeException(e);
            }
          });
    } else {
      session.sendMessage(new TextMessage(JsonUtils.toStr(response)));
    }
  }
}
