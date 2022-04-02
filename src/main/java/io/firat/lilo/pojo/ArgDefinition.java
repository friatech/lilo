package io.firat.lilo.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ArgDefinition {

    private String  name;
    private String  description;
    private Type    type;
    private boolean defaultValue;
    @JsonProperty("isDeprecated")
    private boolean isDeprecated;
    private String  deprecationReason;
}
