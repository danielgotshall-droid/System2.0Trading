package study_examples.lzs.context;

public enum LzsContextIntent {
  CONTINUATION("CONT"),
  FADE("FADE"),
  NEUTRAL("NEUT");

  private final String shortLabel;

  LzsContextIntent(String shortLabel) {
    this.shortLabel = shortLabel;
  }

  public String shortLabel() {
    return shortLabel;
  }
}
