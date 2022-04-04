package io.fria.lilo;

import graphql.schema.DataFetchingEnvironment;

public interface QueryRetriever {
    String get(LiloContext liloContext, DataFetchingEnvironment environment, Object context);
}
