package study_examples.lzs.model;

public final class LzsConfig {
  public boolean enableLong = true;
  public boolean enableShort = true;

  public int evalMinIntervalMs = 100;
  public int domLevelsToCapture = 20;
  public int domWindowMaxUpdates = 50;
  public boolean useRthTicks = true;
  public boolean useHistBars = false;

  public double zoneMinSize = 100.0;
  public double zoneMinRowPctOfAnchor = 0.15;
  public int zoneMinContiguousRows = 1;
  public int zoneMaxGapRows = 2;
  public int zoneMaxHeightTicks = 20;
  public int zoneMaxDistanceTicks = 12;

  public int armProximityTicks = 2;
  public int executionProximityTicks = 2;
  public int minBubbleSize = 30;
  public int minBubbleCount = 1;
  public double minAggressionShare = 0.10;
  public int maxUpdatesFromArmToExec = 20;
  public int maxUpdatesToReverse = 25;
  public int minReversalTicks = 2;

  public boolean requireReload = false;
  public double minReloadPct = 0.0;
  public double minRemainingZonePct = 0.10;
  public boolean rejectIfConsumed = false;
  public int minOpenPathTicks = 0;
  public double maxOpposingBlockInPath = 1000.0;
  public int attractionPenaltyLookaheadTicks = 0;

  public int minBarsBetweenSameSideSignals = 0;
  public int minMsBetweenSameZoneSignals = 1500;

  public int hudRefreshIntervalMs = 250;

  public boolean openModeThrottleEnabled = true;
  public int openModeMinutes = 5;
  public int openModeEvalIntervalMs = 150;

  public int execRefreshMinIntervalMs = 75;

  public boolean showHud = true;
  public boolean showPhaseDetails = true;
  public boolean showCandidateMetrics = true;
  public int hudOffsetTicks = 12;

  public static LzsConfig defaults() {
    return new LzsConfig();
  }
}
