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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;

class RemoteIntrospectionFlowTest {

  private static final LiloContext CONTEXT = Mockito.mock(LiloContext.class);

  private SchemaSource source;
  private Logger log;

  @BeforeEach
  void setUp() {
    this.source = Mockito.mock(SchemaSource.class);
    Mockito.when(this.source.getName()).thenReturn("remote");
    this.log = Mockito.mock(Logger.class);
  }

  private static String read(final String response) {
    return "read:" + response;
  }

  private RemoteIntrospectionFlow sync(final SyncIntrospectionRetriever retriever) {
    return RemoteIntrospectionFlow.of(retriever, this.log);
  }

  private RemoteIntrospectionFlow async(final CompletableFuture<String> response) {
    return RemoteIntrospectionFlow.of(
        (AsyncIntrospectionRetriever) (liloContext, schemaSource, query, localContext) -> response,
        this.log);
  }

  private <T> CompletableFuture<Optional<T>> load(
      final RemoteIntrospectionFlow flow, final Function<String, T> read) {
    return flow.load(this.source, CONTEXT, null, read);
  }

  private void assertFailureLogged() {
    Mockito.verify(this.log).error("Could not load introspection for {}", "remote");
  }

  @Test
  void ofPicksTheFlowByRetrieverKind() {

    Assertions.assertInstanceOf(
        RemoteIntrospectionFlow.Sync.class, this.sync((c, s, q, l) -> "{}"));
    Assertions.assertInstanceOf(
        RemoteIntrospectionFlow.Async.class, this.async(new CompletableFuture<>()));
  }

  @Test
  void retrieverOfUnknownKindFailsOnUse() {

    final RemoteIntrospectionFlow flow =
        RemoteIntrospectionFlow.of(
            (IntrospectionRetriever<Object>) (liloContext, schemaSource, query, local) -> "x",
            this.log);

    Assertions.assertThrows(
        ClassCastException.class, () -> this.load(flow, RemoteIntrospectionFlowTest::read));
  }

  @Test
  void syncFlowSendsTheIntrospectionRequestOffTheCallerThread() {

    final AtomicReference<Object[]> arguments = new AtomicReference<>();
    final AtomicReference<Thread> thread = new AtomicReference<>();
    final Object localContext = new Object();
    final RemoteIntrospectionFlow flow =
        this.sync(
            (liloContext, schemaSource, query, local) -> {
              arguments.set(new Object[] {liloContext, schemaSource, query, local});
              thread.set(Thread.currentThread());
              return "response";
            });

    final Optional<String> result =
        flow.load(this.source, CONTEXT, localContext, RemoteIntrospectionFlowTest::read).join();

    Assertions.assertEquals(Optional.of("read:response"), result);
    Assertions.assertArrayEquals(
        new Object[] {
          CONTEXT, this.source, RemoteIntrospectionFlow.INTROSPECTION_REQUEST, localContext
        },
        arguments.get());
    Assertions.assertTrue(
        RemoteIntrospectionFlow.INTROSPECTION_REQUEST.contains(
            "\"operationName\":\"IntrospectionQuery\""),
        RemoteIntrospectionFlow.INTROSPECTION_REQUEST);
    Assertions.assertNotSame(Thread.currentThread(), thread.get());
    Mockito.verifyNoInteractions(this.log);
  }

  @Test
  void asyncFlowReadsTheResponseOnceTheRetrieverCompletes() {

    final CompletableFuture<String> response = new CompletableFuture<>();
    final CompletableFuture<Optional<String>> result =
        this.load(this.async(response), RemoteIntrospectionFlowTest::read);

    Assertions.assertFalse(result.isDone());

    response.complete("response");

    Assertions.assertEquals(Optional.of("read:response"), result.join());
    Mockito.verifyNoInteractions(this.log);
  }

  @Test
  void syncRetrieverFailureIsLoggedAndYieldsNothing() {

    final RemoteIntrospectionFlow flow =
        this.sync(
            (liloContext, schemaSource, query, local) -> {
              throw new IllegalStateException("down");
            });

    Assertions.assertEquals(
        Optional.empty(), this.load(flow, RemoteIntrospectionFlowTest::read).join());
    this.assertFailureLogged();
  }

  @Test
  void asyncRetrieverFailureIsLoggedAndYieldsNothing() {

    final RemoteIntrospectionFlow flow =
        this.async(CompletableFuture.failedFuture(new IllegalStateException("down")));

    Assertions.assertEquals(
        Optional.empty(), this.load(flow, RemoteIntrospectionFlowTest::read).join());
    this.assertFailureLogged();
  }

  @Test
  void failedReadIsLoggedByTheAsyncFlowButFailsTheSyncFlow() {

    final Function<String, String> failingRead =
        response -> {
          throw new IllegalArgumentException("unreadable");
        };

    Assertions.assertEquals(
        Optional.empty(),
        this.load(this.async(CompletableFuture.completedFuture("x")), failingRead).join());
    this.assertFailureLogged();

    Mockito.reset(this.log);

    final CompletionException thrown =
        Assertions.assertThrows(
            CompletionException.class,
            () -> this.load(this.sync((c, s, q, l) -> "x"), failingRead).join());
    Assertions.assertInstanceOf(IllegalArgumentException.class, thrown.getCause());
    Assertions.assertEquals("unreadable", thrown.getCause().getMessage());
    Mockito.verifyNoInteractions(this.log);
  }
}
