package io.fria.lilo;

import graphql.schema.idl.TypeDefinitionRegistry;
import java.util.Map;

class ProcessedSchemaSource {

    private final SchemaSource           schemaSource;
    private final Map<String, Object>    schema;
    private final TypeDefinitionRegistry typeDefinitionRegistry;

    ProcessedSchemaSource(final SchemaSource schemaSource, final Map<String, Object> schema, final TypeDefinitionRegistry typeDefinitionRegistry) {
        this.schemaSource = schemaSource;
        this.schema = schema;
        this.typeDefinitionRegistry = typeDefinitionRegistry;
    }

    Map<String, Object> getSchema() {
        return this.schema;
    }

    SchemaSource getSchemaSource() {
        return this.schemaSource;
    }

    TypeDefinitionRegistry getTypeDefinitionRegistry() {
        return this.typeDefinitionRegistry;
    }
}
