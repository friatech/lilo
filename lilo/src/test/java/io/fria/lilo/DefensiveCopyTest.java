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

import graphql.GraphQLError;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import io.fria.lilo.error.ErrorSourceLocation;
import io.fria.lilo.error.GraphQLResultError;
import io.fria.lilo.error.LiloSourceDataFetcherException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DefensiveCopyTest {

  @Test
  void graphQLQueryCopiesArgumentsAndVariables() {

    final Map<String, Object> arguments = new HashMap<>();
    arguments.put("id", 1);
    arguments.put("nullable", null);
    final Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value");

    final GraphQLQuery query =
        GraphQLQuery.builder()
            .query("{\"query\":\"{name}\",\"variables\":{\"var\":\"value\"}}")
            .operationType(OperationDefinition.Operation.QUERY)
            .queryNode(new Field("name"))
            .arguments(arguments)
            .variables(variables)
            .build();

    arguments.put("id", 2);
    variables.clear();

    Assertions.assertEquals(1, query.getArguments().get("id"));
    Assertions.assertTrue(query.getArguments().containsKey("nullable"));
    Assertions.assertEquals(Map.of("var", "value"), query.getVariables());
    Assertions.assertThrows(
        UnsupportedOperationException.class, () -> query.getArguments().put("x", 1));
    Assertions.assertThrows(
        UnsupportedOperationException.class, () -> query.getVariables().put("x", 1));
  }

  @Test
  void graphQLQueryReturnsRequestCopy() {

    final GraphQLQuery query =
        GraphQLQuery.builder()
            .query("{\"query\":\"{name}\",\"operationName\":\"op\",\"variables\":{\"a\":null}}")
            .operationType(OperationDefinition.Operation.QUERY)
            .queryNode(new Field("name"))
            .arguments(Map.of())
            .build();

    final GraphQLRequest request = query.getRequest();
    request.setQuery("{other}");
    request.setOperationName("other");

    Assertions.assertEquals("{name}", query.getRequest().getQuery());
    Assertions.assertEquals("op", query.getRequest().getOperationName());
    Assertions.assertTrue(query.getRequest().getVariables().containsKey("a"));
    Assertions.assertEquals(query.getRequest(), query.getRequest());
  }

  @Test
  void graphQLRequestCopiesVariables() {

    final Map<String, Object> variables = new HashMap<>();
    variables.put("a", 1);

    final GraphQLRequest fromConstructor = new GraphQLRequest("{name}", null, variables);
    final GraphQLRequest fromBuilder =
        GraphQLRequest.builder().query("{name}").variables(variables).build();
    final GraphQLRequest fromSetter = new GraphQLRequest();
    fromSetter.setQuery("{name}");
    fromSetter.setVariables(variables);

    variables.put("a", 2);

    for (final GraphQLRequest request : List.of(fromConstructor, fromBuilder, fromSetter)) {
      Assertions.assertEquals(Map.of("a", 1), request.getVariables());
      Assertions.assertThrows(
          UnsupportedOperationException.class, () -> request.getVariables().put("b", 1));
    }

    Assertions.assertEquals(fromConstructor, fromSetter);
    Assertions.assertEquals(fromConstructor.hashCode(), fromBuilder.hashCode());
  }

  @Test
  void graphQLRequestJsonRoundTripKeepsNullVariables() {

    final GraphQLRequest request =
        JsonUtils.toObj(
                "{\"query\":\"{name}\",\"variables\":{\"a\":null,\"b\":1}}", GraphQLRequest.class)
            .orElseThrow();

    Assertions.assertTrue(request.getVariables().containsKey("a"));
    Assertions.assertEquals(1, request.getVariables().get("b"));
    Assertions.assertEquals(
        request, JsonUtils.toObj(JsonUtils.toStr(request), GraphQLRequest.class).orElseThrow());
  }

  @Test
  void graphQLResultErrorCopiesCollections() {

    final Map<String, Object> extensions = new HashMap<>();
    extensions.put("code", "X");
    final List<Object> path = new ArrayList<>(List.of("a", 1));
    final List<ErrorSourceLocation> locations =
        new ArrayList<>(List.of(new ErrorSourceLocation(1, 2, null)));

    final GraphQLResultError error = new GraphQLResultError();
    error.setExtensions(extensions);
    error.setPath(path);
    error.setLocations(locations);

    extensions.clear();
    path.clear();
    locations.clear();

    Assertions.assertEquals(Map.of("code", "X"), error.getExtensions());
    Assertions.assertEquals(List.of("a", 1), error.getPath());
    Assertions.assertEquals(1, error.getLocations().size());
    Assertions.assertThrows(
        UnsupportedOperationException.class, () -> error.getExtensions().put("b", 1));
    Assertions.assertThrows(UnsupportedOperationException.class, () -> error.getPath().add("b"));
  }

  @Test
  void graphQLResultExtensionsAreReadOnly() {

    final GraphQLResult result =
        JsonUtils.toObj("{\"data\":{\"a\":1},\"extensions\":{\"b\":2}}", GraphQLResult.class)
            .orElseThrow();

    Assertions.assertEquals(Map.of("b", 2), result.getExtensions());
    Assertions.assertThrows(
        UnsupportedOperationException.class, () -> result.getExtensions().put("c", 3));
    Assertions.assertEquals(Map.of("b", 2), result.toSpecification().get("extensions"));
  }

  @Test
  void liloSourceDataFetcherExceptionCopiesErrors() {

    final GraphQLResultError error = new GraphQLResultError();
    error.setMessage("failure");
    final List<GraphQLError> errors = new ArrayList<>(List.of(error));

    final LiloSourceDataFetcherException exception = new LiloSourceDataFetcherException(errors);
    errors.clear();

    Assertions.assertEquals(1, exception.getErrors().size());
    Assertions.assertEquals("failure", exception.getMessage());
    Assertions.assertThrows(
        UnsupportedOperationException.class, () -> exception.getErrors().remove(0));
  }
}
