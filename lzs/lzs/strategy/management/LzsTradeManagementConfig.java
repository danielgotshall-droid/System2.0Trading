package study_examples.lzs.strategy.management;

/**
 * Phase 5A Step 3 management foundation configuration.
 *
 * <p>The first pass is intentionally conservative. It computes management
 * health/degeneration/failure scores, but it does not automatically send
 * management-driven orders yet.</p>
 */
public class LzsTradeManagementConfig {
  public boolean enabled = true;
  public long primaryWindowMs = 30_000L;
  public long confirmWindowMs = 60_000L;

  /** Scaling constant used to convert delta into a more stable denominator. */
  public double deltaScale = 250.0;

  /** Avoid divide-by-zero and pathological tiny baselines. */
  public double minBaseline = 0.05;
  public double minDisplacementTicks = 0.25;

  /** Structure failure heuristic used by DFSF. */
  public double structureBreakTicks = 2.0;

  /** Score normalization anchors. */
  public double goodAer = 0.80;
  public double goodIc = 1.00;
  public double goodOer = 2.00;
  public double goodErd = 1.00;

  /** State thresholds. */
  public double protectThreshold = 55.0;
  public double exitRiskThreshold = 65.0;
  public double forceExitThreshold = 80.0;
  public double protectIcThreshold = 0.70;
  public double protectDrrThreshold = 0.40;

  /** Phase 5B action policy controls. */
  public boolean actionsEnabled = true;
  public boolean protectActionEnabled = true;
  public double protectArmMfeTicks = 6.0;
  public long protectPersistenceMs = 1500L;
  public double protectBreakevenCushionTicks = 1.0;
  public boolean structuralProtectEnabled = true;
  public long structuralProtectLookbackMs = 4000L;
  public double structuralProtectBufferTicks = 1.0;
  public double structuralProtectMinDrr = 0.50;
  public boolean structuralProtectRequireBaiOk = true;
  public boolean exitRiskUseStructuralRef = true;

  public boolean exitRiskActionEnabled = true;
  /** 0 = tighten active stop, 1 = exit at market. */
  public int exitRiskActionMode = 0;
  public long exitRiskPersistenceMs = 1000L;
  public double exitRiskTightenBufferTicks = 3.0;

  public boolean forceExitActionEnabled = true;
}
