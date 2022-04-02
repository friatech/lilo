package io.firat.lilo;

import graphql.language.Field;

public interface QueryRetriever {
    String get(String url, Field field);
}
