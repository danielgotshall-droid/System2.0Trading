package study_examples.lzs.context;

public final class LzsReferenceZoneProfile {
  public enum Family { VALUE, WINDOW, EXTREME }

  public final Family family;
  public final int zoneHalfWidthTicks;
  public final int acceptanceDistanceTicks;
  public final long acceptanceHoldMs;

  public LzsReferenceZoneProfile(Family family, int zoneHalfWidthTicks, int acceptanceDistanceTicks, long acceptanceHoldMs) {
    this.family = family == null ? Family.WINDOW : family;
    this.zoneHalfWidthTicks = Math.max(1, zoneHalfWidthTicks);
    this.acceptanceDistanceTicks = Math.max(this.zoneHalfWidthTicks + 1, acceptanceDistanceTicks);
    this.acceptanceHoldMs = Math.max(1000L, acceptanceHoldMs);
  }

  public static LzsReferenceZoneProfile forLabel(String label, LzsContextConfig cfg) {
    if (isValue(label)) {
      return new LzsReferenceZoneProfile(Family.VALUE, cfg.valueZoneHalfWidthTicks, cfg.valueAcceptanceDistanceTicks, cfg.valueAcceptanceHoldMs);
    }
    if (isWindow(label)) {
      return new LzsReferenceZoneProfile(Family.WINDOW, cfg.windowZoneHalfWidthTicks, cfg.windowAcceptanceDistanceTicks, cfg.windowAcceptanceHoldMs);
    }
    return new LzsReferenceZoneProfile(Family.EXTREME, cfg.extremeZoneHalfWidthTicks, cfg.extremeAcceptanceDistanceTicks, cfg.extremeAcceptanceHoldMs);
  }

  private static boolean isValue(String label) {
    return "VWAP".equals(label) || "POC".equals(label) || "OPEN".equals(label);
  }

  private static boolean isWindow(String label) {
    return "ORH".equals(label) || "ORL".equals(label) || "IBH".equals(label) || "IBL".equals(label) || "VAH".equals(label) || "VAL".equals(label);
  }
}
