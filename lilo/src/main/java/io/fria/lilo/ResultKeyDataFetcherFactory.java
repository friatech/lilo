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

import graphql.execution.ExecutionStepInfo;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetcherFactory;
import graphql.schema.DataFetcherFactoryEnvironment;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.LightDataFetcher;
import graphql.schema.PropertyDataFetcher;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;

/**
 * Default data fetcher of the gateway schema, used for every field without an explicit fetcher.
 *
 * <p>Below a root field served by a remote source, the value is the JSON payload of the source,
 * keyed by result key because aliases are forwarded to the source. Such a field is therefore read
 * by its result key. Anything else, such as a value returned by a {@link DefinedSchemaSource} data
 * fetcher, is read by field name as graphql-java does by default.
 */
final class ResultKeyDataFetcherFactory implements DataFetcherFactory<Object> {

  private final Set<FieldCoordinates> remoteRootFields;

  ResultKeyDataFetcherFactory(final @NotNull Set<FieldCoordinates> remoteRootFields) {
    this.remoteRootFields = Set.copyOf(remoteRootFields);
  }

  @Override
  public @NotNull DataFetcher<Object> get(
      final @NotNull DataFetcherFactoryEnvironment environment) {
    return this.get(environment.getFieldDefinition());
  }

  @Override
  public @NotNull DataFetcher<Object> get(final @NotNull GraphQLFieldDefinition fieldDefinition) {
    return new ResultKeyDataFetcher(
        PropertyDataFetcher.fetching(fieldDefinition.getName()), this.remoteRootFields);
  }

  private static final class ResultKeyDataFetcher implements LightDataFetcher<Object> {

    private final PropertyDataFetcher<Object> propertyDataFetcher;
    private final Set<FieldCoordinates> remoteRootFields;

    ResultKeyDataFetcher(
        final @NotNull PropertyDataFetcher<Object> propertyDataFetcher,
        final @NotNull Set<FieldCoordinates> remoteRootFields) {
      this.propertyDataFetcher = propertyDataFetcher;
      this.remoteRootFields = remoteRootFields;
    }

    private static @NotNull ExecutionStepInfo rootFieldStep(
        final @NotNull ExecutionStepInfo executionStepInfo) {

      // The operation itself is the topmost step; the root field is the step right below it
      ExecutionStepInfo step = executionStepInfo;
      ExecutionStepInfo parent = step.getParent();

      while (parent != null && parent.getParent() != null) {
        step = parent;
        parent = step.getParent();
      }

      return step;
    }

    @Override
    public Object get(
        final @NotNull GraphQLFieldDefinition fieldDefinition,
        final Object sourceObject,
        final @NotNull Supplier<DataFetchingEnvironment> environmentSupplier)
        throws Exception {

      if (sourceObject instanceof Map) {
        final DataFetchingEnvironment environment = environmentSupplier.get();

        if (this.isBelowRemoteRootField(environment)) {
          return ((Map<?, ?>) sourceObject).get(environment.getField().getResultKey());
        }
      }

      return this.propertyDataFetcher.get(fieldDefinition, sourceObject, environmentSupplier);
    }

    @Override
    public Object get(final @NotNull DataFetchingEnvironment environment) throws Exception {
      return this.get(environment.getFieldDefinition(), environment.getSource(), () -> environment);
    }

    private boolean isBelowRemoteRootField(final @NotNull DataFetchingEnvironment environment) {

      final ExecutionStepInfo rootFieldStep = rootFieldStep(environment.getExecutionStepInfo());

      return this.remoteRootFields.contains(
          FieldCoordinates.coordinates(
              rootFieldStep.getObjectType(), rootFieldStep.getFieldDefinition()));
    }
  }
}
