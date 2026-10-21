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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** The response size cap of the default retrievers, without an HTTP exchange. */
class BoundedBodySubscriberTest {

  private static ByteBuffer bytes(final String text) {
    return ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
  }

  private static final class RecordingSubscription implements Flow.Subscription {

    final AtomicBoolean cancelled = new AtomicBoolean();
    long requested;

    @Override
    public void request(final long n) {
      this.requested = n;
    }

    @Override
    public void cancel() {
      this.cancelled.set(true);
    }
  }

  @Test
  void collectsChunksUpToTheLimit() throws Exception {
    final BoundedBodySubscriber subscriber = new BoundedBodySubscriber(6);
    final RecordingSubscription subscription = new RecordingSubscription();
    subscriber.onSubscribe(subscription);

    subscriber.onNext(List.of(bytes("abc"), bytes("def")));
    subscriber.onComplete();

    final CompletableFuture<byte[]> body = subscriber.getBody().toCompletableFuture();
    assertArrayEquals("abcdef".getBytes(StandardCharsets.UTF_8), body.get());
    assertEquals(Long.MAX_VALUE, subscription.requested);
    assertFalse(subscription.cancelled.get());
  }

  @Test
  void aChunkBeyondTheLimitCancelsTheTransferAndFailsTheBody() {
    final BoundedBodySubscriber subscriber = new BoundedBodySubscriber(5);
    final RecordingSubscription subscription = new RecordingSubscription();
    subscriber.onSubscribe(subscription);

    subscriber.onNext(List.of(bytes("abc")));
    subscriber.onNext(List.of(bytes("def")));

    assertTrue(subscription.cancelled.get());
    final ExecutionException e =
        assertThrows(
            ExecutionException.class, () -> subscriber.getBody().toCompletableFuture().get());
    assertInstanceOf(BoundedBodySubscriber.ResponseTooLargeException.class, e.getCause());
  }

  @Test
  void chunksAfterTheFailureAreIgnored() {
    final BoundedBodySubscriber subscriber = new BoundedBodySubscriber(2);
    subscriber.onSubscribe(new RecordingSubscription());
    subscriber.onNext(List.of(bytes("abc")));

    subscriber.onNext(List.of(bytes("a")));
    subscriber.onComplete();

    assertTrue(subscriber.getBody().toCompletableFuture().isCompletedExceptionally());
  }

  @Test
  void aBodyOfExactlyTheLimitIsAccepted() throws Exception {
    final BoundedBodySubscriber subscriber = new BoundedBodySubscriber(3);
    subscriber.onSubscribe(new RecordingSubscription());

    subscriber.onNext(List.of(bytes("abc")));
    subscriber.onComplete();

    assertEquals(3, subscriber.getBody().toCompletableFuture().get().length);
  }

  @Test
  void aTransportErrorFailsTheBody() {
    final BoundedBodySubscriber subscriber = new BoundedBodySubscriber(10);
    subscriber.onSubscribe(new RecordingSubscription());

    subscriber.onError(new IllegalStateException("boom"));

    final ExecutionException e =
        assertThrows(
            ExecutionException.class, () -> subscriber.getBody().toCompletableFuture().get());
    assertInstanceOf(IllegalStateException.class, e.getCause());
  }
}
