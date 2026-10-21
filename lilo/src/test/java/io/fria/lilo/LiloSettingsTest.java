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
package io.fria.lilo;

import graphql.execution.SimpleDataFetcherExceptionHandler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class LiloSettingsTest {

  private static final RequestLimits LIMITS = new RequestLimits(null, null, true);

  @Test
  void requiredSettingsRejectNull() {

    final SimpleDataFetcherExceptionHandler handler = new SimpleDataFetcherExceptionHandler();
    final IntrospectionFetchingMode mode = IntrospectionFetchingMode.CACHE_UNTIL_INVALIDATION;

    Assertions.assertThrows(
        NullPointerException.class,
        () -> new LiloSettings(null, mode, true, null, LIMITS, null, null));
    Assertions.assertThrows(
        NullPointerException.class,
        () -> new LiloSettings(handler, null, true, null, LIMITS, null, null));
    Assertions.assertThrows(
        NullPointerException.class,
        () -> new LiloSettings(handler, mode, true, null, null, null, null));
  }

  @Test
  void optionalSettingsMayBeNull() {

    final LiloSettings settings =
        new LiloSettings(
            new SimpleDataFetcherExceptionHandler(),
            IntrospectionFetchingMode.CACHE_UNTIL_INVALIDATION,
            false,
            null,
            LIMITS,
            null,
            null);

    Assertions.assertNull(settings.getInstrumentation());
    Assertions.assertNull(settings.getGraphQLCustomizer());
    Assertions.assertNull(settings.getSchemaDescription());
    Assertions.assertFalse(settings.isRetrySchemaLoad());
  }
}
