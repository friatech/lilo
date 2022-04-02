package io.firat.lilo.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
public class FieldDefinition {

    private String              name;
    private String              description;
    private List<ArgDefinition> args;
    private Type                type;
    @JsonProperty("isDeprecated")
    private boolean             isDeprecated;
    private String              deprecationReason;
}
