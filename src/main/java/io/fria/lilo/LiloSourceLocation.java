package io.fria.lilo;

import graphql.language.SourceLocation;

public class LiloSourceLocation extends SourceLocation {

  public LiloSourceLocation() {
    super(0, 0);
  }

  public LiloSourceLocation(final int line, final int column) {
    super(line, column);
  }

  public LiloSourceLocation(final int line, final int column, final String sourceName) {
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
  public String getSourceName() {
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
