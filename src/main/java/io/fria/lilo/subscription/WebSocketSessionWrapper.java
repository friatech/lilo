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

/**
 * WebSocketSessionWrapper encapsulates the basic session operations for making communication
 * framework-agnostic.
 */
public interface WebSocketSessionWrapper {

  /** Closes the websocket session */
  void close();

  /**
   * indicates that session is open or not
   *
   * @return true for open session, false for closed session
   */
  boolean isOpen();

  /**
   * Getter for publisher
   *
   * @return websocket message stream publisher
   */
  @Nullable
  SubscriptionSourcePublisher getPublisher();

  /**
   * Sends a string message
   *
   * @param message websocket string message
   */
  void send(@NotNull String message);

  /**
   * Setter for publisher
   *
   * @param publisher websocket message stream publisher
   */
  void setPublisher(@Nullable SubscriptionSourcePublisher publisher);
}
