package study_examples.lzs.context;

public enum LzsContextMode {
  ANNOTATION_ONLY(0, "ANN"),
  SCORE_ONLY(1, "SCR"),
  SOFT_BIAS(2, "BIAS"),
  OPTIONAL_FILTER(3, "FILT");

  private final int code;
  private final String shortLabel;

  LzsContextMode(int code, String shortLabel) {
    this.code = code;
    this.shortLabel = shortLabel;
  }

  public int code() { return code; }
  public String shortLabel() { return shortLabel; }

  public static LzsContextMode fromCode(int code) {
    for (LzsContextMode v : values()) if (v.code == code) return v;
    return ANNOTATION_ONLY;
  }
}
