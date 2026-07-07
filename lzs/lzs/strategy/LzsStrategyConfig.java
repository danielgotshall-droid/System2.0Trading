package study_examples.lzs.strategy;

import study_examples.lzs.strategy.management.LzsTradeManagementConfig;

/**
 * Strategy-owned configuration for the LZS strategy scaffold.
 */
public class LzsStrategyConfig {
  public boolean enabled = true;
  public boolean enableLong = true;
  public boolean enableShort = true;

  /** Require the study-side context filter to pass before entry is eligible. */
  public boolean requireContextPass = true;

  /** Minimum merged context score for a long/short candidate. */
  public double minLongContextScore = 0.8;
  public double minShortContextScore = 0.8;

  /** The earliest study phase that may be treated as entry-eligible. */
  public int minEligiblePhaseOrdinal = 7; // SIGNAL_READY

  /** Initial bracket planning. More adaptive logic comes in later steps. */
  public double initialStopTicks = 8.0;
  public double initialTargetTicks = 12.0;

  /** Require some path room on the study surface before allowing entry. */
  public boolean requirePathClear = true;
  public double minPathClearTicks = 2.0;

  /** Phase 5A Step 3 management foundation toggle. */
  public boolean managementEnabled = true;
  public final LzsTradeManagementConfig management = new LzsTradeManagementConfig();

  /** Phase 5B management actions. */
  public boolean managementActionsEnabled = true;
  public boolean protectActionEnabled = true;
  public double protectArmMfeTicks = 6.0;
  public long protectPersistenceMs = 1500L;
  public double protectBreakevenCushionTicks = 1.0;
  public boolean structuralProtectEnabled = true;
  public long structuralProtectLookbackMs = 4000L;
  public double structuralProtectBufferTicks = 1.0;
  public double structuralProtectMinDrr = 0.50;
  public boolean structuralProtectRequireBaiOk = true;
  public boolean exitRiskActionEnabled = true;
  /** 0 = Tighten Only, 1 = Exit Market. */
  public int exitRiskActionMode = 0;
  public long exitRiskPersistenceMs = 1000L;
  public double exitRiskTightenBufferTicks = 3.0;
  public boolean exitRiskUseStructuralRef = true;
  public boolean forceExitActionEnabled = true;

  /** Minimum hold-time gate to help avoid sub-threshold automated trade durations. */
  public boolean minimumHoldEnabled = false;
  public double minimumHoldSeconds = 0.0;
  public boolean allowForceExitDuringMinimumHold = true;
  public boolean allowInitialStopDuringMinimumHold = true;

  /** Show the custom chart-side strategy HUD. */
  public boolean showStrategyHud = true;
  public int strategyHudOffsetTicks = 28;

  /** Use the far side of the fired signal zone plus this buffer for the initial stop. */
  public boolean useZoneBasedInitialStop = true;
  public double zoneStopBufferTicks = 8.0;
}
