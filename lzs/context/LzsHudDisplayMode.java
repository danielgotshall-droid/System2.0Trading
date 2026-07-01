package study_examples.lzs.context;

public enum LzsHudDisplayMode {
  COMPACT(0),
  STANDARD(1),
  DEBUG(2);

  private final int code;

  LzsHudDisplayMode(int code) {
    this.code = code;
  }

  public int code() { return code; }

  public static LzsHudDisplayMode fromCode(int code) {
    for (LzsHudDisplayMode v : values()) if (v.code == code) return v;
    return STANDARD;
  }
}
