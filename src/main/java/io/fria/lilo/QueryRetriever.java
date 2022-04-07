package io.fria.lilo;

public interface QueryRetriever {
    String get(LiloContext liloContext, final SchemaSource schemaSource, String query, Object context);
}
