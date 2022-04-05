package io.fria.lilo;

public interface IntrospectionRetriever {
    String get(LiloContext liloContext, String query, Object context);
}
