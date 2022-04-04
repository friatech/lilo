package io.fria.lilo;

import graphql.ExecutionInput;
import java.util.Map;
import lombok.Data;
import lombok.Getter;

@Data
@Getter
public class GraphQLRequest {

    private String              query;
    private String              operationName;
    private Map<String, Object> variables;

    public ExecutionInput toExecutionInput() {

        final ExecutionInput.Builder builder = ExecutionInput.newExecutionInput()
            .query(this.query);

        if (this.operationName != null) {
            builder.operationName(this.operationName);
        }

        if (this.variables != null) {
            builder.variables(this.variables);
        }

        return builder.build();
    }
}
