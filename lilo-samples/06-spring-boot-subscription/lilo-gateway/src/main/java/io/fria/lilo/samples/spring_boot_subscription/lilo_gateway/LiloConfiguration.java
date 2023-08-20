package io.fria.lilo.samples.spring_boot_subscription.lilo_gateway;

import io.fria.lilo.Lilo;
import io.fria.lilo.RemoteSchemaSource;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LiloConfiguration {

  private static final String SOURCE1_NAME     = "SERVER1";
  private static final String SOURCE1_BASE_URL = "http://localhost:8081";
  private static final String SOURCE2_NAME     = "SERVER2";
  private static final String SOURCE2_BASE_URL = "http://localhost:8082";

  @Bean
  public @NotNull Lilo lilo() {

    return Lilo.builder()
      .addSource(
        RemoteSchemaSource.create(
          SOURCE1_NAME,
          new IntrospectionRetrieverImpl(SOURCE1_BASE_URL),
          new QueryRetrieverImpl(SOURCE1_BASE_URL)))
      .addSource(
        RemoteSchemaSource.create(
          SOURCE2_NAME,
          new IntrospectionRetrieverImpl(SOURCE2_BASE_URL),
          new QueryRetrieverImpl(SOURCE2_BASE_URL)))
      .build();
  }
}
