# Lilo Spring Library

`lilo-spring` is mostly developed for mitigating the complexity of subscription configuration. In the future endpoint definition and
zero-code lilo-configurations features will be added but currently the focus of the library is making subscription GraphQL stitching is
developer-friendly.

## Installation

Just add a single **Lilo** dependency to your `pom.xml` file.

```xml
<dependencies>
  ...
  <dependency>
    <groupId>io.fria</groupId>
    <artifactId>lilo-spring</artifactId>
    <version>24.5.0</version>
  </dependency>
  ...
</dependencies>
```

If you're using gradle add the dependency to your `build.gradle` file.

```groovy
implementation 'io.fria:lilo-spring:24.5.0'
```

## Usage

Once you added the library you can enable Lilo by adding `@EnableLilo` annotation. `lilo-spring` library expects you to define
a basic Lilo bean definition for making configuration complete.

```java
@Bean
public Lilo lilo() {

  return Lilo.builder()
      .addSource(
          RemoteSchemaSource.create(
              SOURCE1_NAME,
              new MyRemoteIntrospectionRetriever(SOURCE1_GRAPHQL_URL),
              new MyRemoteQueryRetriever(SOURCE1_GRAPHQL_URL),
              new MySubscriptionRetriever(SOURCE1_GRAPHQL_WS_URL)))
      .addSource(
          RemoteSchemaSource.create(
              SOURCE2_NAME,
              new MyRemoteIntrospectionRetriever(SOURCE2_GRAPHQL_URL),
              new MyRemoteQueryRetriever(SOURCE2_GRAPHQL_URL),
              new MySubscriptionRetriever(SOURCE2_GRAPHQL_WS_URL)))
      .build();
}
```

For final touch you need a Controller method for stitching operations other than subscription. 

```java
@ResponseBody
@PostMapping("/graphql")
public @NotNull Map<String, Object> stitch(@RequestBody final @NotNull GraphQLRequest request) {
  return this.lilo.stitch(request.toExecutionInput()).toSpecification();
}
```

You're ready to go.

# Additional configurations

If you are tend to use a subscription path other than `/graphql` you can define it in your SpringBoot application configuration.

```yaml
lilo:
  graphQlPath: /my-graphql-endpoint
  subscriptionPath: /my-graphql-subscription-endpoint
```
