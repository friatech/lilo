package io.fria.lilo;

public class SchemaSource {

  private final String name;
  private final IntrospectionRetriever introspectionRetriever;
  private final QueryRetriever queryRetriever;

  SchemaSource(
      final String name,
      final IntrospectionRetriever introspectionRetriever,
      final QueryRetriever queryRetriever) {
    this.name = name;
    this.introspectionRetriever = introspectionRetriever;
    this.queryRetriever = queryRetriever;
  }

  public static SchemaSourceBuilder builder() {
    return new SchemaSourceBuilder();
  }

  public IntrospectionRetriever getIntrospectionRetriever() {
    return this.introspectionRetriever;
  }

  public String getName() {
    return this.name;
  }

  public QueryRetriever getQueryRetriever() {
    return this.queryRetriever;
  }

  public static final class SchemaSourceBuilder {
    private String name;
    private IntrospectionRetriever introspectionRetriever;
    private QueryRetriever queryRetriever;

    public SchemaSource build() {
      return new SchemaSource(this.name, this.introspectionRetriever, this.queryRetriever);
    }

    public SchemaSourceBuilder introspectionRetriever(
        final IntrospectionRetriever introspectionRetrieverParam) {
      this.introspectionRetriever = introspectionRetrieverParam;
      return this;
    }

    public SchemaSourceBuilder name(final String nameParam) {
      this.name = nameParam;
      return this;
    }

    public SchemaSourceBuilder queryRetriever(final QueryRetriever queryRetrieverParam) {
      this.queryRetriever = queryRetrieverParam;
      return this;
    }

    public String toString() {
      return "SchemaSource.SchemaSourceBuilder(name="
          + this.name
          + ", introspectionRetriever="
          + this.introspectionRetriever
          + ", queryRetriever="
          + this.queryRetriever
          + ")";
    }
  }
}
