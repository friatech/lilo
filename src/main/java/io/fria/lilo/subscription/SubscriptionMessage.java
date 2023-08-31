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
package io.fria.lilo.subscription;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SubscriptionMessage {

  private @Nullable String id;
  private @Nullable Object payload;
  private @Nullable String type;

  public SubscriptionMessage() {
    // Default constructor
  }

  public SubscriptionMessage(
      final @Nullable String id, final @Nullable Object payload, final @Nullable String type) {
    this.id = id;
    this.payload = payload;
    this.type = type;
  }

  public static @NotNull SubscriptionMessage.GraphQLSubscriptionMessageBuilder builder() {
    return new SubscriptionMessage.GraphQLSubscriptionMessageBuilder();
  }

  public @Nullable String getId() {
    return this.id;
  }

  public void setId(final @Nullable String id) {
    this.id = id;
  }

  public @Nullable Object getPayload() {
    return this.payload;
  }

  public void setPayload(final @Nullable Object payload) {
    this.payload = payload;
  }

  public @Nullable String getType() {
    return this.type;
  }

  public void setType(final @Nullable String type) {
    this.type = type;
  }

  public static final class GraphQLSubscriptionMessageBuilder {

    private @Nullable String id;
    private @Nullable Object payload;
    private @Nullable String type;

    private GraphQLSubscriptionMessageBuilder() {
      // Private constructor
    }

    public @NotNull SubscriptionMessage build() {
      return new SubscriptionMessage(this.id, this.payload, this.type);
    }

    public @NotNull SubscriptionMessage.GraphQLSubscriptionMessageBuilder id(
        final @NotNull String id) {
      this.id = id;
      return this;
    }

    public @NotNull SubscriptionMessage.GraphQLSubscriptionMessageBuilder payload(
        final @NotNull Object payload) {
      this.payload = payload;
      return this;
    }

    public @NotNull SubscriptionMessage.GraphQLSubscriptionMessageBuilder type(
        final @NotNull String type) {
      this.type = type;
      return this;
    }
  }
}
