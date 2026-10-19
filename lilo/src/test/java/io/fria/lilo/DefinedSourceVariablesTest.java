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

import graphql.ExecutionResult;
import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.GraphQLScalarType;
import graphql.schema.idl.RuntimeWiring;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Pins that variables reach a defined source as JSON-normalised values. */
class DefinedSourceVariablesTest {

  private static final Coercing<Object, Object> PASS_THROUGH =
      new Coercing<>() {
        @Override
        public Object serialize(
            final @NotNull Object dataFetcherResult,
            final @NotNull GraphQLContext graphQLContext,
            final @NotNull Locale locale) {
          return dataFetcherResult;
        }

        @Override
        public Object parseValue(
            final @NotNull Object input,
            final @NotNull GraphQLContext graphQLContext,
            final @NotNull Locale locale) {
          return input;
        }

        @Override
        public Object parseLiteral(
            final @NotNull Value<?> input,
            final @NotNull CoercedVariables variables,
            final @NotNull GraphQLContext graphQLContext,
            final @NotNull Locale locale) {
          return input;
        }
      };

  private static final String QUERY = "query($v: Any) { kind(v: $v) }";

  private static SchemaSource source() {
    return DefinedSchemaSource.create(
        "local",
        "scalar Any\ntype Query { kind(v: Any): String }",
        RuntimeWiring.newRuntimeWiring()
            .scalar(GraphQLScalarType.newScalar().name("Any").coercing(PASS_THROUGH).build())
            .type(
                "Query",
                t -> t.dataFetcher("kind", env -> env.getArgument("v").getClass().getSimpleName()))
            .build());
  }

  private static ExecutionResult execute(final GraphQLQuery.GraphQLQueryBuilder builder) {

    final SchemaSource source = source();
    final Lilo lilo = Lilo.builder().addSource(source).build();
    lilo.getContext().getMergedGraphQL();

    return source
        .execute(
            lilo.getContext(),
            builder
                .operationType(OperationDefinition.Operation.QUERY)
                .queryNode(new Field("kind"))
                .arguments(Map.of())
                .build(),
            null)
        .join();
  }

  @Test
  void variablesGivenAsJsonAreNormalised() {

    final ExecutionResult result =
        execute(
            GraphQLQuery.builder()
                .query(
                    JsonUtils.toStr(
                        GraphQLRequest.builder().query(QUERY).variables(Map.of("v", 5)).build())));

    Assertions.assertTrue(result.getErrors().isEmpty(), result.getErrors().toString());
    Assertions.assertEquals(Map.of("kind", "Integer"), result.getData());
  }
}
