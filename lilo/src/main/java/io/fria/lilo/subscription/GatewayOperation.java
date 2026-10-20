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
package io.fria.lilo.subscription;

import static io.fria.lilo.subscription.GatewayMessages.errorsOf;
import static io.fria.lilo.subscription.GatewayMessages.logFailure;
import static io.fria.lilo.subscription.GatewayMessages.sendNext;
import static io.fria.lilo.subscription.GatewayMessages.trySendComplete;
import static io.fria.lilo.subscription.GatewayMessages.trySendError;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;

/**
 * One subscription operation of a client session. It is registered before stitching, without an
 * upstream; the upstream and the gateway's subscription to it are attached once stitching finishes.
 * A cancel may land at any point: before the upstream is attached (the attach then fails and the
 * caller closes the upstream), or after (the upstream is closed here).
 */
final class GatewayOperation {

  // Logged under the handler's name: the gateway's classes are one component to an operator
  private static final Logger LOG = LoggerFactory.getLogger(SubscriptionGatewayHandler.class);

  private final @NotNull String id;
  private final @NotNull WebSocketSessionWrapper session;

  /** The state of the session this operation belongs to */
  private final @NotNull GatewaySessionRegistry.SessionState state;

  private @Nullable SubscriptionSourcePublisher upstream;
  private @Nullable Disposable subscription;
  private boolean cancelled;

  GatewayOperation(
      final @NotNull String id,
      final @NotNull WebSocketSessionWrapper session,
      final @NotNull GatewaySessionRegistry.SessionState state) {
    this.id = id;
    this.session = session;
    this.state = state;
  }

  @NotNull
  String id() {
    return this.id;
  }

  /** The client session the operation belongs to. */
  @NotNull
  WebSocketSessionWrapper session() {
    return this.session;
  }

  @NotNull
  GatewaySessionRegistry.SessionState state() {
    return this.state;
  }

  /**
   * Unregisters the operation if it is still registered. Whoever removes the operation owns its
   * cleanup, so of the client's complete, a session close and the operation's own end only the
   * first one acts.
   *
   * @return true if this call removed it; the caller then reports the end and cancels it as needed
   */
  boolean release() {
    return this.state.remove(this);
  }

  /** Whether the operation is still registered, i.e. neither completed nor cancelled meanwhile. */
  boolean isRegistered() {
    return this.state.isRegistered(this);
  }

  /**
   * Attaches the upstream the stitch produced.
   *
   * @return false if the operation was cancelled meanwhile; the caller then owns the upstream
   */
  synchronized boolean attach(final @NotNull SubscriptionSourcePublisher source) {
    if (this.cancelled) {
      return false;
    }
    this.upstream = source;
    return true;
  }

  void setSubscription(final @NotNull Disposable disposable) {
    final boolean dispose;
    synchronized (this) {
      dispose = this.cancelled;
      if (!dispose) {
        this.subscription = disposable;
      }
    }
    if (dispose) {
      disposable.dispose();
    }
  }

  /** Stops forwarding to the client, then tells the source the operation is over. */
  void cancel() {
    final Disposable currentSubscription;
    final SubscriptionSourcePublisher currentUpstream;
    synchronized (this) {
      if (this.cancelled) {
        return;
      }
      this.cancelled = true;
      currentSubscription = this.subscription;
      currentUpstream = this.upstream;
    }
    // Neither step may throw: closeSession cancels the session's other operations afterwards
    try {
      if (currentSubscription != null) {
        currentSubscription.dispose();
      }
    } catch (final RuntimeException e) {
      LOG.warn("Could not stop forwarding a subscription", e);
    }
    try {
      if (currentUpstream != null) {
        currentUpstream.close();
      }
    } catch (final RuntimeException e) {
      LOG.warn("Could not close a subscription source", e);
    }
  }

  /** Forwards an event of the source to the client, unless the operation is over. */
  void onNext(final @NotNull Object payload) {

    // Nothing follows a client complete, even an event already in flight
    if (!this.isRegistered()) {
      return;
    }

    sendNext(this.session, this.id, payload);
  }

  /** Reports the source's failure to the client and ends the operation. */
  void onError(final @NotNull Throwable e) {

    // The source failed this operation. Only report it if it was still active.
    // This also runs when sending a next to the client failed.
    if (this.release()) {
      logFailure(this.id, e);
      try {
        trySendError(this.session, this.id, errorsOf(e));
      } finally {
        // Releases the source in every case
        this.cancel();
      }
    }
  }

  /** Reports the source's end to the client and ends the operation. */
  void onComplete() {

    // The source finished this operation. Only report it if the operation was still
    // active, i.e. neither the client nor a session close cancelled it meanwhile.
    if (this.release()) {
      try {
        trySendComplete(this.session, this.id);
      } finally {
        this.cancel();
      }
    }
  }

  /** Reports an operation that could not start, unless it was cancelled meanwhile. */
  void fail(final @NotNull List<Map<String, Object>> errors) {

    if (this.release()) {
      trySendError(this.session, this.id, errors);
    }
  }
}
