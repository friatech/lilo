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

  @NotNull
  public static SchemaSourceBuilder builder() {
    return new SchemaSourceBuilder();
  }

  @NotNull
  public IntrospectionRetriever getIntrospectionRetriever() {
    return this.introspectionRetriever;
  }

  @NotNull
  public String getName() {
    return this.name;
  }

  @NotNull
  public QueryRetriever getQueryRetriever() {
    return this.queryRetriever;
  }

  @NotNull
  @Override
  public String toString() {
    return "SchemaSource{" + "name='" + this.name + '\'' + '}';
  }

  public static final class SchemaSourceBuilder {
    private String name;
    private IntrospectionRetriever introspectionRetriever;
    private QueryRetriever queryRetriever;

    @NotNull
    public SchemaSource build() {
      return new SchemaSource(this.name, this.introspectionRetriever, this.queryRetriever);
    }

    @NotNull
    public SchemaSourceBuilder introspectionRetriever(
        @NotNull final IntrospectionRetriever introspectionRetrieverParam) {
      this.introspectionRetriever = Objects.requireNonNull(introspectionRetrieverParam);
      return this;
    }

    @NotNull
    public SchemaSourceBuilder name(@NotNull final String nameParam) {
      this.name = Objects.requireNonNull(nameParam);
      return this;
    }

    @NotNull
    public SchemaSourceBuilder queryRetriever(@NotNull final QueryRetriever queryRetrieverParam) {
      this.queryRetriever = Objects.requireNonNull(queryRetrieverParam);
      return this;
    }
  }
}
