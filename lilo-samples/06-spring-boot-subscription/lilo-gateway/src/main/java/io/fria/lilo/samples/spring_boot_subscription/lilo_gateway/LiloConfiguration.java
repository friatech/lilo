package io.fria.lilo.samples.spring_boot_subscription.lilo_gateway;

import io.fria.lilo.Lilo;
import io.fria.lilo.RemoteSchemaSource;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LiloConfiguration {

  private static final String SOURCE1_NAME           = "SERVER1";
  private static final String SOURCE1_GRAPHQL_URL    = "http://localhost:8081/graphql";
  private static final String SOURCE1_GRAPHQL_WS_URL = "ws://localhost:8081/graphql";
  private static final String SOURCE2_NAME           = "SERVER2";
  private static final String SOURCE2_GRAPHQL_URL    = "http://localhost:8082/graphql";
  private static final String SOURCE2_GRAPHQL_WS_URL = "ws://localhost:8082/graphql";

  @Bean
  public @NotNull Lilo lilo() {

    return Lilo.builder()
      .addSource(
        RemoteSchemaSource.create(
          SOURCE1_NAME,
          new IntrospectionRetrieverImpl(SOURCE1_GRAPHQL_URL),
          new QueryRetrieverImpl(SOURCE1_GRAPHQL_URL),
          new SubscriptionRetrieverImpl(SOURCE1_GRAPHQL_WS_URL)))
//      .addSource(
//        RemoteSchemaSource.create(
//          SOURCE2_NAME,
//          new IntrospectionRetrieverImpl(SOURCE2_GRAPHQL_URL),
//          new QueryRetrieverImpl(SOURCE2_GRAPHQL_URL),
//          new SubscriptionRetrieverImpl(SOURCE2_GRAPHQL_WS_URL)))
      .build();
  }
}
