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

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

import graphql.schema.DataFetcher;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DataFetcherWiringTest {

  private static final String WARNING =
      "WARN io.fria.lilo.SchemaMerger - Field Thing.computed has different data fetchers in "
          + "sources [project1, project2]; using the one of project1";

  private static SchemaMerger.SourceSnapshot source(
      final String name, final String rootField, final DataFetcher<?> computed) {

    final TypeDefinitionRegistry registry =
        new SchemaParser()
            .parse(
                "type Query { " + rootField + ": Thing } type Thing { id: ID computed: String }");
    final RuntimeWiring wiring =
        RuntimeWiring.newRuntimeWiring()
            .type(newTypeWiring("Thing").dataFetcher("computed", computed))
            .build();

    return new SchemaMerger.SourceSnapshot(name, false, registry, wiring);
  }

  /**
   * Merges the sources and returns what was logged meanwhile. The tests log through slf4j-simple,
   * which has no appender to attach but writes to the current {@code System.err} on every call.
   */
  private static String mergeAndCaptureLog(final SchemaMerger.SourceSnapshot... sources) {

    final ByteArrayOutputStream captured = new ByteArrayOutputStream();
    final PrintStream originalErr = System.err;

    System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));

    try {
      SchemaMerger.mergeSnapshots(
          List.of(sources), new TypeDefinitionRegistry(), RuntimeWiring.newRuntimeWiring());
    } finally {
      System.setErr(originalErr);
    }

    return captured.toString(StandardCharsets.UTF_8);
  }

  @Test
  void differentFetchersForSameFieldAreWarnedUnderTheMergerCategory() {

    // Added in reverse name order: the warning names the first source by name
    final String log =
        mergeAndCaptureLog(
            source("project2", "b", env -> "other"), source("project1", "a", env -> "computed"));

    Assertions.assertTrue(log.contains(WARNING), log);
  }

  @Test
  void sameFetcherInstanceInSeveralSourcesIsNotWarned() {

    final DataFetcher<?> shared = env -> "computed";
    final String log =
        mergeAndCaptureLog(source("project1", "a", shared), source("project2", "b", shared));

    Assertions.assertFalse(log.contains("has different data fetchers"), log);
  }
}
