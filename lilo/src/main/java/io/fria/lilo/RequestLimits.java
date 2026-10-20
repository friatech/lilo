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

import org.jetbrains.annotations.Nullable;

/** Lilo's per-request limits; a {@code null} limit is not enforced. */
final class RequestLimits {

  private final @Nullable Integer maxQueryDepth;
  private final @Nullable Integer maxQueryComplexity;
  private final boolean introspectionEnabled;

  RequestLimits(
      final @Nullable Integer maxQueryDepth,
      final @Nullable Integer maxQueryComplexity,
      final boolean introspectionEnabled) {
    this.maxQueryDepth = maxQueryDepth;
    this.maxQueryComplexity = maxQueryComplexity;
    this.introspectionEnabled = introspectionEnabled;
  }

  @Nullable
  Integer getMaxQueryDepth() {
    return this.maxQueryDepth;
  }

  @Nullable
  Integer getMaxQueryComplexity() {
    return this.maxQueryComplexity;
  }

  boolean isIntrospectionEnabled() {
    return this.introspectionEnabled;
  }
}
