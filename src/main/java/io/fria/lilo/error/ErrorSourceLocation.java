package io.fria.lilo.error;

import graphql.language.SourceLocation;
import org.jetbrains.annotations.Nullable;

public class ErrorSourceLocation extends SourceLocation {

  public ErrorSourceLocation() {
    super(0, 0);
  }

  public ErrorSourceLocation(final int line, final int column) {
    super(line, column);
  }

  public ErrorSourceLocation(final int line, final int column, final @Nullable String sourceName) {
    super(line, column, sourceName);
  }

  @Override
  public boolean equals(final Object o) {
    return super.equals(o);
  }

  @Override
  public int getColumn() {
    return super.getColumn();
  }

  @Override
  public int getLine() {
    return super.getLine();
  }

  @Override
  public @Nullable String getSourceName() {
    return super.getSourceName();
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }

  @Override
  public String toString() {
    return super.toString();
  }
}
