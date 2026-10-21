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
package io.fria.lilo.spring;

import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Assembles the fragments of one text message at a time and enforces the limit on its size. All
 * methods are synchronized: the buffer is guarded by itself.
 */
final class FragmentBuffer {

  /**
   * Most characters the fragment buffer keeps allocated between messages. A buffer that grew beyond
   * this for a large fragmented message is released once the message is handled, so that one large
   * event does not keep its buffer for the whole operation.
   */
  private static final int RETAINED_BUFFER_CAPACITY = 64 * 1024;

  private final int maxMessageSize;

  /** Fragments of the text message being received. */
  private final @NotNull StringBuilder fragments = new StringBuilder();

  /** Set once a message exceeded the limit: every later message is dropped. */
  private boolean oversized;

  FragmentBuffer(final int maxMessageSize) {
    this.maxMessageSize = maxMessageSize;
  }

  /**
   * Adds a fragment to the message being received.
   *
   * @return the complete message once its last fragment arrived; no message while it is incomplete
   *     or once a message exceeded the size limit. The result says so for the one fragment that
   *     made the message exceed the limit, decided under the buffer's lock.
   */
  synchronized @NotNull Result append(final @NotNull String fragment, final boolean isLast) {

    if (this.oversized) {
      return Result.NONE;
    }

    // long: the sum of two large lengths must not overflow
    final long length = (long) this.fragments.length() + fragment.length();

    if (length > this.maxMessageSize) {
      this.fragments.setLength(0);
      this.fragments.trimToSize();
      this.oversized = true;
      return Result.OVERFLOWED;
    }

    if (this.fragments.length() == 0 && isLast) {
      // The common case: a message that arrived whole
      return new Result(fragment, false);
    }

    this.fragments.append(fragment);

    if (!isLast) {
      return Result.NONE;
    }

    return new Result(this.take(), false);
  }

  /** Returns the assembled message and empties the buffer, releasing it if it grew large. */
  private @NotNull String take() {
    final String message = this.fragments.toString();
    this.fragments.setLength(0);
    if (this.fragments.capacity() > RETAINED_BUFFER_CAPACITY) {
      this.fragments.trimToSize();
    }
    return message;
  }

  /** Whether a message exceeded the limit; once true, every later message is dropped. */
  synchronized boolean oversized() {
    return this.oversized;
  }

  /** Drops the fragments received so far and releases the buffer. */
  synchronized void clear() {
    this.fragments.setLength(0);
    this.fragments.trimToSize();
  }

  /** Capacity of the fragment buffer, in characters; for tests. */
  synchronized int capacity() {
    return this.fragments.capacity();
  }

  /** What one fragment amounted to: a complete message, the overflow, or neither. */
  static final class Result {

    private static final Result NONE = new Result(null, false);
    private static final Result OVERFLOWED = new Result(null, true);

    private final @Nullable String message;
    private final boolean overflowed;

    private Result(final @Nullable String message, final boolean overflowed) {
      this.message = message;
      this.overflowed = overflowed;
    }

    /** The complete message, or empty while it is incomplete or dropped. */
    @NotNull
    Optional<String> message() {
      return Optional.ofNullable(this.message);
    }

    /** Whether this fragment made the message exceed the limit: true for one fragment only. */
    boolean overflowed() {
      return this.overflowed;
    }
  }
}
