package io.firat.lilo.pojo;

import java.util.List;
import lombok.Data;

@Data
public class TypeDefinition {

    private String                kind;
    private String                name;
    private String                description;
    private List<FieldDefinition> fields;
    private List<String>          inputFields;
    private List<String>          interfaces;
    private List<EnumValue>       enumValues;
    private List<String>          possibleTypes;
}
