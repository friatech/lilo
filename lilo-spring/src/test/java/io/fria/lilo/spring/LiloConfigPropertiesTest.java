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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** Binds the properties the way Spring Boot binds application.yml/properties. */
class LiloConfigPropertiesTest {

  private static LiloConfigProperties bind(final Map<String, String> values) {
    final LiloConfigProperties properties = new LiloConfigProperties();
    new Binder(new MapConfigurationPropertySource(values))
        .bind("lilo", Bindable.ofInstance(properties));
    return properties;
  }

  @Test
  void defaults() {
    final LiloConfigProperties properties = bind(Map.of());

    assertEquals("/graphql", properties.getGraphQlPath());
    assertEquals("/graphql", properties.getSubscriptionPath());
    assertEquals(List.of("*"), properties.getAllowedOrigins());
    assertEquals(Duration.ofSeconds(10), properties.getSendTimeLimit());
    assertEquals(524_288, properties.getSendBufferSizeLimit());
  }

  @Test
  void bindsConfiguredValues() {
    final LiloConfigProperties properties =
        bind(
            Map.of(
                "lilo.allowed-origins", "https://a.example,https://*.b.example",
                "lilo.send-time-limit", "250ms",
                "lilo.send-buffer-size-limit", "1024"));

    assertEquals(
        List.of("https://a.example", "https://*.b.example"), properties.getAllowedOrigins());
    assertEquals(Duration.ofMillis(250), properties.getSendTimeLimit());
    assertEquals(1024, properties.getSendBufferSizeLimit());
  }

  @Test
  void bindsDurationWithoutUnitAsMilliseconds() {
    assertEquals(
        Duration.ofMillis(1500), bind(Map.of("lilo.send-time-limit", "1500")).getSendTimeLimit());
  }

  @Test
  void rejectsNonPositiveLimits() {
    assertThrows(BindException.class, () -> bind(Map.of("lilo.send-time-limit", "0s")));
    assertThrows(BindException.class, () -> bind(Map.of("lilo.send-buffer-size-limit", "0")));
    final LiloConfigProperties properties = new LiloConfigProperties();
    assertThrows(
        IllegalArgumentException.class, () -> properties.setSendTimeLimit(Duration.ofMillis(-1)));
    assertThrows(IllegalArgumentException.class, () -> properties.setSendBufferSizeLimit(-1));
  }

  @Test
  void subscriptionPoolSizeAndOperationCapAreUnsetByDefault() {
    final LiloConfigProperties properties = bind(Map.of());

    assertNull(properties.getSubscriptionPoolSize());
    assertNull(properties.getMaxOperationsPerSession());
  }

  @Test
  void bindsSubscriptionPoolSizeAndOperationCap() {
    final LiloConfigProperties properties =
        bind(
            Map.of(
                "lilo.subscription-pool-size", "8",
                "lilo.max-operations-per-session", "3"));

    assertEquals(8, properties.getSubscriptionPoolSize());
    assertEquals(3, properties.getMaxOperationsPerSession());
  }

  @Test
  void rejectsInvalidSubscriptionPoolSizeAndOperationCap() {
    assertThrows(BindException.class, () -> bind(Map.of("lilo.subscription-pool-size", "0")));
    assertThrows(
        BindException.class,
        () -> bind(Map.of("lilo.subscription-pool-size", String.valueOf(Integer.MAX_VALUE))));
    assertThrows(BindException.class, () -> bind(Map.of("lilo.max-operations-per-session", "0")));
    final LiloConfigProperties properties = new LiloConfigProperties();
    assertThrows(IllegalArgumentException.class, () -> properties.setSubscriptionPoolSize(-1));
    assertThrows(IllegalArgumentException.class, () -> properties.setMaxOperationsPerSession(-1));

    // null restores the default
    properties.setSubscriptionPoolSize(4);
    properties.setSubscriptionPoolSize(null);
    properties.setMaxOperationsPerSession(null);
    assertNull(properties.getSubscriptionPoolSize());
    assertNull(properties.getMaxOperationsPerSession());
  }
}
