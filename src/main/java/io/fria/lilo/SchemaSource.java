package io.fria.lilo;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SchemaSource {

  private final String name;
  private final IntrospectionRetriever introspectionRetriever;
  private final QueryRetriever queryRetriever;
}
