package io.fria.lilo;

import graphql.ExecutionInput;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
