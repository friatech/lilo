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
package io.fria.lilo.defined_fetchers;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.DataFetcher;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import io.fria.lilo.DefinedSchemaSource;
import io.fria.lilo.Lilo;
import io.fria.lilo.LiloContext;
import io.fria.lilo.RemoteSchemaSource;
import io.fria.lilo.SchemaSource;
import io.fria.lilo.TestUtils;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Data fetchers a {@link DefinedSchemaSource} supplies for non-root fields run on the gateway. */
class DefinedSourceFetcherTest {

  private static final String THING_SDL = "type Thing { id: ID computed: String }";

  private static final DataFetcher<String> COMPUTED =
      env -> "computed-" + ((Map<?, ?>) env.getSource()).get("id");

  private static ExecutionResult stitch(final Lilo lilo, final String query) {
    return lilo.stitch(ExecutionInput.newExecutionInput().query(query).build());
  }

  private static RuntimeWiring thingWiring(
      final String rootField, final Object rootValue, final DataFetcher<?> computed) {
    return RuntimeWiring.newRuntimeWiring()
        .type(newTypeWiring("Query").dataFetcher(rootField, env -> rootValue))
        .type(newTypeWiring("Thing").dataFetcher("computed", computed))
        .build();
  }

  @Test
  void nonRootFetcherRuns() {

    final Lilo lilo =
        Lilo.builder()
            .addSource(
                DefinedSchemaSource.create(
                    "project1",
                    "type Query { thing: Thing } " + THING_SDL,
                    thingWiring("thing", Map.of("id", "1"), COMPUTED)))
            .build();

    final ExecutionResult result = stitch(lilo, "{ thing { id computed } }");

    Assertions.assertEquals(0, result.getErrors().size(), result.getErrors().toString());
    Assertions.assertEquals(
        Map.of("thing", Map.of("id", "1", "computed", "computed-1")), result.getData());
  }

  @Test
  void nonRootFetcherRunsForListItemsAndAliases() {

    final Lilo lilo =
        Lilo.builder()
            .addSource(
                DefinedSchemaSource.create(
                    "project1",
                    "type Query { things: [Thing] } " + THING_SDL,
                    thingWiring("things", List.of(Map.of("id", "1"), Map.of("id", "2")), COMPUTED)))
            .build();

    final ExecutionResult result = stitch(lilo, "{ all: things { id c: computed computed } }");

    Assertions.assertEquals(0, result.getErrors().size(), result.getErrors().toString());
    Assertions.assertEquals(
        Map.of(
            "all",
            List.of(
                Map.of("id", "1", "c", "computed-1", "computed", "computed-1"),
                Map.of("id", "2", "c", "computed-2", "computed", "computed-2"))),
        result.getData());
  }

  @Test
  void nonRootFetcherOfInterfaceImplementationRuns() {

    final Lilo lilo =
        Lilo.builder()
            .addSource(
                DefinedSchemaSource.create(
                    "project1",
                    "type Query { node: Node } interface Node { id: ID } "
                        + "type Thing implements Node { id: ID computed: String }",
                    RuntimeWiring.newRuntimeWiring()
                        .type(
                            newTypeWiring("Query")
                                .dataFetcher(
                                    "node", env -> Map.of("__typename", "Thing", "id", "1")))
                        .type(
                            newTypeWiring("Node")
                                .typeResolver(env -> env.getSchema().getObjectType("Thing")))
                        .type(newTypeWiring("Thing").dataFetcher("computed", COMPUTED))
                        .build()))
            .build();

    final ExecutionResult result = stitch(lilo, "{ node { id ... on Thing { computed } } }");

    Assertions.assertEquals(0, result.getErrors().size(), result.getErrors().toString());
    Assertions.assertEquals(
        Map.of("node", Map.of("id", "1", "computed", "computed-1")), result.getData());
  }

  private static GraphQL remoteThingGraphQL() {
    return GraphQL.newGraphQL(
            new SchemaGenerator()
                .makeExecutableSchema(
                    new SchemaParser().parse("type Query { remoteThing: Thing } " + THING_SDL),
                    RuntimeWiring.newRuntimeWiring()
                        .type(
                            newTypeWiring("Query")
                                .dataFetcher(
                                    "remoteThing",
                                    env -> Map.of("id", "r", "computed", "from-remote")))
                        .build()))
        .build();
  }

  private static Lilo sharedWithRemote(final TestUtils.TestIntrospectionRetriever retriever) {
    final GraphQL remoteGraphQL = remoteThingGraphQL();
    retriever.setGraphQL(remoteGraphQL);

    return Lilo.builder()
        .addSource(
            RemoteSchemaSource.create(
                "remote", retriever, new TestUtils.TestQueryRetriever(remoteGraphQL)))
        .addSource(
            DefinedSchemaSource.create(
                "local",
                "type Query { thing: Thing } " + THING_SDL,
                thingWiring("thing", Map.of("id", "1", "computed", "from-map"), COMPUTED)))
        .build();
  }

  @Test
  void fetcherOfTypeSharedWithRemoteSourceRunsBelowLocalRootField() {

    final Lilo lilo =
        sharedWithRemote(new TestUtils.TestIntrospectionRetriever(remoteThingGraphQL()));

    final ExecutionResult result = stitch(lilo, "{ thing { id computed } }");

    Assertions.assertEquals(0, result.getErrors().size(), result.getErrors().toString());
    Assertions.assertEquals(
        Map.of("thing", Map.of("id", "1", "computed", "computed-1")), result.getData());
  }

  @Test
  void fetcherOfTypeSharedWithRemoteSourceIsNotUsedBelowRemoteRootField() {

    final Lilo lilo =
        sharedWithRemote(new TestUtils.TestIntrospectionRetriever(remoteThingGraphQL()));

    final ExecutionResult result =
        stitch(lilo, "{ remoteThing { id c: computed } thing { id c: computed } }");

    // Below the remote root field the remote payload is read by result key
    Assertions.assertEquals(0, result.getErrors().size(), result.getErrors().toString());
    Assertions.assertEquals(
        Map.of(
            "remoteThing", Map.of("id", "r", "c", "from-remote"),
            "thing", Map.of("id", "1", "c", "computed-1")),
        result.getData());
  }

  @Test
  void fetcherOfTypeSharedWithRemoteSourceDoesNotDependOnRemoteAvailability() {

    final AtomicBoolean remoteDown = new AtomicBoolean(true);
    final TestUtils.TestIntrospectionRetriever retriever =
        new TestUtils.TestIntrospectionRetriever(remoteThingGraphQL()) {
          @Override
          public @NotNull String get(
              final @NotNull LiloContext liloContext,
              final @NotNull SchemaSource schemaSource,
              final @NotNull String query,
              final @Nullable Object localContext) {

            if (remoteDown.get()) {
              throw new IllegalStateException("remote is down");
            }

            return super.get(liloContext, schemaSource, query, localContext);
          }
        };
    final Lilo lilo = sharedWithRemote(retriever);

    final ExecutionResult down = stitch(lilo, "{ thing { computed } }");
    Assertions.assertEquals(0, down.getErrors().size(), down.getErrors().toString());
    Assertions.assertEquals(Map.of("thing", Map.of("computed", "computed-1")), down.getData());

    remoteDown.set(false);
    lilo.getContext().invalidateAll();

    final ExecutionResult up = stitch(lilo, "{ thing { computed } remoteThing { computed } }");
    Assertions.assertEquals(0, up.getErrors().size(), up.getErrors().toString());
    Assertions.assertEquals(
        Map.of(
            "thing", Map.of("computed", "computed-1"),
            "remoteThing", Map.of("computed", "from-remote")),
        up.getData());
  }

  @Test
  void differentFetchersForSameFieldUseFirstSourceByName() {

    // Added in reverse name order: the choice must not depend on the order of the sources
    final Lilo lilo =
        Lilo.builder()
            .addSource(
                DefinedSchemaSource.create(
                    "project2",
                    "type Query { b: Thing } " + THING_SDL,
                    thingWiring("b", Map.of("id", "2"), env -> "other")))
            .addSource(
                DefinedSchemaSource.create(
                    "project1",
                    "type Query { a: Thing } " + THING_SDL,
                    thingWiring("a", Map.of("id", "1"), COMPUTED)))
            .build();

    final ExecutionResult result = stitch(lilo, "{ a { computed } b { computed } }");

    Assertions.assertEquals(0, result.getErrors().size(), result.getErrors().toString());
    Assertions.assertEquals(
        Map.of("a", Map.of("computed", "computed-1"), "b", Map.of("computed", "computed-2")),
        result.getData());
  }

  @Test
  void fetcherAtInterfaceCoordinateBehavesAsInSourceSchema() {

    final String sdl =
        "type Query { node: Node } interface Node { id: ID label: String } "
            + "type Thing implements Node { id: ID label: String }";
    final RuntimeWiring wiring =
        RuntimeWiring.newRuntimeWiring()
            .type(
                newTypeWiring("Query")
                    .dataFetcher(
                        "node", env -> Map.of("__typename", "Thing", "id", "1", "label", "map")))
            .type(
                newTypeWiring("Node")
                    .dataFetcher("label", env -> "from-interface")
                    .typeResolver(env -> env.getSchema().getObjectType("Thing")))
            .build();
    final String query = "{ node { id label } }";

    final ExecutionResult expected =
        GraphQL.newGraphQL(
                new SchemaGenerator().makeExecutableSchema(new SchemaParser().parse(sdl), wiring))
            .build()
            .execute(query);

    final Lilo lilo =
        Lilo.builder().addSource(DefinedSchemaSource.create("project1", sdl, wiring)).build();
    final ExecutionResult result = stitch(lilo, query);

    Assertions.assertEquals(0, result.getErrors().size(), result.getErrors().toString());
    Assertions.assertEquals(expected.<Object>getData(), result.getData());
  }

  @Test
  void sameFetcherInstanceInTwoSourcesIsAccepted() {

    final Lilo lilo =
        Lilo.builder()
            .addSource(
                DefinedSchemaSource.create(
                    "project1",
                    "type Query { a: Thing } " + THING_SDL,
                    thingWiring("a", Map.of("id", "1"), COMPUTED)))
            .addSource(
                DefinedSchemaSource.create(
                    "project2",
                    "type Query { b: Thing } " + THING_SDL,
                    thingWiring("b", Map.of("id", "2"), COMPUTED)))
            .build();

    final ExecutionResult result = stitch(lilo, "{ a { computed } b { computed } }");

    Assertions.assertEquals(0, result.getErrors().size(), result.getErrors().toString());
    Assertions.assertEquals(
        Map.of("a", Map.of("computed", "computed-1"), "b", Map.of("computed", "computed-2")),
        result.getData());
  }

  @Test
  void fetcherOnlyInOneSourceOfSharedTypeRuns() {

    final Lilo lilo =
        Lilo.builder()
            .addSource(
                DefinedSchemaSource.create(
                    "project1",
                    "type Query { a: Thing } " + THING_SDL,
                    thingWiring("a", Map.of("id", "1"), COMPUTED)))
            .addSource(
                DefinedSchemaSource.create(
                    "project2",
                    "type Query { b: Thing } " + THING_SDL,
                    RuntimeWiring.newRuntimeWiring()
                        .type(newTypeWiring("Query").dataFetcher("b", env -> Map.of("id", "2")))
                        .build()))
            .build();

    final ExecutionResult result = stitch(lilo, "{ a { computed } b { computed } }");

    // The gateway has one Thing type, so the fetcher applies below both root fields
    Assertions.assertEquals(0, result.getErrors().size(), result.getErrors().toString());
    Assertions.assertEquals(
        Map.of("a", Map.of("computed", "computed-1"), "b", Map.of("computed", "computed-2")),
        result.getData());
  }

  @Test
  void nonRootFetcherRunsAfterReMerge() {

    final AtomicInteger calls = new AtomicInteger();
    final Lilo lilo =
        Lilo.builder()
            .addSource(
                DefinedSchemaSource.create(
                    "project1",
                    "type Query { thing: Thing } " + THING_SDL,
                    thingWiring(
                        "thing",
                        Map.of("id", "1"),
                        env -> {
                          calls.incrementAndGet();
                          return COMPUTED.get(env);
                        })))
            .build();

    Assertions.assertEquals(
        Map.of("thing", Map.of("computed", "computed-1")),
        stitch(lilo, "{ thing { computed } }").getData());

    lilo.getContext().invalidateAll();

    final ExecutionResult result = stitch(lilo, "{ thing { computed } }");
    Assertions.assertEquals(0, result.getErrors().size(), result.getErrors().toString());
    Assertions.assertEquals(Map.of("thing", Map.of("computed", "computed-1")), result.getData());
    Assertions.assertEquals(2, calls.get());
  }
}
