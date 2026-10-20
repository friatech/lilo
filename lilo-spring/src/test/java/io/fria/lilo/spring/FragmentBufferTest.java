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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** The reassembly of fragmented messages and the size limit, without a websocket session. */
class FragmentBufferTest {

  @Test
  void aWholeMessageIsReturnedAsIs() {
    final FragmentBuffer buffer = new FragmentBuffer(10);

    assertEquals(Optional.of("hello"), buffer.append("hello", true).message());
    assertFalse(buffer.oversized());
  }

  @Test
  void fragmentsAreJoinedInOrderWhenTheLastOneArrives() {
    final FragmentBuffer buffer = new FragmentBuffer(10);

    assertEquals(Optional.empty(), buffer.append("ab", false).message());
    assertEquals(Optional.empty(), buffer.append("cd", false).message());
    assertEquals(Optional.of("abcde"), buffer.append("e", true).message());
  }

  @Test
  void aMessageStartsFromScratchAfterTheLastFragment() {
    final FragmentBuffer buffer = new FragmentBuffer(10);
    buffer.append("ab", false);
    buffer.append("cd", true);

    assertEquals(Optional.empty(), buffer.append("x", false).message());
    assertEquals(Optional.of("xy"), buffer.append("y", true).message());
  }

  @Test
  void aMessageOfExactlyTheLimitIsAccepted() {
    final FragmentBuffer buffer = new FragmentBuffer(4);

    assertEquals(Optional.empty(), buffer.append("ab", false).message());
    assertEquals(Optional.of("abcd"), buffer.append("cd", true).message());
    assertFalse(buffer.oversized());
  }

  @Test
  void aWholeMessageBeyondTheLimitIsOversized() {
    final FragmentBuffer buffer = new FragmentBuffer(4);

    assertEquals(Optional.empty(), buffer.append("abcde", true).message());
    assertTrue(buffer.oversized());
  }

  @Test
  void fragmentsAddingUpBeyondTheLimitAreOversizedBeforeTheLastOne() {
    final FragmentBuffer buffer = new FragmentBuffer(4);

    assertEquals(Optional.empty(), buffer.append("abc", false).message());
    assertFalse(buffer.oversized());
    assertEquals(Optional.empty(), buffer.append("de", false).message());
    assertTrue(buffer.oversized());
  }

  @Test
  void everyMessageAfterAnOversizedOneIsDropped() {
    final FragmentBuffer buffer = new FragmentBuffer(4);
    buffer.append("abcde", true);

    assertEquals(Optional.empty(), buffer.append("ok", true).message());
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
    assertEquals(Optional.of("a".repeat(100_000) + "b"), buffer.append("b", true).message());
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

    assertEquals(Optional.of("c"), buffer.append("c", true).message());
  }

  @Test
  void exactlyOneOfManyConcurrentFragmentsIsReportedAsTheOverflow() throws Exception {
    final int threads = 8;
    for (int round = 0; round < 50; round++) {
      final FragmentBuffer buffer = new FragmentBuffer(4);
      final CountDownLatch start = new CountDownLatch(1);
      final AtomicInteger overflows = new AtomicInteger();
      final ExecutorService pool = Executors.newFixedThreadPool(threads);
      try {
        final List<Future<?>> done = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
          done.add(
              pool.submit(
                  () -> {
                    start.await();
                    if (buffer.append("abc", false).overflowed()) {
                      overflows.incrementAndGet();
                    }
                    return null;
                  }));
        }
        start.countDown();
        for (final Future<?> future : done) {
          future.get(10, TimeUnit.SECONDS);
        }
      } finally {
        pool.shutdownNow();
      }
      assertEquals(1, overflows.get());
      assertTrue(buffer.oversized());
    }
  }
}
