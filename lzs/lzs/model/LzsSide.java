package study_examples.lzs.model;

public enum LzsSide {
  LONG,
  SHORT;

  public boolean isLong() {
    return this == LONG;
  }

  public boolean isShort() {
    return this == SHORT;
  }
}
