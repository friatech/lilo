package io.firat.lilo;

import graphql.schema.DataFetchingEnvironment;

public interface QueryRetriever {
    String get(DataFetchingEnvironment environment);
}
