package io.fria.lilo;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import java.util.HashMap;
import java.util.Map;

public final class Lilo {

    private final LiloContext context;

    private Lilo(final LiloContext context) {
        this.context = context;
    }

    public LiloContext getContext() {
        return this.context;
    }

    public static LiloBuilder builder() {
        return new LiloBuilder();
    }

    public ExecutionResult stitch(final ExecutionInput executionInput) {

        return this.context.getGraphQL().execute(executionInput);
    }

    public static class LiloBuilder {

        private final Map<String, SchemaSource> schemaSources = new HashMap<>();

        private LiloBuilder() {
        }

        public LiloBuilder addSource(final SchemaSource schemaSource) {
            this.schemaSources.put(schemaSource.getName(), schemaSource);
            return this;
        }

        public Lilo build() {

            try {
                return new Lilo(new LiloContext(this.schemaSources.values().toArray(new SchemaSource[0])));
            } catch (final Exception e) {
                e.printStackTrace();
            }

            return null;
        }
    }
}
