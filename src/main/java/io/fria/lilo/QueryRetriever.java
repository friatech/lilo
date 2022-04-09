package io.fria.lilo;

public interface QueryRetriever {
  String get(LiloContext liloContext, SchemaSource schemaSource, String query, Object context);
}
