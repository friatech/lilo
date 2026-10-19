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

import graphql.ExecutionInput;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class GraphQLRequestTest {

  private static final String WITH_EXTENSIONS =
      "{\"query\":\"{name}\",\"extensions\":{\"persistedQuery\":{\"version\":1}}}";

  @Test
  void extensionsAreDeserialised() {

    final GraphQLRequest request =
        JsonUtils.toObj(WITH_EXTENSIONS, GraphQLRequest.class).orElseThrow();

    Assertions.assertEquals(
        Map.of("persistedQuery", Map.of("version", 1)), request.getExtensions());
  }

  @Test
  void nullExtensionsAreDeserialisedAsAbsent() {

    final GraphQLRequest request =
        JsonUtils.toObj("{\"query\":\"{name}\",\"extensions\":null}", GraphQLRequest.class)
            .orElseThrow();

    Assertions.assertNull(request.getExtensions());
    Assertions.assertTrue(request.toExecutionInput().getExtensions().isEmpty());
  }

  @Test
  void nonObjectExtensionsAreRejected() {

    final IllegalArgumentException e =
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () ->
                JsonUtils.toObj(
                    "{\"query\":\"{name}\",\"extensions\":\"not an object\"}",
                    GraphQLRequest.class));
    Assertions.assertEquals("Deserialization exception", e.getMessage());
  }

  @Test
  void extensionsReachExecutionInput() {

    final GraphQLRequest request =
        GraphQLRequest.builder().query("{name}").extensions(Map.of("a", 1)).build();

    final ExecutionInput input = request.toExecutionInput();

    Assertions.assertEquals(Map.of("a", 1), input.getExtensions());
    Assertions.assertTrue(
        GraphQLRequest.builder()
            .query("{name}")
            .build()
            .toExecutionInput()
            .getExtensions()
            .isEmpty());
  }

  @Test
  void missingQueryGivesClearException() {

    for (final String json : new String[] {"{}", "{\"query\":\"  \"}", "{\"query\":\"\"}"}) {
      final GraphQLRequest request = JsonUtils.toObj(json, GraphQLRequest.class).orElseThrow();
      final IllegalArgumentException e =
          Assertions.assertThrows(IllegalArgumentException.class, request::toExecutionInput);
      Assertions.assertEquals("GraphQL request must contain a query", e.getMessage());
    }
  }

  @Test
  void jsonRoundTripKeepsExtensions() {

    final GraphQLRequest request =
        JsonUtils.toObj(WITH_EXTENSIONS, GraphQLRequest.class).orElseThrow();

    Assertions.assertEquals(
        request, JsonUtils.toObj(JsonUtils.toStr(request), GraphQLRequest.class).orElseThrow());
    Assertions.assertTrue(JsonUtils.toStr(request).contains("\"extensions\""));
  }

  @Test
  void requestWithoutExtensionsStillWorks() {

    final GraphQLRequest request =
        JsonUtils.toObj("{\"query\":\"{name}\"}", GraphQLRequest.class).orElseThrow();

    Assertions.assertNull(request.getExtensions());
    Assertions.assertEquals(
        "{\"query\":\"{name}\",\"operationName\":null,\"variables\":null}",
        JsonUtils.toStr(request));
  }

  @Test
  void extensionsAreDefensivelyCopied() {

    final Map<String, Object> source = new HashMap<>(Map.of("a", 1));
    final GraphQLRequest fromBuilder =
        GraphQLRequest.builder().query("{name}").extensions(source).build();
    final GraphQLRequest fromSetter = new GraphQLRequest();
    fromSetter.setQuery("{name}");
    fromSetter.setExtensions(source);
    final GraphQLRequest fromConstructor = new GraphQLRequest("{name}", null, null, source);
    source.put("b", 2);

    for (final GraphQLRequest request :
        new GraphQLRequest[] {fromBuilder, fromSetter, fromConstructor}) {
      Assertions.assertEquals(Map.of("a", 1), request.getExtensions());
      Assertions.assertThrows(
          UnsupportedOperationException.class, () -> request.getExtensions().put("c", 3));
      Assertions.assertEquals(Map.of("a", 1), request.getExtensions());
    }
  }

  @Test
  void extensionsTakePartInEqualsHashCodeAndToString() {

    final GraphQLRequest one =
        GraphQLRequest.builder().query("{name}").extensions(Map.of("a", 1)).build();
    final GraphQLRequest same =
        GraphQLRequest.builder().query("{name}").extensions(Map.of("a", 1)).build();
    final GraphQLRequest other =
        GraphQLRequest.builder().query("{name}").extensions(Map.of("a", 2)).build();
    final GraphQLRequest none = GraphQLRequest.builder().query("{name}").build();

    Assertions.assertEquals(one, same);
    Assertions.assertEquals(one.hashCode(), same.hashCode());
    Assertions.assertNotEquals(one, other);
    Assertions.assertNotEquals(one, none);
    Assertions.assertTrue(one.toString().contains("extensions={a=1}"));
  }
}
