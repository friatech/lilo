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

import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import org.jetbrains.annotations.Nullable;

/** Collects the body chunk by chunk and cancels the transfer once the size limit is exceeded. */
final class BoundedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {

  private final long maxBytes;
  private final CompletableFuture<byte[]> result = new CompletableFuture<>();
  private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
  private @Nullable Flow.Subscription subscription;

  BoundedBodySubscriber(final long maxBytes) {
    this.maxBytes = maxBytes;
  }

  @Override
  public CompletionStage<byte[]> getBody() {
    // A read-only view, so callers cannot complete the subscriber's future
    return this.result.minimalCompletionStage();
  }

  @Override
  public void onComplete() {
    this.result.complete(this.buffer.toByteArray());
  }

  @Override
  public void onError(final Throwable throwable) {
    this.result.completeExceptionally(throwable);
  }

  @Override
  public void onNext(final List<ByteBuffer> items) {

    if (this.result.isDone()) {
      return;
    }

    for (final ByteBuffer item : items) {
      final int remaining = item.remaining();

      if (this.buffer.size() + (long) remaining > this.maxBytes) {
        if (this.subscription != null) {
          this.subscription.cancel();
        }

        this.result.completeExceptionally(new ResponseTooLargeException());
        return;
      }

      final byte[] bytes = new byte[remaining];
      item.get(bytes);
      this.buffer.write(bytes, 0, remaining);
    }
  }

  @Override
  public void onSubscribe(final Flow.Subscription newSubscription) {
    this.subscription = newSubscription;
    newSubscription.request(Long.MAX_VALUE);
  }

  static final class ResponseTooLargeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private ResponseTooLargeException() {
      super("Response body exceeded the configured maxResponseBytes");
    }
  }
}
