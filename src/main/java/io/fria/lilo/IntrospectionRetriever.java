package io.fria.lilo;

public interface IntrospectionRetriever {
    String get(LiloContext liloContext, final SchemaSource schemaSource, String query, Object context);
}
