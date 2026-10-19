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

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.language.TypeDefinition;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SchemaSnapshotTest {

  private static final String SDL = "type Query { me: User } type User { id: ID nick: String }";

  @Test
  void invalidateWhileBuildingKeepsMergeAndRootFieldsConsistent() throws Exception {

    final RuntimeWiring wiring =
        RuntimeWiring.newRuntimeWiring()
            .type(newTypeWiring("Query").dataFetcher("me", env -> Map.of("id", "1", "nick", "N")))
            .build();
    final GraphQL remote =
        GraphQL.newGraphQL(
                new SchemaGenerator().makeExecutableSchema(new SchemaParser().parse(SDL), wiring))
            .build();

    final Lilo lilo =
        Lilo.builder()
            .addSource(
                RemoteSchemaSource.create(
                    "remote",
                    new TestUtils.TestIntrospectionRetriever(remote),
                    new TestUtils.TestQueryRetriever(remote)))
            .build();

    // Load once, so that the next build reuses the loaded source
    Assertions.assertEquals(
        Map.of("me", Map.of("n", "N")), lilo.stitch("{ me { n: nick } }").getData());

    final SchemaSource source = lilo.getContext().getSchemaSources().get("remote");
    final TypeDefinitionRegistry loaded = source.typeDefinitionRegistry;
    Assertions.assertNotNull(loaded);

    // Invalidates the source as soon as the merge reads its types, i.e. in the middle of building
    // the merged schema, as a concurrent invalidate could
    source.typeDefinitionRegistry = new InvalidatingRegistry(source, loaded);

    final GraphQL graphQL = lilo.getContext().reloadGraphQL(null).get(30, TimeUnit.SECONDS);
    Assertions.assertTrue(source.isSchemaNotLoaded());

    // The source was merged, so its root fields must also be read by result key
    final ExecutionResult result = graphQL.execute("{ me { n: nick } }");
    Assertions.assertEquals(List.of(), result.getErrors());
    Assertions.assertEquals(Map.of("me", Map.of("n", "N")), result.getData());
  }

  private static final class InvalidatingRegistry extends TypeDefinitionRegistry {

    private static final long serialVersionUID = 1L;

    private final transient SchemaSource source;
    private final AtomicBoolean invalidated = new AtomicBoolean();

    InvalidatingRegistry(
        final @NotNull SchemaSource source, final @NotNull TypeDefinitionRegistry content) {
      this.source = source;
      this.merge(content);
    }

    @Override
    public @NotNull Map<String, TypeDefinition> types() {

      if (this.invalidated.compareAndSet(false, true)) {
        this.source.invalidate();
      }

      return super.types();
    }
  }
}
