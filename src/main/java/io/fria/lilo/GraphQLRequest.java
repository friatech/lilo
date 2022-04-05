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

    public ExecutionInput toExecutionInput(final Object localContext) {

        final ExecutionInput.Builder builder = ExecutionInput.newExecutionInput()
            .query(this.query);

        if (this.operationName != null) {
            builder.operationName(this.operationName);
        }

        if (this.variables != null || !this.variables.isEmpty()) {
            builder.variables(this.variables);
        }

        if (localContext != null) {
            builder.localContext(localContext);
        }

        return builder.build();
    }

    public ExecutionInput toExecutionInput() {

        return this.toExecutionInput(null);
    }
}
