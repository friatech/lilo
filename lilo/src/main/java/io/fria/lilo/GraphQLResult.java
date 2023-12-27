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
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;
import io.fria.lilo.error.GraphQLResultError;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class GraphQLResult implements ExecutionResult {

  private Map<String, Object> data;
  private List<GraphQLResultError> errors;
  private Map<Object, Object> extensions;

  @Override
  public @NotNull Map<String, Object> getData() {
    return this.data;
  }

  @Override
  public @Nullable List<GraphQLError> getErrors() {

    if (this.errors == null) {
      return null;
    }

    return this.errors.stream().map(le -> (GraphQLError) le).collect(Collectors.toList());
  }

  @Override
  public @Nullable Map<Object, Object> getExtensions() {
    return this.extensions;
  }

  @Override
  public boolean isDataPresent() {
    return this.data != null;
  }

  @Override
  public @NotNull Map<String, Object> toSpecification() {

    return ExecutionResultImpl.newExecutionResult()
        .data(this.data)
        .errors(this.getErrors())
        .extensions(this.extensions)
        .build()
        .toSpecification();
  }
}
