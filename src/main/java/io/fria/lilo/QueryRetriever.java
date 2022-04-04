package io.fria.lilo;

import graphql.schema.DataFetchingEnvironment;

public interface QueryRetriever {
    String get(DataFetchingEnvironment environment);
}
