package io.fria.lilo;

import graphql.ErrorClassification;
import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.language.SourceLocation;
import java.util.List;
import java.util.stream.Collectors;

public class LiloGraphQLError implements GraphQLError {

    private String               message;
    private List<LiloSourceLocation> locations;
    private ErrorType            errorType;
    private List<Object>               path;

    public LiloGraphQLError() {
    }

    @Override
    public List<Object> getPath() {
        return this.path;
    }

    public void setPath(final List<Object> path) {
        this.path = path;
    }

    @Override
    public String getMessage() {
        return this.message;
    }

    @Override
    public List<SourceLocation> getLocations() {

        if (this.locations == null) {
            return null;
        }

        return this.locations
            .stream()
            .map(l -> new LiloSourceLocation(l.getLine(), l.getColumn(), l.getSourceName()))
            .collect(Collectors.toList());
    }

    @Override
    public ErrorClassification getErrorType() {
        return this.errorType;
    }

    public void setMessage(final String message) {
        this.message = message;
    }

    public void setLocations(final List<LiloSourceLocation> locations) {
        this.locations = locations;
    }

    public void setErrorType(final ErrorType errorType) {
        this.errorType = errorType;
    }
}
