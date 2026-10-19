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
package io.fria.lilo.default_retrievers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import io.fria.lilo.DefaultRemoteIntrospectionRetriever;
import io.fria.lilo.DefaultRemoteQueryRetriever;
import io.fria.lilo.GraphQLQuery;
import io.fria.lilo.Lilo;
import io.fria.lilo.LiloContext;
import io.fria.lilo.RemoteRetrieverOptions;
import io.fria.lilo.RemoteSchemaSource;
import io.fria.lilo.SchemaSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultRetrieverTest {

  private static final String QUERY = "{\"query\":\"{x}\"}";
  private static final String DATA = "{\"data\":{\"x\":1}}";
  private static final String LOOPBACK = "127.0.0.1";

  private final CountDownLatch release = new CountDownLatch(1);
  private ExecutorService executor;
  private HttpServer server;
  private LiloContext liloContext;
  private SchemaSource schemaSource;

  @BeforeEach
  void setUp() throws IOException {
    this.executor = Executors.newCachedThreadPool();
    this.server = HttpServer.create(new InetSocketAddress(InetAddress.getByName(LOOPBACK), 0), 0);
    this.server.setExecutor(this.executor);
    this.server.start();
    this.liloContext = Lilo.builder().build().getContext();
    this.schemaSource = RemoteSchemaSource.create("source", this.url("/unused"));
  }

  @AfterEach
  void tearDown() {
    this.release.countDown();
    this.server.stop(0);
    this.executor.shutdownNow();
  }

  @Test
  void postsJsonWithAcceptHeader() {

    final AtomicReference<String> method = new AtomicReference<>();
    final AtomicReference<String> accept = new AtomicReference<>();
    final AtomicReference<String> contentType = new AtomicReference<>();
    final AtomicReference<String> requestBody = new AtomicReference<>();

    this.handle(
        "/graphql",
        exchange -> {
          method.set(exchange.getRequestMethod());
          accept.set(exchange.getRequestHeaders().getFirst("Accept"));
          contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
          requestBody.set(readBody(exchange));
          respond(exchange, 200, DATA);
        });

    final String result = this.introspect(new DefaultRemoteIntrospectionRetriever(this.url()));

    Assertions.assertEquals(DATA, result);
    Assertions.assertEquals("POST", method.get());
    Assertions.assertEquals("application/json", accept.get());
    Assertions.assertEquals("application/json", contentType.get());
    Assertions.assertEquals(QUERY, requestBody.get());
  }

  @Test
  void customHeadersAreSent() {

    final AtomicReference<String> authorization = new AtomicReference<>();

    this.handle(
        "/graphql",
        exchange -> {
          authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
          respond(exchange, 200, DATA);
        });

    final RemoteRetrieverOptions options =
        RemoteRetrieverOptions.builder()
            .headers((context, localContext) -> Map.of("Authorization", (String) localContext))
            .build();

    final DefaultRemoteQueryRetriever retriever =
        new DefaultRemoteQueryRetriever(this.url(), options);

    final String result =
        retriever.get(this.liloContext, this.schemaSource, graphQLQuery(), "Basic dXNlcjpwYXNz");

    Assertions.assertEquals(DATA, result);
    Assertions.assertEquals("Basic dXNlcjpwYXNz", authorization.get());
  }

  @Test
  void headersFunctionReceivesLiloContext() {

    final AtomicReference<LiloContext> received = new AtomicReference<>();

    this.handle("/graphql", exchange -> respond(exchange, 200, DATA));

    final RemoteRetrieverOptions options =
        RemoteRetrieverOptions.builder()
            .headers(
                (context, localContext) -> {
                  received.set(context);
                  return Map.of();
                })
            .build();

    this.introspect(new DefaultRemoteIntrospectionRetriever(this.url(), options));

    Assertions.assertSame(this.liloContext, received.get());
  }

  @Test
  void invalidHeaderValueIsNotLeaked() {

    final RemoteRetrieverOptions options =
        RemoteRetrieverOptions.builder()
            .headers((context, localContext) -> Map.of("Authorization", "secret\r\nvalue"))
            .build();

    final DefaultRemoteIntrospectionRetriever retriever =
        new DefaultRemoteIntrospectionRetriever(this.url(), options);

    final IllegalArgumentException exception =
        Assertions.assertThrows(IllegalArgumentException.class, () -> this.introspect(retriever));

    Assertions.assertTrue(exception.getMessage().contains("Authorization"));
    assertNotLeaked(exception, "secret");
  }

  @Test
  void readTimeoutFires() {

    this.handle(
        "/graphql",
        exchange -> {
          try {
            this.release.await(5, TimeUnit.SECONDS);
          } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          respond(exchange, 200, DATA);
        });

    final RemoteRetrieverOptions options =
        RemoteRetrieverOptions.builder().requestTimeout(Duration.ofMillis(200)).build();

    final DefaultRemoteIntrospectionRetriever retriever =
        new DefaultRemoteIntrospectionRetriever(this.url(), options);

    final IllegalStateException exception =
        Assertions.assertTimeoutPreemptively(
            Duration.ofSeconds(2),
            () ->
                Assertions.assertThrows(
                    IllegalStateException.class, () -> this.introspect(retriever)));

    Assertions.assertTrue(exception.getMessage().contains("timed out"), exception.getMessage());
  }

  @Test
  void graphQlErrorBodyOn400IsReturned() {

    final String errorBody = "{\"errors\":[{\"message\":\"bad\"}]}";
    this.handle("/graphql", exchange -> respond(exchange, 400, errorBody));

    Assertions.assertEquals(
        errorBody, this.introspect(new DefaultRemoteIntrospectionRetriever(this.url())));
  }

  @Test
  void plainErrorBodyOn500Throws() {

    this.handle("/graphql", exchange -> respond(exchange, 500, "boom"));

    final DefaultRemoteIntrospectionRetriever retriever =
        new DefaultRemoteIntrospectionRetriever(this.url());

    final IllegalStateException exception =
        Assertions.assertThrows(IllegalStateException.class, () -> this.introspect(retriever));

    Assertions.assertTrue(exception.getMessage().contains("500"), exception.getMessage());
    Assertions.assertTrue(exception.getMessage().contains("boom"), exception.getMessage());
  }

  @Test
  void longErrorBodyIsTruncated() {

    final String body = "x".repeat(10_000);
    this.handle("/graphql", exchange -> respond(exchange, 502, body));

    final DefaultRemoteIntrospectionRetriever retriever =
        new DefaultRemoteIntrospectionRetriever(this.url());

    final IllegalStateException exception =
        Assertions.assertThrows(IllegalStateException.class, () -> this.introspect(retriever));

    Assertions.assertTrue(exception.getMessage().contains("502"), exception.getMessage());
    Assertions.assertTrue(exception.getMessage().length() < 1024, "message is not truncated");
  }

  @Test
  void errorMessageDoesNotLeakHeaderValues() {

    this.handle("/graphql", exchange -> respond(exchange, 500, "boom"));

    final RemoteRetrieverOptions options =
        RemoteRetrieverOptions.builder()
            .headers((context, localContext) -> Map.of("Authorization", "Bearer secret-token"))
            .build();

    final DefaultRemoteIntrospectionRetriever retriever =
        new DefaultRemoteIntrospectionRetriever(this.url(), options);

    final IllegalStateException exception =
        Assertions.assertThrows(IllegalStateException.class, () -> this.introspect(retriever));

    assertNotLeaked(exception, "secret-token");
  }

  @Test
  void redirectsAreNotFollowedByDefault() {

    this.handle("/target", exchange -> respond(exchange, 200, DATA));
    this.handle(
        "/graphql",
        exchange -> {
          exchange.getResponseHeaders().set("Location", this.url("/target"));
          respond(exchange, 302, "");
        });

    final DefaultRemoteIntrospectionRetriever retriever =
        new DefaultRemoteIntrospectionRetriever(this.url());

    final IllegalStateException exception =
        Assertions.assertThrows(IllegalStateException.class, () -> this.introspect(retriever));

    Assertions.assertTrue(exception.getMessage().contains("302"), exception.getMessage());
  }

  @Test
  void redirectsAreFollowedWhenEnabled() {

    this.handle("/target", exchange -> respond(exchange, 200, DATA));
    this.handle(
        "/graphql",
        exchange -> {
          exchange.getResponseHeaders().set("Location", this.url("/target"));
          respond(exchange, 307, "");
        });

    final RemoteRetrieverOptions options =
        RemoteRetrieverOptions.builder().followRedirects(true).build();

    Assertions.assertEquals(
        DATA, this.introspect(new DefaultRemoteIntrospectionRetriever(this.url(), options)));
  }

  @Test
  void oversizedResponseIsRejected() {

    final byte[] body = new byte[1024 * 1024];
    Arrays.fill(body, (byte) 'a');

    this.handle(
        "/graphql",
        exchange -> {
          exchange.sendResponseHeaders(200, body.length);
          try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
          } catch (final IOException e) {
            // The client aborts the transfer once the limit is exceeded
          }
        });

    final RemoteRetrieverOptions options =
        RemoteRetrieverOptions.builder().maxResponseBytes(1024).build();

    final DefaultRemoteIntrospectionRetriever retriever =
        new DefaultRemoteIntrospectionRetriever(this.url(), options);

    final IllegalStateException exception =
        Assertions.assertThrows(IllegalStateException.class, () -> this.introspect(retriever));

    Assertions.assertTrue(exception.getMessage().contains("1024"), exception.getMessage());
  }

  @Test
  void nonHttpSchemeRejected() {

    final DefaultRemoteIntrospectionRetriever retriever =
        new DefaultRemoteIntrospectionRetriever("file:///etc/passwd");

    Assertions.assertThrows(IllegalArgumentException.class, () -> this.introspect(retriever));
  }

  @Test
  void interruptFlagIsRestored() {

    this.handle(
        "/graphql",
        exchange -> {
          try {
            this.release.await(5, TimeUnit.SECONDS);
          } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          respond(exchange, 200, DATA);
        });

    final DefaultRemoteIntrospectionRetriever retriever =
        new DefaultRemoteIntrospectionRetriever(this.url());

    Thread.currentThread().interrupt();

    try {
      Assertions.assertThrows(IllegalStateException.class, () -> this.introspect(retriever));
      Assertions.assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void invalidOptionsAreRejected() {

    final RemoteRetrieverOptions.Builder builder = RemoteRetrieverOptions.builder();

    Assertions.assertThrows(IllegalArgumentException.class, () -> builder.maxResponseBytes(0));
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> builder.connectTimeout(Duration.ZERO));
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> builder.requestTimeout(Duration.ofSeconds(-1)));
  }

  @Test
  void defaultsAreDocumentedValues() {

    final RemoteRetrieverOptions options = RemoteRetrieverOptions.defaults();

    Assertions.assertEquals(Duration.ofSeconds(5), options.getConnectTimeout());
    Assertions.assertEquals(Duration.ofSeconds(30), options.getRequestTimeout());
    Assertions.assertEquals(10L * 1024 * 1024, options.getMaxResponseBytes());
    Assertions.assertFalse(options.isFollowRedirects());
    Assertions.assertEquals(Map.of(), options.getHeaders().apply(this.liloContext, null));
  }

  private static void assertNotLeaked(
      final @NotNull Throwable throwable, final @NotNull String value) {

    for (Throwable current = throwable; current != null; current = current.getCause()) {
      final String message = current.getMessage();
      Assertions.assertFalse(
          message != null && message.contains(value), "header value leaked: " + current);
    }
  }

  private static @NotNull GraphQLQuery graphQLQuery() {
    return GraphQLQuery.builder()
        .query(QUERY)
        .operationType(OperationDefinition.Operation.QUERY)
        .queryNode(new Field("x"))
        .arguments(Map.of())
        .build();
  }

  private static @NotNull String readBody(final @NotNull HttpExchange exchange) throws IOException {
    try (InputStream inputStream = exchange.getRequestBody()) {
      return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static void respond(
      final @NotNull HttpExchange exchange, final int status, final @NotNull String body)
      throws IOException {

    final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);

    try (OutputStream outputStream = exchange.getResponseBody()) {
      outputStream.write(bytes);
    }
  }

  private void handle(final @NotNull String path, final @NotNull HttpHandler handler) {
    this.server.createContext(path, handler);
  }

  private @NotNull String introspect(final @NotNull DefaultRemoteIntrospectionRetriever retriever) {
    return retriever.get(this.liloContext, this.schemaSource, QUERY, null);
  }

  private @NotNull String url() {
    return this.url("/graphql");
  }

  private @NotNull String url(final @NotNull String path) {
    return "http://" + LOOPBACK + ":" + this.server.getAddress().getPort() + path;
  }
}
