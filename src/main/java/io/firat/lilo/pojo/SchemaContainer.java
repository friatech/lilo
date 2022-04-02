package io.firat.lilo.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SchemaContainer {

    @JsonProperty("__schema")
    private Schema schema;
}
