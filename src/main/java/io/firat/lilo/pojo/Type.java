package io.firat.lilo.pojo;

import lombok.Data;

@Data
public class Type {

    private String kind;
    private String name;
    private Type   ofType;
}
