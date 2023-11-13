/*
 * Copyright 2023 the original author or authors.
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
package io.fria.lilo.error;

import graphql.language.SourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ErrorSourceLocation extends SourceLocation {

  public ErrorSourceLocation() {
    super(0, 0);
  }

  public ErrorSourceLocation(final int line, final int column) {
    super(line, column);
  }

  public ErrorSourceLocation(final int line, final int column, final @Nullable String sourceName) {
    super(line, column, sourceName);
  }

  @Override
  public boolean equals(final @Nullable Object o) {
    return super.equals(o);
  }

  @Override
  public int getColumn() {
    return super.getColumn();
  }

  @Override
  public int getLine() {
    return super.getLine();
  }

  @Override
  public @Nullable String getSourceName() {
    return super.getSourceName();
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }

  @Override
  public @NotNull String toString() {
    return super.toString();
  }
}
