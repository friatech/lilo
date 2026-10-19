# Changelog

All notable changes to Lilo are documented in this file. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/). The version number of each release is assigned by the
release process, so changes are listed under `Unreleased` until a release is cut.

## [Unreleased]

### Changed

Upgrade and API notes (the API changes argue for a non-patch version bump):

- Building Lilo needs JDK 21. The produced bytecode still targets Java 11 (`lilo`) and Java 17 (`lilo-spring`).
- `lilo-spring` requires Spring 7 and Spring Boot 4 and no longer works with Spring Boot 3.
- `GraphQLQuery`, `GraphQLRequest`, `LiloContext` and `SubscriptionGatewayHandler` are now `final`.
- Collections returned by `GraphQLRequest`, `GraphQLQuery`, `GraphQLResult`, `GraphQLResultError` and `LiloSourceDataFetcherException` getters are read-only, and setters and builders store copies. `GraphQLQuery` rejects null `arguments`.
- graphql-java 23+ strict wiring: stitching two sources that declare the same root field fails at merge with `StrictModeWiringException`.
- `org.jetbrains:annotations` is now `provided` in `lilo`, so it is no longer a transitive dependency of consumers.

Stitching engine (behavior changes for existing users):

- Shared types that differ structurally between sources now fail at merge with an `IllegalArgumentException` naming the type and both sources. Identical definitions are accepted; descriptions, comments and member order are ignored.
- Remote queries now include an injected `__typename` and nested aliases. This matters for persisted-query allowlists and for retrievers that inspect the query text.
- `stitch` throws the root cause (for example `IllegalArgumentException` or `SchemaProblem`) instead of `RuntimeException(ExecutionException)`. A missing `__typename` on an abstract value throws from `stitch` rather than returning a GraphQL error.
- `DefaultRemoteQueryRetriever` and `DefaultRemoteIntrospectionRetriever` use `java.net.http.HttpClient` with a 5 s connect timeout, a 30 s request timeout, a 10 MiB response cap, no redirects, `http`/`https` only and `Accept: application/json`. Failures are `IllegalStateException` or `IllegalArgumentException`. A subclass that overrides only the two-argument `retrieve` is no longer called by `get`.
- `stitchAsync` on a warm context runs on the caller's thread.
- `GraphQLRequest.toExecutionInput` now throws `IllegalArgumentException("GraphQL request must contain a query")` for a null or blank query instead of graphql-java's `AssertException`.

Subscriptions (behavior changes):

- A client that sends `subscribe` before `connection_init` is now disconnected, and a second `connection_init` on a session closes it.
- A `SubscriptionRetriever` now receives a `SubscriptionConnectionContext` (the `connection_init` payload and the handshake headers) as its `localContext` argument.
- A session may run at most 50 operations at once, and stitching for subscriptions runs on a bounded pool. When a limit is hit the client gets an `error` frame ("Too many subscriptions on this connection" or "Too many concurrent subscription requests") instead of an exception.
- Subscription errors (invalid payload, GraphQL errors, non-subscription operations, remote source failures) are reported to the client as `error` frames.
- `SubscriptionWebSocketConfigurer`'s public constructor now takes the `GatewayWebSocketHandler` and the `LiloConfigProperties`. Applications that only use `@EnableLilo` are unaffected; code that constructs it directly must pass the properties.
- `@EnableLilo` now also enables Spring's WebSocket support.
- Surefire runs with the Mockito agent (`-javaagent` and `-Xshare:off` in `argLine`) in `lilo` and `lilo-spring`; test JVMs only.

### Added

- `GraphQLRequest` supports an optional `extensions` map (JSON, builder, getter/setter, defensively copied) that is passed on to the `ExecutionInput`; it is omitted from the JSON when absent.
- Subscriptions: `ping`/`pong` and `error` messages, several operations per socket with per-operation `complete`, source handler lifecycle fixes (one `subscribe` per source, sources released on cancel and send failures), and `SubscriptionConnectionContext`.
- `lilo-spring` properties: `lilo.allowed-origins` (default `*`; an empty list allows only same-origin requests), `lilo.send-time-limit` (default `10s`) and `lilo.send-buffer-size-limit` (default `524288` bytes). A slow client is disconnected with close status 4500 and its subscriptions are released. Client sends are no longer racy across threads.
- `RemoteRetrieverOptions` for the default remote retrievers (timeouts, response cap, header hook).
- `Lilo.builder()` options `maxQueryDepth`, `maxQueryComplexity` (graphql-java's standard instrumentations, chained before the user's instrumentation), `introspectionEnabled` (per instance, default `true`) and `graphQLCustomizer` (applied last when the merged schema is built).
- `lilo-spring` tests, configuration metadata descriptions and defaults.
- SpotBugs runs in `verify` for `lilo` and `lilo-spring` and fails the build on findings.
- A JDK 11 test job, Dependabot, CodeQL and hardened (pinned, least-privilege) GitHub Actions workflows. The JDK 11 job has not run in CI yet.
- README sections "Compatibility and upgrade notes", "Behavior and limitations" (including "Subscriptions"), `CONTRIBUTING.md`, `SECURITY.md` and GitHub templates.

### Fixed

- Conflicting shared types no longer let the first definition win silently.
- Interface and union fields work because the gateway injects `__typename` into remote queries and resolves abstract types from it; an aliased or directive-carrying `__typename` no longer suppresses the injection.
- Nested aliases (including inside fragments, and the same field twice with different arguments) are forwarded and read back by result key.
- When a remote returns data and errors, the data is kept and the errors are reported. The interrupt flag is restored, and an all-sources-down failure is no longer wrapped in `ExecutionException`.
- Schema loading is single-flight (one introspection per source for concurrent cold requests), `invalidate*` during a load is not lost, and an `Error` while a load starts no longer wedges the context.
- Subscriptions no longer throw on a subscribe without an id or on a dead socket, and operations registered after a session close are cancelled.

### Dependencies

- graphql-java 22.1 to 26.1, reactor-core 3.8.7, jackson 2.22.4, Spring 7.0.9, Spring Boot 4.1.1 (samples and `lilo-spring`), JUnit Jupiter 5.14.4, Mockito 5.24.0.
- Error Prone 2.50.0, Checkstyle 14.3.0, SpotBugs 4.10.5, fmt 2.29, maven-gpg-plugin 3.2.8.

### Known limitations

- Partial-result remote errors bypass a custom `DataFetcherExceptionHandler` and keep the remote's paths.
- A client alias named `__typename` conflicts with the injected field at the remote.
- Single-flight loading uses the first caller's `localContext`.
- There is no timeout or backoff on schema loading; a hung introspection blocks requests until it completes.
- The send limits are checked when another frame is queued; the servlet container's own send timeout still bounds the frame being written.
