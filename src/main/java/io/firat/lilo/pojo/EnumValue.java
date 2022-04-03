package io.firat.lilo.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class EnumValue {

    private String  name;
    private String  description;
    @JsonProperty("isDeprecated")
    private boolean isDeprecated;
    private String  deprecationReason;
}
