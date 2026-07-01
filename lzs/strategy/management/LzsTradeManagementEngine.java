package study_examples.lzs.strategy.management;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import study_examples.lzs.model.LzsSide;

/**
 * Position-aware research metrics management foundation.
 *
 * <p>This first pass computes health/degradation/failure scores only. It does
 * not place management-driven orders yet; that comes in later strategy steps.</p>
 */
public class LzsTradeManagementEngine {
  private final LzsTradeManagementConfig config;
  private final LzsTradeManagementWindow primaryWindow;
  private final LzsTradeManagementWindow confirmWindow;

  private LzsTradeManagementMetrics lastMetrics = new LzsTradeManagementMetrics();
  private LzsTradeManagementScores lastScores = LzsTradeManagementScores.inactive();

  public LzsTradeManagementEngine() {
    this(new LzsTradeManagementConfig());
  }

  public LzsTradeManagementEngine(LzsTradeManagementConfig config) {
    this.config = config == null ? new LzsTradeManagementConfig() : config;
    this.primaryWindow = new LzsTradeManagementWindow(this.config.primaryWindowMs);
    this.confirmWindow = new LzsTradeManagementWindow(this.config.confirmWindowMs);
  }

  public void reset() {
    primaryWindow.clear();
    confirmWindow.clear();
    lastMetrics = new LzsTradeManagementMetrics();
    lastScores = LzsTradeManagementScores.inactive();
  }

  public LzsTradeManagementMetrics getLastMetrics() {
    return lastMetrics;
  }

  public LzsTradeManagementScores getLastScores() {
    return lastScores;
  }

  public LzsTradeManagementScores update(LzsTradeManagementSnapshot snapshot) {
    if (!config.enabled || snapshot == null || snapshot.side == null || Double.isNaN(snapshot.close)) {
      reset();
      return lastScores;
    }

    primaryWindow.add(snapshot);
    confirmWindow.add(snapshot);

    lastMetrics = computeMetrics(snapshot);
    lastScores = computeScores(lastMetrics, snapshot);
    return lastScores;
  }

  private LzsTradeManagementMetrics computeMetrics(LzsTradeManagementSnapshot s) {
    LzsTradeManagementMetrics m = new LzsTradeManagementMetrics();
    double deltaDenom = Math.max(1.0, primaryWindow.sumAbsDelta() / Math.max(1.0, config.deltaScale));
    double primaryDisp = primaryWindow.signedDisplacementTicks(s.side, s.tickSize);
    double confirmDisp = confirmWindow.signedDisplacementTicks(s.side, s.tickSize);

    m.aer = primaryDisp / deltaDenom;

    double confirmAer = confirmDisp / Math.max(1.0, confirmWindow.sumAbsDelta() / Math.max(1.0, config.deltaScale));
    m.ic = safeRatio(Math.abs(m.aer), Math.max(config.minBaseline, Math.abs(confirmAer)));

    double reference = Double.isNaN(s.referencePrice) ? s.entryPrice : s.referencePrice;
    m.bai = clamp01(primaryWindow.closesBeyondReferenceRatio(s.side, reference));

    double favorableNowTicks = signedTicksFromReference(s.side, s.lastPrice, reference, s.tickSize);
    double mfeTicks = Math.max(0.0, nz(s.mfeTicks));
    m.drr = mfeTicks <= 0.0 ? 0.0 : clamp01(Math.max(0.0, favorableNowTicks) / Math.max(0.25, mfeTicks));

    double adverseTicks = Math.max(0.0, -signedTicksFromReference(s.side, s.lastPrice, reference, s.tickSize));
    double opposingDelta = primaryWindow.opposingDeltaMagnitude(s.side);
    double absDelta = Math.max(1.0, primaryWindow.sumAbsDelta());
    m.oer = adverseTicks * (opposingDelta / absDelta);

    boolean structureBroken = adverseTicks >= config.structureBreakTicks;
    boolean opposingEffective = opposingDelta > primaryWindow.favorableDeltaMagnitude(s.side);
    m.dfsf = structureBroken && opposingEffective ? 1.0 : 0.0;

    double effort = safeRatio(primaryWindow.sumAbsDelta(), Math.max(1.0, confirmWindow.sumAbsDelta()));
    double result = safeRatio(Math.abs(primaryDisp), Math.max(config.minDisplacementTicks, Math.abs(confirmDisp)));
    m.erd = Math.max(0.0, effort - result);
    return m;
  }

  private LzsTradeManagementScores computeScores(LzsTradeManagementMetrics m, LzsTradeManagementSnapshot s) {
    LzsTradeManagementScores out = new LzsTradeManagementScores();

    double aerGood = clamp01(m.aer / config.goodAer);
    double icGood = clamp01(m.ic / config.goodIc);
    double baiGood = clamp01(m.bai);
    double drrGood = clamp01(m.drr);

    double oerBad = clamp01(m.oer / config.goodOer);
    double erdBad = clamp01(m.erd / config.goodErd);
    double baiBad = 1.0 - baiGood;
    double drrBad = 1.0 - drrGood;
    double icBad = 1.0 - icGood;

    out.css = 100.0 * clamp01(0.35 * aerGood + 0.20 * icGood + 0.25 * baiGood + 0.20 * drrGood);
    out.exs = 100.0 * clamp01(0.45 * erdBad + 0.30 * icBad + 0.25 * drrBad);
    out.ers = 100.0 * clamp01(0.45 * oerBad + 0.35 * clamp01(m.dfsf) + 0.20 * baiBad);

    if (out.ers >= config.forceExitThreshold || m.dfsf >= 1.0) out.state = LzsTradeManagementState.MGMT_FORCE_EXIT;
    else if (out.ers >= config.exitRiskThreshold) out.state = LzsTradeManagementState.MGMT_EXIT_RISK;
    else if (out.exs >= config.protectThreshold || m.ic < config.protectIcThreshold || m.drr < config.protectDrrThreshold) out.state = LzsTradeManagementState.MGMT_PROTECT;
    else out.state = LzsTradeManagementState.MGMT_HOLD;

    List<String> reasons = new ArrayList<>();
    if (m.aer >= config.goodAer * 0.75) reasons.add("AER+");
    if (m.bai >= 0.60) reasons.add("BAIok");
    if (m.drr >= 0.50) reasons.add("DRRok");
    if (m.ic < config.protectIcThreshold) reasons.add("ICdn");
    if (m.erd >= 0.75 * config.goodErd) reasons.add("ERDrise");
    if (m.oer >= 0.75 * config.goodOer) reasons.add("OERhi");
    if (m.dfsf >= 1.0) reasons.add("DFSF");
    if (reasons.isEmpty()) reasons.add(String.format(Locale.US, "AER %.2f", m.aer));
    out.reasons = join(reasons);
    return out;
  }

  private double signedTicksFromReference(LzsSide side, double price, double reference, double tickSize) {
    double ts = Math.max(0.0000001, tickSize);
    if (Double.isNaN(price) || Double.isNaN(reference)) return 0.0;
    if (side == LzsSide.LONG) return (price - reference) / ts;
    return (reference - price) / ts;
  }

  private String join(List<String> reasons) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < reasons.size(); i++) {
      if (i > 0) sb.append('/');
      sb.append(reasons.get(i));
    }
    return sb.toString();
  }

  private double safeRatio(double num, double den) {
    return den == 0.0 ? 0.0 : num / den;
  }

  private double clamp01(double v) {
    if (Double.isNaN(v)) return 0.0;
    if (v < 0.0) return 0.0;
    if (v > 1.0) return 1.0;
    return v;
  }

  private double nz(double v) {
    return Double.isNaN(v) ? 0.0 : v;
  }
}
