package io.firat.lilo.pojo;

import java.util.List;
import lombok.Data;

@Data
public class InterfaceType {

    private String                name;
    private String                description;
    private String                kind;
    private List<FieldDefinition> fields;
    private List<String>          inputFields;
    private List<InterfaceType>   interfaces;
    private List<EnumValue>       enumValues;
    private List<TypeDefinition>  possibleTypes;
}
