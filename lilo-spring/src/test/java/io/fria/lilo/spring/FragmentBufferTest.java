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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The reassembly of fragmented messages and the size limit, without a websocket session. */
class FragmentBufferTest {

  @Test
  void aWholeMessageIsReturnedAsIs() {
    final FragmentBuffer buffer = new FragmentBuffer(10);

    assertEquals(Optional.of("hello"), buffer.append("hello", true));
    assertFalse(buffer.oversized());
  }

  @Test
  void fragmentsAreJoinedInOrderWhenTheLastOneArrives() {
    final FragmentBuffer buffer = new FragmentBuffer(10);

    assertEquals(Optional.empty(), buffer.append("ab", false));
    assertEquals(Optional.empty(), buffer.append("cd", false));
    assertEquals(Optional.of("abcde"), buffer.append("e", true));
  }

  @Test
  void aMessageStartsFromScratchAfterTheLastFragment() {
    final FragmentBuffer buffer = new FragmentBuffer(10);
    buffer.append("ab", false);
    buffer.append("cd", true);

    assertEquals(Optional.empty(), buffer.append("x", false));
    assertEquals(Optional.of("xy"), buffer.append("y", true));
  }

  @Test
  void aMessageOfExactlyTheLimitIsAccepted() {
    final FragmentBuffer buffer = new FragmentBuffer(4);

    assertEquals(Optional.empty(), buffer.append("ab", false));
    assertEquals(Optional.of("abcd"), buffer.append("cd", true));
    assertFalse(buffer.oversized());
  }

  @Test
  void aWholeMessageBeyondTheLimitIsOversized() {
    final FragmentBuffer buffer = new FragmentBuffer(4);

    assertEquals(Optional.empty(), buffer.append("abcde", true));
    assertTrue(buffer.oversized());
  }

  @Test
  void fragmentsAddingUpBeyondTheLimitAreOversizedBeforeTheLastOne() {
    final FragmentBuffer buffer = new FragmentBuffer(4);

    assertEquals(Optional.empty(), buffer.append("abc", false));
    assertFalse(buffer.oversized());
    assertEquals(Optional.empty(), buffer.append("de", false));
    assertTrue(buffer.oversized());
  }

  @Test
  void everyMessageAfterAnOversizedOneIsDropped() {
    final FragmentBuffer buffer = new FragmentBuffer(4);
    buffer.append("abcde", true);

    assertEquals(Optional.empty(), buffer.append("ok", true));
    assertTrue(buffer.oversized());
  }

  @Test
  void oversizedFragmentsAreReleased() {
    final FragmentBuffer buffer = new FragmentBuffer(200_000);
    buffer.append("a".repeat(150_000), false);
    assertTrue(buffer.capacity() >= 150_000);

    buffer.append("b".repeat(100_000), false);

    assertTrue(buffer.oversized());
    assertTrue(buffer.capacity() < 150_000);
  }

  @Test
  void aLargeBufferIsTrimmedOnceTheMessageIsHandledButASmallOneIsKept() {
    final FragmentBuffer buffer = new FragmentBuffer(1_000_000);
    buffer.append("a".repeat(100_000), false);
    assertEquals(Optional.of("a".repeat(100_000) + "b"), buffer.append("b", true));
    assertTrue(buffer.capacity() <= 64 * 1024);

    buffer.append("c".repeat(100), false);
    buffer.append("d", true);
    assertTrue(buffer.capacity() >= 101, "kept for the next message");
  }

  @Test
  void clearDropsTheFragmentsReceivedSoFar() {
    final FragmentBuffer buffer = new FragmentBuffer(10);
    buffer.append("ab", false);

    buffer.clear();

    assertEquals(Optional.of("c"), buffer.append("c", true));
  }

  @Test
  void theLimitDoesNotOverflowForHugeLengths() {
    final FragmentBuffer buffer = new FragmentBuffer(Integer.MAX_VALUE);
    // Fragment lengths are ints, so only the sum could wrap; it is counted as a long
    assertEquals(Optional.empty(), buffer.append("a", false));
    assertFalse(buffer.oversized());
  }
}
