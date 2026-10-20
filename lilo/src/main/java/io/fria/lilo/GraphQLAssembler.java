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

import graphql.ExecutionInput;
import graphql.GraphQL;
import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.introspection.Introspection;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.UnionTypeDefinition;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.TypeResolver;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.TypeDefinitionRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Builds the merged GraphQL of the loaded sources; holds no state of the load itself. */
final class GraphQLAssembler {

  private static final Set<String> PREDEFINED_SCALARS =
      Set.of("Boolean", "Float", "Int", "ID", "String");
  private static final TypeResolver INTERFACE_TYPE_RESOLVER =
      typenameTypeResolver("Please provide __typename for interface types");
  private static final TypeResolver UNION_TYPE_RESOLVER =
      typenameTypeResolver("Please provide __typename for union types");

  private final DataFetcherExceptionHandler dataFetcherExceptionHandler;
  private final @Nullable Instrumentation instrumentation;
  private final @Nullable Consumer<GraphQL.Builder> graphQLCustomizer;
  private final @Nullable String schemaDescription;

  GraphQLAssembler(
      final @NotNull DataFetcherExceptionHandler dataFetcherExceptionHandler,
      final @Nullable Instrumentation instrumentation,
      final @Nullable Consumer<GraphQL.Builder> graphQLCustomizer,
      final @Nullable String schemaDescription) {
    this.dataFetcherExceptionHandler = dataFetcherExceptionHandler;
    this.instrumentation = instrumentation;
    this.graphQLCustomizer = graphQLCustomizer;
    this.schemaDescription = schemaDescription;
  }

  /**
   * Puts Lilo's own instrumentations in front of the user's one. Without any limit configured the
   * result is exactly the user's instrumentation, so the default behaviour does not change.
   */
  static @Nullable Instrumentation combineInstrumentation(
      final @NotNull LiloContext.RequestLimits requestLimits,
      final @Nullable Instrumentation userInstrumentation) {

    final List<Instrumentation> instrumentations = new ArrayList<>();

    if (!requestLimits.isIntrospectionEnabled()) {
      instrumentations.add(IntrospectionDisablingInstrumentation.INSTANCE);
    }

    final Integer maxQueryDepth = requestLimits.getMaxQueryDepth();

    if (maxQueryDepth != null) {
      instrumentations.add(new MaxQueryDepthInstrumentation(maxQueryDepth));
    }

    final Integer maxQueryComplexity = requestLimits.getMaxQueryComplexity();

    if (maxQueryComplexity != null) {
      instrumentations.add(new MaxQueryComplexityInstrumentation(maxQueryComplexity));
    }

    if (instrumentations.isEmpty()) {
      return userInstrumentation;
    }

    if (userInstrumentation != null) {
      instrumentations.add(userInstrumentation);
    }

    return new ChainedInstrumentation(instrumentations);
  }

  private static @NotNull RuntimeWiring finalizeWiring(
      final @NotNull TypeDefinitionRegistry typeRegistry,
      final @NotNull RuntimeWiring.Builder runtimeWiringBuilder,
      final @NotNull Set<FieldCoordinates> remoteRootFields) {

    // Applies only to fields without an explicit data fetcher, so the root fetchers wired by
    // SchemaMerger keep precedence; scalars and type resolvers are wired independently of it
    runtimeWiringBuilder.codeRegistry(
        GraphQLCodeRegistry.newCodeRegistry()
            .defaultDataFetcher(new ResultKeyDataFetcherFactory(remoteRootFields)));

    final var dummyCoercing = new DummyCoercing();

    typeRegistry.scalars().values().stream()
        .filter(sd -> !PREDEFINED_SCALARS.contains(sd.getName()))
        .forEach(
            sd ->
                runtimeWiringBuilder.scalar(
                    GraphQLScalarType.newScalar()
                        .name(sd.getName())
                        .coercing(dummyCoercing)
                        .build()));

    typeRegistry.types().values().stream()
        .filter(t -> t instanceof InterfaceTypeDefinition || t instanceof UnionTypeDefinition)
        .forEach(
            t -> {
              if (t instanceof InterfaceTypeDefinition) {
                runtimeWiringBuilder.type(
                    newTypeWiring(t.getName()).typeResolver(INTERFACE_TYPE_RESOLVER));
              } else {
                runtimeWiringBuilder.type(
                    newTypeWiring(t.getName()).typeResolver(UNION_TYPE_RESOLVER));
              }
            });

    return runtimeWiringBuilder.build();
  }

  /**
   * Resolves an interface or union value by the {@code __typename} its source returned. The query
   * sent to a source always selects {@code __typename} on abstract types (see {@link
   * QueryTransformer}), so a missing value means the source did not honour the selection.
   */
  private static @NotNull TypeResolver typenameTypeResolver(final @NotNull String missingMessage) {

    return env -> {
      final Object source = env.getObject();
      final Object typeName = source instanceof Map ? ((Map<?, ?>) source).get("__typename") : null;

      if (typeName == null) {
        throw new IllegalArgumentException(missingMessage);
      }

      return env.getSchema().getObjectType(typeName.toString());
    };
  }

  @NotNull
  GraphQL assemble(final @NotNull List<SchemaSource> schemaSourceList) {

    // One read of every source's state, shared by the merge and the remote root field set, so an
    // invalidate during the build cannot make them disagree
    final List<SchemaMerger.SourceSnapshot> snapshots = SchemaMerger.snapshot(schemaSourceList);

    final TypeDefinitionRegistry combinedRegistry = new TypeDefinitionRegistry();
    final RuntimeWiring.Builder runtimeWiringBuilder = RuntimeWiring.newRuntimeWiring();
    SchemaMerger.mergeSnapshots(snapshots, combinedRegistry, runtimeWiringBuilder);

    final RuntimeWiring runtimeWiring =
        finalizeWiring(
            combinedRegistry, runtimeWiringBuilder, SchemaMerger.remoteRootFields(snapshots));

    final GraphQLSchema generatedSchema =
        new SchemaGenerator().makeExecutableSchema(combinedRegistry, runtimeWiring);

    // Applied on every assembly, so a re-merge keeps the description
    final GraphQLSchema graphQLSchema =
        this.schemaDescription == null
            ? generatedSchema
            : generatedSchema.transformWithoutTypes(b -> b.description(this.schemaDescription));

    final GraphQL.Builder builder =
        GraphQL.newGraphQL(graphQLSchema)
            .defaultDataFetcherExceptionHandler(this.dataFetcherExceptionHandler);

    if (this.instrumentation != null) {
      builder.instrumentation(this.instrumentation);
    }

    // Last, so the user can add to or override what Lilo set
    if (this.graphQLCustomizer != null) {
      this.graphQLCustomizer.accept(builder);
    }

    return builder.build();
  }

  /**
   * Disables introspection for every execution of the gateway's own schema by setting
   * graphql-java's per-request {@link Introspection#INTROSPECTION_DISABLED} flag in the request's
   * {@code GraphQLContext}. It runs as an instrumentation of the merged {@link GraphQL}, so it
   * covers {@code stitch}, {@code stitchAsync}, subscriptions and direct use of {@link
   * LiloContext#getMergedGraphQL()}, and it leaves the JVM-wide switch and the introspection Lilo
   * sends to its sources untouched. The flag is always overwritten, so a request cannot enable
   * introspection again.
   */
  private static final class IntrospectionDisablingInstrumentation
      extends SimplePerformantInstrumentation {

    private static final IntrospectionDisablingInstrumentation INSTANCE =
        new IntrospectionDisablingInstrumentation();

    @Override
    public @NotNull ExecutionInput instrumentExecutionInput(
        final @NotNull ExecutionInput executionInput,
        final @NotNull InstrumentationExecutionParameters parameters,
        final @Nullable InstrumentationState state) {

      executionInput.getGraphQLContext().put(Introspection.INTROSPECTION_DISABLED, true);
      return executionInput;
    }
  }
}
