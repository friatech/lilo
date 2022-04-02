package io.firat.lilo.pojo;

import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class IntrospectionResult implements GraphQLResult<SchemaContainer> {

    private List<Object>        errors;
    private SchemaContainer     data;
    private boolean             dataPresent;
    private Map<Object, Object> extensions;
}
