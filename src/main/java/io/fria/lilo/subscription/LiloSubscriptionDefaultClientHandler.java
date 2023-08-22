package io.fria.lilo.subscription;

import io.fria.lilo.GraphQLQuery;
import io.fria.lilo.JsonUtils;
import java.io.IOException;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

public class LiloSubscriptionDefaultClientHandler implements LiloSubscriptionHandler {

  public LiloSubscriptionDefaultClientHandler() {}

  @Override
  public @NotNull Object handleMessage(final @NotNull String wsMessage) {
    return null;
  }

  public void startHandShaking(final @NotNull SubscriptionMessageSender subscriptionMessageSender)
      throws IOException {

    final GraphQLSubscriptionMessage initMessage = new GraphQLSubscriptionMessage();
    initMessage.setType("connection_init");
    initMessage.setPayload(new HashMap<>());

    Objects.requireNonNull(subscriptionMessageSender).send(JsonUtils.toStr(initMessage));
  }

  public GraphQLSubscriptionMessage convertToSubscriptionMessage(
      final @NotNull String jsonMessage) {

    final Optional<GraphQLSubscriptionMessage> requestOptional =
        JsonUtils.toObj(jsonMessage, GraphQLSubscriptionMessage.class);

    return requestOptional.orElse(null);
  }

  public void sendQuery(
      final @NotNull GraphQLQuery query,
      final @NotNull SubscriptionMessageSender subscriptionMessageSender)
      throws IOException {
    final GraphQLSubscriptionMessage response = new GraphQLSubscriptionMessage();
    response.setId(UUID.randomUUID().toString());
    response.setType("subscribe");

    response.setPayload(Objects.requireNonNull(query.getQuery()));
    Objects.requireNonNull(subscriptionMessageSender).send(JsonUtils.toStr(response));
  }
}
