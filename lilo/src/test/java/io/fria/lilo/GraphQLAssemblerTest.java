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

import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class GraphQLAssemblerTest {

  private static final RequestLimits NO_LIMITS = new RequestLimits(null, null, true);
  private static final Instrumentation USER = new UserInstrumentation();

  private static List<String> chain(final Instrumentation combined) {

    final ChainedInstrumentation chained =
        Assertions.assertInstanceOf(ChainedInstrumentation.class, combined);

    return chained.getInstrumentations().stream()
        .map(i -> i.getClass().getSimpleName())
        .collect(Collectors.toList());
  }

  @Test
  void withoutLimitsTheUserInstrumentationIsUsedAsIs() {

    Assertions.assertSame(USER, GraphQLAssembler.combineInstrumentation(NO_LIMITS, USER));
    Assertions.assertNull(GraphQLAssembler.combineInstrumentation(NO_LIMITS, null));
  }

  @Test
  void liloInstrumentationsRunBeforeTheUserOneInAFixedOrder() {

    Assertions.assertEquals(
        List.of(
            "IntrospectionDisablingInstrumentation",
            MaxQueryDepthInstrumentation.class.getSimpleName(),
            MaxQueryComplexityInstrumentation.class.getSimpleName(),
            "UserInstrumentation"),
        chain(GraphQLAssembler.combineInstrumentation(new RequestLimits(5, 100, false), USER)));
  }

  @Test
  void onlyConfiguredLimitsAreChained() {

    Assertions.assertEquals(
        List.of(MaxQueryDepthInstrumentation.class.getSimpleName(), "UserInstrumentation"),
        chain(GraphQLAssembler.combineInstrumentation(new RequestLimits(5, null, true), USER)));
    Assertions.assertEquals(
        List.of(MaxQueryComplexityInstrumentation.class.getSimpleName()),
        chain(GraphQLAssembler.combineInstrumentation(new RequestLimits(null, 100, true), null)));
    Assertions.assertEquals(
        List.of("IntrospectionDisablingInstrumentation"),
        chain(GraphQLAssembler.combineInstrumentation(new RequestLimits(null, null, false), null)));
  }

  private static final class UserInstrumentation extends SimplePerformantInstrumentation {}
}
