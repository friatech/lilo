package io.fria.lilo;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public class SchemaSource {

  private final String name;
  private final IntrospectionRetriever introspectionRetriever;
  private final QueryRetriever queryRetriever;

  SchemaSource(
      @NotNull final String name,
      @NotNull final IntrospectionRetriever introspectionRetriever,
      @NotNull final QueryRetriever queryRetriever) {
    this.name = Objects.requireNonNull(name);
    this.introspectionRetriever = Objects.requireNonNull(introspectionRetriever);
    this.queryRetriever = Objects.requireNonNull(queryRetriever);
  }

  public static @NotNull SchemaSourceBuilder builder() {
    return new SchemaSourceBuilder();
  }

  public @NotNull IntrospectionRetriever getIntrospectionRetriever() {
    return this.introspectionRetriever;
  }

  public @NotNull String getName() {
    return this.name;
  }

  public @NotNull QueryRetriever getQueryRetriever() {
    return this.queryRetriever;
  }

  @Override
  public @NotNull String toString() {
    return "SchemaSource{" + "name='" + this.name + '\'' + '}';
  }

  public static final class SchemaSourceBuilder {
    private String name;
    private IntrospectionRetriever introspectionRetriever;
    private QueryRetriever queryRetriever;

    public @NotNull SchemaSource build() {
      return new SchemaSource(this.name, this.introspectionRetriever, this.queryRetriever);
    }

    public @NotNull SchemaSourceBuilder introspectionRetriever(
        @NotNull final IntrospectionRetriever introspectionRetrieverParam) {
      this.introspectionRetriever = Objects.requireNonNull(introspectionRetrieverParam);
      return this;
    }

    public @NotNull SchemaSourceBuilder name(@NotNull final String nameParam) {
      this.name = Objects.requireNonNull(nameParam);
      return this;
    }

    public @NotNull SchemaSourceBuilder queryRetriever(
        @NotNull final QueryRetriever queryRetrieverParam) {
      this.queryRetriever = Objects.requireNonNull(queryRetrieverParam);
      return this;
    }
  }
}
