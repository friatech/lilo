package io.firat.lilo.pojo;

import lombok.Data;

@Data
public class EnumValue {

    private String  name;
    private String  description;
    private boolean isDeprecated;
    private String  deprecationReason;
}
