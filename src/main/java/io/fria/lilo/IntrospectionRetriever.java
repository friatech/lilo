package io.fria.lilo;

public interface IntrospectionRetriever {
  String get(LiloContext liloContext, SchemaSource schemaSource, String query, Object context);
}
