package io.firat.lilo.pojo;

import java.util.List;
import java.util.Map;

public interface GraphQLResult<T> {

    T getData();

    List<Object> getErrors();

    Map<Object, Object> getExtensions();

    boolean isDataPresent();
}
