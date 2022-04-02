package io.firat.lilo.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
public class Directive {

    private String              name;
    private String              description;
    private List<String>        locations;
    private List<ArgDefinition> ars;
    @JsonProperty("isRepeatable")
    private boolean             isRepeatable;
}
