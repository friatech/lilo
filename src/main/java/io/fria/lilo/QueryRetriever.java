package io.fria.lilo;

public interface QueryRetriever {
    String get(LiloContext liloContext, String query, Object context);
}
