package io.firat.lilo.pojo;

import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class Schema {

    private QueryType            queryType;
    private MutationType         mutationType;
    private Map<String, Object>  subscriptionType;
    private List<TypeDefinition> types;
    private List<Directive>      directives;
}
