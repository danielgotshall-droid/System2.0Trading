package study_examples.lzs.session;

import com.motivewave.platform.sdk.common.DataContext;
import com.motivewave.platform.sdk.common.DataSeries;
import com.motivewave.platform.sdk.common.Instrument;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Phase 3 reusable helper for classifying the developing post-IB day structure.
 *
 * Phase 3 upgrades over Phase 2:
 * - stronger liquidation / inventory-correction filter
 * - better structural scoring for trend vs rotational auction quality
 * - optional gap / volume context folded into the score when available
 * - richer reasons/debug output
 *
 * Design goals:
 * - shared helper usable by both studies and strategies
 * - deterministic, explainable outputs
 * - no dependency on another study instance being present on the chart
 */
public final class SessionDayTypeHelper {

  private static final ZoneId ET_ZONE = ZoneId.of("America/New_York");
  private static final DateTimeFormatter ET_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ET_ZONE);
  private static final double MIN_STRUCTURAL_VWAP_DRIFT_TICKS = 6.0;

  private SessionDayTypeHelper() {}

  public enum DayTypeState {
    UNKNOWN,
    TRANSITION,
    RANGE_BALANCED,
    RANGE_VOLATILE,
    TREND_UP,
    TREND_DOWN,
    LIQUIDATION_THEN_BALANCE
  }

  public static final class DayTypeConfig {
    public final int ibMinutes;
    public final int recentLookbackSessions;
    public final double smallIbThreshold;
    public final double largeIbThreshold;
    public final int acceptanceHoldBars;
    public final int extensionMinTicks;
    public final int reentryToleranceTicks;
    public final int rotationThreshold;
    public final double liquidationImpulseAtrMultiple;
    public final double valueMigrationMinTicks;
    public final boolean useGapContext;
    public final boolean useVolumeContext;
    public final double meaningfulGapAtrMultiple;
    public final double strongIbVolumeThreshold;
    public final boolean useManualAid;
    public final boolean preferManualAid;
    public final double manualRecentMedianIbRange;
    public final double manualRecentMedianIbVolume;
    public final double manualAtrLikeRange;
    public final double manualPriorSessionClose;

    public DayTypeConfig(
        int ibMinutes,
        int recentLookbackSessions,
        double smallIbThreshold,
        double largeIbThreshold,
        int acceptanceHoldBars,
        int extensionMinTicks,
        int reentryToleranceTicks,
        int rotationThreshold,
        double liquidationImpulseAtrMultiple,
        double valueMigrationMinTicks
    ) {
      this(
          ibMinutes,
          recentLookbackSessions,
          smallIbThreshold,
          largeIbThreshold,
          acceptanceHoldBars,
          extensionMinTicks,
          reentryToleranceTicks,
          rotationThreshold,
          liquidationImpulseAtrMultiple,
          valueMigrationMinTicks,
          false,
          false,
          Double.NaN,
          Double.NaN,
          Double.NaN,
          Double.NaN
      );
    }

    public DayTypeConfig(
        int ibMinutes,
        int recentLookbackSessions,
        double smallIbThreshold,
        double largeIbThreshold,
        int acceptanceHoldBars,
        int extensionMinTicks,
        int reentryToleranceTicks,
        int rotationThreshold,
        double liquidationImpulseAtrMultiple,
        double valueMigrationMinTicks,
        boolean useManualAid,
        boolean preferManualAid,
        double manualRecentMedianIbRange,
        double manualRecentMedianIbVolume,
        double manualAtrLikeRange,
        double manualPriorSessionClose
    ) {
      this.ibMinutes = ibMinutes;
      this.recentLookbackSessions = recentLookbackSessions;
      this.smallIbThreshold = smallIbThreshold;
      this.largeIbThreshold = largeIbThreshold;
      this.acceptanceHoldBars = acceptanceHoldBars;
      this.extensionMinTicks = extensionMinTicks;
      this.reentryToleranceTicks = reentryToleranceTicks;
      this.rotationThreshold = rotationThreshold;
      this.liquidationImpulseAtrMultiple = liquidationImpulseAtrMultiple;
      this.valueMigrationMinTicks = valueMigrationMinTicks;
      this.useGapContext = true;
      this.useVolumeContext = true;
      this.meaningfulGapAtrMultiple = 0.35;
      this.strongIbVolumeThreshold = 1.15;
      this.useManualAid = useManualAid;
      this.preferManualAid = preferManualAid;
      this.manualRecentMedianIbRange = manualRecentMedianIbRange;
      this.manualRecentMedianIbVolume = manualRecentMedianIbVolume;
      this.manualAtrLikeRange = manualAtrLikeRange;
      this.manualPriorSessionClose = manualPriorSessionClose;
    }

    public static DayTypeConfig defaults() {
      return new DayTypeConfig(
          60,
          20,
          0.80,
          1.20,
          3,
          4,
          2,
          3,
          0.60,
          2.0
      );
    }
  }

  public static final class InitialBalanceContext {
    public int index;
    public int sessionStartIndex = -1;
    public int ibEndIndex = -1;
    public boolean ibComplete;

    public long sessionStartTime = Long.MIN_VALUE;
    public long ibEndTime = Long.MIN_VALUE;
    public long currentBarTime = Long.MIN_VALUE;
    public int ibBarCount;

    public double tickSize = Double.NaN;
    public double open = Double.NaN;
    public double ibOpen = Double.NaN;
    public double ibHigh = Double.NaN;
    public double ibLow = Double.NaN;
    public double ibRange = Double.NaN;
    public double ibMid = Double.NaN;
    public double ibClose = Double.NaN;
    public double ibCloseLocation = Double.NaN; // 0..1 inside IB
    public double ibNetChangeTicks = Double.NaN;
    public double ibBodyPct = Double.NaN;

    public double lastPrice = Double.NaN;
    public double sessionHigh = Double.NaN;
    public double sessionLow = Double.NaN;

    public double firstExtensionUpTicks = 0.0;
    public double firstExtensionDownTicks = 0.0;
    public boolean acceptedAboveIb;
    public boolean acceptedBelowIb;
    public int barsHeldAboveIb;
    public int barsHeldBelowIb;
    public int closesAboveIb;
    public int closesBelowIb;
    public boolean reenteredIb;

    public int dominantDirection; // +1 up, -1 down, 0 none
    public double netMoveTicks = Double.NaN;
    public double directionalEfficiency = Double.NaN;
    public double pullbackFromExtremeTicks = Double.NaN;

    public double ibVwap = Double.NaN;
    public double postIbVwap = Double.NaN;
    public double sessionVwap = Double.NaN;
    public double valueMigrationTicks = Double.NaN;

    public double overlapRatio = Double.NaN;
    public int rotationCount;
    public int midCrossCount;
    public int failedBreakCount;
    public int edgeReversalCount;

    public double recentMedianIbRange = Double.NaN;
    public double normalizedIbSize = Double.NaN;
    public double atrLikeRange = Double.NaN;
    public double ibVolume = Double.NaN;
    public double recentMedianIbVolume = Double.NaN;
    public double normalizedIbVolume = Double.NaN;
    public double priorSessionClose = Double.NaN;
    public double sessionGapTicks = Double.NaN;
    public double sessionGapAtr = Double.NaN;
    public boolean gapUp;
    public boolean gapDown;

    public double impulseDistanceTicks = Double.NaN;
    public double impulseSpeedBars = Double.NaN;
    public boolean liquidationWarning;
    public int lookbackSessionsUsed;
    public boolean manualAidUsed;

    public final List<String> debugNotes = new ArrayList<String>();
  }

  public static final class DayTypeFeatures {
    public double smallIbScore;
    public double largeIbScore;

    public double directionalIbScore;
    public double rotationalIbScore;

    public double extensionUpScore;
    public double extensionDownScore;

    public double acceptanceUpScore;
    public double acceptanceDownScore;
    public double holdUpScore;
    public double holdDownScore;
    public double reentryPenalty;

    public double valueMigrationUpScore;
    public double valueMigrationDownScore;
    public double staticValueScore;

    public double efficiencyTrendScore;
    public double inefficientAuctionScore;
    public double trendStructureScore;
    public double rangeStructureScore;
    public double pullbackTrendQuality;
    public double trendContinuationQuality;
    public double balanceReversionScore;
    public double gapUpContextScore;
    public double gapDownContextScore;
    public double volumeTrendSupportScore;
    public double volumeBalanceSupportScore;

    public double liquidationScore;

    public double trendUpScore;
    public double trendDownScore;
    public double rangeScore;
  }

  public static final class DayTypeResult {
    public DayTypeState state = DayTypeState.UNKNOWN;

    public boolean ibComplete;

    public double trendUpScore;
    public double trendDownScore;
    public double rangeScore;
    public double liquidationScore;
    public double confidence;

    public String summary = "";
    public String debugText = "";
    public final List<String> reasons = new ArrayList<String>();

    public String buildReasonSummary() {
      if (reasons.isEmpty()) return "";
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < reasons.size(); i++) {
        if (i > 0) sb.append(" | ");
        sb.append(reasons.get(i));
      }
      return sb.toString();
    }

    public String buildDisplayText() {
      String rs = buildReasonSummary();
      return rs.length() == 0 ? summary : summary + "\n" + rs;
    }
  }

  public static InitialBalanceContext buildContext(int index, DataContext ctx, DayTypeConfig cfg) {
    InitialBalanceContext out = new InitialBalanceContext();
    out.index = index;

    if (ctx == null) return out;
    DataSeries s = ctx.getDataSeries();
    if (s == null || index < 0 || index >= s.size()) return out;

    Instrument instr = ctx.getInstrument();
    if (instr == null) return out;

    out.tickSize = safeTickSize(instr);
    out.lastPrice = s.getClose(index);
    out.currentBarTime = s.getStartTime(index);
    out.sessionStartTime = instr.getStartOfDay(s.getStartTime(index), true);
    out.ibEndTime = out.sessionStartTime + cfg.ibMinutes * 60_000L;
    out.sessionStartIndex = findSessionStartIndex(s, index, out.sessionStartTime);
    out.ibEndIndex = findIbEndIndex(s, out.sessionStartIndex, index, out.ibEndTime);
    out.ibComplete = out.ibEndIndex >= out.sessionStartIndex && index > out.ibEndIndex;
    out.ibBarCount = (out.sessionStartIndex >= 0 && out.ibEndIndex >= out.sessionStartIndex) ? (out.ibEndIndex - out.sessionStartIndex + 1) : 0;

    if (out.sessionStartIndex < 0) {
      out.debugNotes.add("No session start index found.");
      return out;
    }

    populateIbWindowStats(out, s, cfg);
    populateSessionStats(out, s);
    populateRecentIbStats(out, s, instr, cfg);

    if (out.ibComplete) {
      populatePostIbStats(out, s, cfg);
      populateRegimeProxies(out, s, cfg);
    }
    else {
      out.debugNotes.add("IB window not complete yet.");
    }

    return out;
  }

  public static DayTypeResult evaluate(InitialBalanceContext c, DayTypeConfig cfg) {
    DayTypeResult out = new DayTypeResult();
    out.ibComplete = c.ibComplete;

    if (!c.ibComplete || Double.isNaN(c.ibRange) || c.ibRange <= 0.0) {
      out.state = DayTypeState.UNKNOWN;
      out.summary = "IB not complete";
      out.debugText = c.debugNotes.isEmpty() ? "Initial Balance window is not complete yet." : join(c.debugNotes, " | ");
      return out;
    }

    DayTypeFeatures f = extractFeatures(c, cfg);
    scoreFeatures(f);
    classify(c, f, out);
    buildReasons(c, f, out);
    buildDebug(c, f, out, cfg);
    return out;
  }

  private static DayTypeFeatures extractFeatures(InitialBalanceContext c, DayTypeConfig cfg) {
    DayTypeFeatures f = new DayTypeFeatures();

    if (!Double.isNaN(c.normalizedIbSize)) {
      double rel = c.normalizedIbSize;
      f.smallIbScore = clamp01((cfg.smallIbThreshold - rel + 1.0) * 0.5);
      f.largeIbScore = clamp01((rel - cfg.largeIbThreshold + 1.0) * 0.5);
    }

    if (!Double.isNaN(c.ibCloseLocation)) {
      double closeLocDirectional = Math.abs(c.ibCloseLocation - 0.5) * 2.0;
      double bodyDirectional = safe01(c.ibBodyPct);
      double netDir = normalizeTicks(Math.abs(c.ibNetChangeTicks), Math.max(2.0, cfg.extensionMinTicks));
      f.directionalIbScore = clamp01(0.50 * closeLocDirectional + 0.30 * bodyDirectional + 0.20 * netDir);
      f.rotationalIbScore = clamp01(1.0 - f.directionalIbScore);
    }

    f.extensionUpScore = normalizeTicks(c.firstExtensionUpTicks, cfg.extensionMinTicks);
    f.extensionDownScore = normalizeTicks(c.firstExtensionDownTicks, cfg.extensionMinTicks);

    f.acceptanceUpScore = c.acceptedAboveIb
        ? clamp01(c.barsHeldAboveIb / (double) Math.max(1, cfg.acceptanceHoldBars)) : 0.0;
    f.acceptanceDownScore = c.acceptedBelowIb
        ? clamp01(c.barsHeldBelowIb / (double) Math.max(1, cfg.acceptanceHoldBars)) : 0.0;

    double postBars = Math.max(1.0, c.index - c.ibEndIndex);
    f.holdUpScore = clamp01(c.closesAboveIb / postBars);
    f.holdDownScore = clamp01(c.closesBelowIb / postBars);
    f.reentryPenalty = c.reenteredIb ? 1.0 : 0.0;

    double vMig = Double.isNaN(c.valueMigrationTicks) ? 0.0 : c.valueMigrationTicks;
    double effectiveValueMigrationMinTicks = Math.max(cfg.valueMigrationMinTicks, MIN_STRUCTURAL_VWAP_DRIFT_TICKS);
    f.valueMigrationUpScore = normalizeTicks(Math.max(0.0, vMig), effectiveValueMigrationMinTicks);
    f.valueMigrationDownScore = normalizeTicks(Math.max(0.0, -vMig), effectiveValueMigrationMinTicks);
    f.staticValueScore = clamp01(1.0 - (Math.abs(vMig) / Math.max(1.0, effectiveValueMigrationMinTicks * 2.0)));

    f.efficiencyTrendScore = safe01(c.directionalEfficiency);
    f.inefficientAuctionScore = clamp01(1.0 - safe01(c.directionalEfficiency));

    double overlap = safe01(c.overlapRatio);
    double rotationScaled = Math.min(1.0, c.rotationCount / (double) Math.max(1, cfg.rotationThreshold));
    double failedBreakScaled = Math.min(1.0, c.failedBreakCount / 3.0);
    double edgeRevScaled = Math.min(1.0, c.edgeReversalCount / 3.0);
    f.trendStructureScore = clamp01(
        0.30 * (1.0 - overlap) +
        0.25 * f.efficiencyTrendScore +
        0.20 * Math.max(f.holdUpScore, f.holdDownScore) +
        0.15 * Math.max(f.acceptanceUpScore, f.acceptanceDownScore) +
        0.10 * (1.0 - failedBreakScaled));
    f.rangeStructureScore = clamp01(
        0.28 * overlap +
        0.24 * rotationScaled +
        0.18 * failedBreakScaled +
        0.15 * edgeRevScaled +
        0.15 * f.inefficientAuctionScore);

    double domExt = Math.max(c.firstExtensionUpTicks, c.firstExtensionDownTicks);
    f.pullbackTrendQuality = (Double.isNaN(c.pullbackFromExtremeTicks) || domExt <= 0.0)
        ? 0.0 : clamp01(1.0 - (c.pullbackFromExtremeTicks / Math.max(1.0, domExt)));
    f.trendContinuationQuality = clamp01(
        0.35 * Math.max(f.extensionUpScore, f.extensionDownScore) +
        0.30 * Math.max(f.acceptanceUpScore, f.acceptanceDownScore) +
        0.20 * f.pullbackTrendQuality +
        0.15 * f.efficiencyTrendScore);
    f.balanceReversionScore = clamp01(
        0.35 * f.reentryPenalty +
        0.30 * rotationScaled +
        0.20 * failedBreakScaled +
        0.15 * edgeRevScaled);

    if (cfg.useGapContext && !Double.isNaN(c.sessionGapAtr)) {
      double gapMag = Math.abs(c.sessionGapAtr);
      double gapScore = clamp01(gapMag / Math.max(0.01, cfg.meaningfulGapAtrMultiple));
      boolean gapSupportsUp = c.gapUp && c.valueMigrationTicks > 0.0 && c.firstExtensionUpTicks >= c.firstExtensionDownTicks;
      boolean gapSupportsDown = c.gapDown && c.valueMigrationTicks < 0.0 && c.firstExtensionDownTicks >= c.firstExtensionUpTicks;
      f.gapUpContextScore = gapSupportsUp ? gapScore : 0.0;
      f.gapDownContextScore = gapSupportsDown ? gapScore : 0.0;
    }

    if (cfg.useVolumeContext && !Double.isNaN(c.normalizedIbVolume)) {
      double volRel = c.normalizedIbVolume;
      double volTrend = clamp01((volRel - cfg.strongIbVolumeThreshold + 1.0) * 0.5);
      f.volumeTrendSupportScore = volTrend;
      f.volumeBalanceSupportScore = clamp01(1.0 - volTrend);
    }

    double liqBase = c.liquidationWarning ? 0.70 : 0.0;
    double liqAdd = clamp01(0.50 * f.balanceReversionScore + 0.30 * f.inefficientAuctionScore + 0.20 * f.reentryPenalty);
    f.liquidationScore = clamp01(liqBase + 0.45 * liqAdd);
    return f;
  }

  private static void scoreFeatures(DayTypeFeatures f) {
    f.trendUpScore =
        14 * f.smallIbScore +
         9 * f.directionalIbScore +
        13 * f.extensionUpScore +
        12 * f.acceptanceUpScore +
         8 * f.holdUpScore +
        10 * f.valueMigrationUpScore +
         8 * f.efficiencyTrendScore +
         8 * f.trendStructureScore +
         8 * f.trendContinuationQuality +
         5 * f.pullbackTrendQuality +
         5 * f.gapUpContextScore +
         4 * f.volumeTrendSupportScore -
         7 * f.reentryPenalty -
         5 * f.liquidationScore;

    f.trendDownScore =
        14 * f.smallIbScore +
         9 * f.directionalIbScore +
        13 * f.extensionDownScore +
        12 * f.acceptanceDownScore +
         8 * f.holdDownScore +
        10 * f.valueMigrationDownScore +
         8 * f.efficiencyTrendScore +
         8 * f.trendStructureScore +
         8 * f.trendContinuationQuality +
         5 * f.pullbackTrendQuality +
         5 * f.gapDownContextScore +
         4 * f.volumeTrendSupportScore -
         7 * f.reentryPenalty -
         5 * f.liquidationScore;

    f.rangeScore =
        16 * f.largeIbScore +
         9 * f.rotationalIbScore +
        16 * f.rangeStructureScore +
        10 * f.staticValueScore +
        10 * f.inefficientAuctionScore +
         8 * f.reentryPenalty +
         8 * f.balanceReversionScore +
         5 * f.volumeBalanceSupportScore +
        10 * f.liquidationScore;
  }

  private static void classify(InitialBalanceContext c, DayTypeFeatures f, DayTypeResult out) {
    out.trendUpScore = f.trendUpScore;
    out.trendDownScore = f.trendDownScore;
    out.rangeScore = f.rangeScore;
    out.liquidationScore = f.liquidationScore * 100.0;

    double best = Math.max(out.rangeScore, Math.max(out.trendUpScore, out.trendDownScore));
    double second = secondBest(out.trendUpScore, out.trendDownScore, out.rangeScore);
    out.confidence = Math.max(0.0, best - second);

    boolean liquidationOverrides = f.liquidationScore >= 0.75 && out.rangeScore >= Math.max(out.trendUpScore, out.trendDownScore) * 0.75;
    if (liquidationOverrides) {
      out.state = DayTypeState.LIQUIDATION_THEN_BALANCE;
    }
    else if (out.trendUpScore > out.rangeScore && out.trendUpScore > out.trendDownScore) {
      out.state = out.confidence < 7.5 ? DayTypeState.TRANSITION : DayTypeState.TREND_UP;
    }
    else if (out.trendDownScore > out.rangeScore && out.trendDownScore > out.trendUpScore) {
      out.state = out.confidence < 7.5 ? DayTypeState.TRANSITION : DayTypeState.TREND_DOWN;
    }
    else if (out.rangeScore >= Math.max(out.trendUpScore, out.trendDownScore)) {
      out.state = c.rotationCount >= Math.max(4, cfgLikeVolatileThreshold(c)) ? DayTypeState.RANGE_VOLATILE : DayTypeState.RANGE_BALANCED;
    }
    else {
      out.state = DayTypeState.TRANSITION;
    }

    out.summary = String.format(Locale.US, "%s | Conf %.1f", out.state, out.confidence);
  }

  private static int cfgLikeVolatileThreshold(InitialBalanceContext c) {
    return Math.max(4, c.midCrossCount >= 3 ? 3 : 4);
  }

  private static void buildReasons(InitialBalanceContext c, DayTypeFeatures f, DayTypeResult out) {
    switch (out.state) {
      case TREND_UP:
        if (f.smallIbScore > 0.5) out.reasons.add("Small IB vs recent history");
        if (f.extensionUpScore > 0.45) out.reasons.add("Post-IB upside extension");
        if (f.acceptanceUpScore > 0.45) out.reasons.add("Acceptance above IB high");
        if (f.holdUpScore > 0.45) out.reasons.add("Closes holding above IB");
        if (f.valueMigrationUpScore > 0.45) out.reasons.add("VWAP/value migrating higher");
        if (f.trendContinuationQuality > 0.45) out.reasons.add("Pullbacks staying constructive");
        if (f.gapUpContextScore > 0.35) out.reasons.add("Gap context supports upside continuation");
        if (f.volumeTrendSupportScore > 0.35) out.reasons.add("IB volume supportive of trend continuation");
        break;

      case TREND_DOWN:
        if (f.smallIbScore > 0.5) out.reasons.add("Small IB vs recent history");
        if (f.extensionDownScore > 0.45) out.reasons.add("Post-IB downside extension");
        if (f.acceptanceDownScore > 0.45) out.reasons.add("Acceptance below IB low");
        if (f.holdDownScore > 0.45) out.reasons.add("Closes holding below IB");
        if (f.valueMigrationDownScore > 0.45) out.reasons.add("VWAP/value migrating lower");
        if (f.trendContinuationQuality > 0.45) out.reasons.add("Pullbacks staying constructive");
        if (f.gapDownContextScore > 0.35) out.reasons.add("Gap context supports downside continuation");
        if (f.volumeTrendSupportScore > 0.35) out.reasons.add("IB volume supportive of trend continuation");
        break;

      case RANGE_BALANCED:
      case RANGE_VOLATILE:
        if (f.largeIbScore > 0.45) out.reasons.add("Large IB vs recent history");
        if (f.rotationalIbScore > 0.45) out.reasons.add("Rotational / non-directional IB");
        if (f.rangeStructureScore > 0.45) out.reasons.add("Repeated rotation / overlap");
        if (f.staticValueScore > 0.45) out.reasons.add("VWAP/value not migrating");
        if (f.balanceReversionScore > 0.45) out.reasons.add("Breaks reverting back into balance");
        if (c.failedBreakCount > 0) out.reasons.add("Failed extension attempts");
        if (c.reenteredIb) out.reasons.add("Reentry back into IB");
        if (f.volumeBalanceSupportScore > 0.45) out.reasons.add("IB volume not supportive of clean trend continuation");
        break;

      case LIQUIDATION_THEN_BALANCE:
        out.reasons.add("Fast impulse without durable acceptance");
        if (c.reenteredIb) out.reasons.add("Reentry back into balance after impulse");
        if (!Double.isNaN(c.pullbackFromExtremeTicks) && c.pullbackFromExtremeTicks > 0) out.reasons.add("Large giveback from extension extreme");
        out.reasons.add("Likely liquidation/inventory move");
        break;

      case TRANSITION:
        out.reasons.add("Mixed evidence");
        if (Math.max(out.trendUpScore, out.trendDownScore) > 0.0) out.reasons.add("Directional evidence present but not dominant");
        if (out.rangeScore > 0.0) out.reasons.add("Range evidence still competing");
        if (c.failedBreakCount > 0) out.reasons.add("Failed early breakouts");
        if (f.gapUpContextScore > 0.0 || f.gapDownContextScore > 0.0) out.reasons.add("Gap context present but not decisive");
        break;

      default:
        out.reasons.add("IB not complete or insufficient context");
        break;
    }
  }

  private static void buildDebug(InitialBalanceContext c, DayTypeFeatures f, DayTypeResult out, DayTypeConfig cfg) {
    String ibWindow = String.format(
        Locale.US,
        "IBWin=%s->%s | Sess=%s | idx=%d->%d | bars=%d | IBH=%.2f | IBL=%.2f | IB=%.2f | complete=%s",
        fmtEt(c.sessionStartTime),
        fmtEt(c.ibEndTime),
        fmtEt(c.currentBarTime),
        c.sessionStartIndex,
        c.ibEndIndex,
        c.ibBarCount,
        c.ibHigh,
        c.ibLow,
        c.ibRange,
        c.ibComplete);

    String structure = String.format(
        Locale.US,
        "NormIB=%.2f | IBCL=%.2f | IBBody=%.2f | GapT=%.1f | GapATR=%.2f | ExtUp=%.1f | ExtDn=%.1f | HoldUp=%.2f | HoldDn=%.2f",
        c.normalizedIbSize,
        c.ibCloseLocation,
        c.ibBodyPct,
        c.sessionGapTicks,
        c.sessionGapAtr,
        c.firstExtensionUpTicks,
        c.firstExtensionDownTicks,
        f.holdUpScore,
        f.holdDownScore);

    String flow = String.format(
        Locale.US,
        "RTHVWAPDrift=%.1f | RTHVWAP=%.2f | IBVWAP=%.2f | PostIBVWAP=%.2f | VWAPTh=%.1f | Eff=%.2f | Pull=%.1f | Rot=%d | MidX=%d | FailBrk=%d | Liq=%.2f | IBVol=%.2f",
        c.valueMigrationTicks,
        c.sessionVwap,
        c.ibVwap,
        c.postIbVwap,
        Math.max(cfg.valueMigrationMinTicks, MIN_STRUCTURAL_VWAP_DRIFT_TICKS),
        c.directionalEfficiency,
        c.pullbackFromExtremeTicks,
        c.rotationCount,
        c.midCrossCount,
        c.failedBreakCount,
        f.liquidationScore,
        c.normalizedIbVolume);

    if (c.debugNotes.isEmpty()) {
      out.debugText = ibWindow + "\n" + structure + "\n" + flow;
    }
    else {
      out.debugText = ibWindow + "\n" + structure + "\n" + flow + "\n" + join(c.debugNotes, " | ");
    }
  }

  private static String fmtEt(long t) {
    if (t <= 0L) return "n/a";
    return ET_FMT.format(Instant.ofEpochMilli(t));
  }

  private static void populateIbWindowStats(InitialBalanceContext out, DataSeries s, DayTypeConfig cfg) {
    if (out.sessionStartIndex < 0 || out.ibEndIndex < out.sessionStartIndex) return;

    double hi = Double.NEGATIVE_INFINITY;
    double lo = Double.POSITIVE_INFINITY;
    double volSum = 0.0;
    double pvSum = 0.0;
    for (int i = out.sessionStartIndex; i <= out.ibEndIndex; i++) {
      hi = Math.max(hi, s.getHigh(i));
      lo = Math.min(lo, s.getLow(i));
      double v = safeVolume(s, i);
      double tp = typicalPrice(s, i);
      volSum += v;
      pvSum += tp * v;
    }
    out.ibHigh = hi;
    out.ibLow = lo;
    out.ibRange = Math.max(0.0, hi - lo);
    out.ibMid = (hi + lo) * 0.5;
    out.ibClose = s.getClose(out.ibEndIndex);
    out.open = s.getOpen(out.sessionStartIndex);
    out.ibOpen = out.open;
    out.ibCloseLocation = out.ibRange <= 1e-9 ? 0.5 : (out.ibClose - out.ibLow) / out.ibRange;
    out.ibNetChangeTicks = out.tickSize > 0.0 ? (out.ibClose - out.ibOpen) / out.tickSize : Double.NaN;
    out.ibBodyPct = out.ibRange <= 1e-9 ? 0.0 : Math.abs(out.ibClose - out.ibOpen) / out.ibRange;
    out.ibVwap = volSum > 0.0 ? pvSum / volSum : out.ibMid;
    out.ibVolume = volSum;
  }

  private static void populateSessionStats(InitialBalanceContext out, DataSeries s) {
    double hi = Double.NEGATIVE_INFINITY;
    double lo = Double.POSITIVE_INFINITY;
    double volSum = 0.0;
    double pvSum = 0.0;
    for (int i = out.sessionStartIndex; i <= out.index; i++) {
      if (s.getStartTime(i) < out.sessionStartTime) continue;
      hi = Math.max(hi, s.getHigh(i));
      lo = Math.min(lo, s.getLow(i));
      double v = safeVolume(s, i);
      double tp = typicalPrice(s, i);
      volSum += v;
      pvSum += tp * v;
    }
    out.sessionHigh = hi;
    out.sessionLow = lo;
    out.sessionVwap = volSum > 0.0 ? pvSum / volSum : s.getClose(out.index);
  }

  private static void populateRecentIbStats(InitialBalanceContext out, DataSeries s, Instrument instr, DayTypeConfig cfg) {
    List<Double> ranges = new ArrayList<Double>();
    List<Double> ibVols = new ArrayList<Double>();
    int sessionsSeen = 0;

    if (out.sessionStartIndex > 0) {
      out.priorSessionClose = s.getClose(out.sessionStartIndex - 1);
      if (out.tickSize > 0.0) {
        out.sessionGapTicks = (out.open - out.priorSessionClose) / out.tickSize;
      }
    }

    if (out.sessionStartIndex < 0 || out.sessionStartTime <= 0L) {
      out.lookbackSessionsUsed = 0;
      applyManualAidFallback(out, cfg, 0);
      out.debugNotes.add("LookbackSessionsUsed=0/" + cfg.recentLookbackSessions);
      return;
    }

    LocalDate currentSessionDate = Instant.ofEpochMilli(out.sessionStartTime).atZone(ET_ZONE).toLocalDate();
    int maxCandidateDays = Math.max(cfg.recentLookbackSessions * 5, cfg.recentLookbackSessions + 2);

    for (int daysBack = 1; daysBack <= maxCandidateDays && sessionsSeen < cfg.recentLookbackSessions; daysBack++) {
      long candidateStart = ZonedDateTime.of(
          currentSessionDate.minusDays(daysBack),
          LocalTime.of(9, 30),
          ET_ZONE
      ).toInstant().toEpochMilli();

      int startIdx = findFirstBarAtOrAfter(s, out.sessionStartIndex - 1, candidateStart);
      if (startIdx < 0 || startIdx >= out.sessionStartIndex) continue;

      LocalDate barDate = Instant.ofEpochMilli(s.getStartTime(startIdx)).atZone(ET_ZONE).toLocalDate();
      if (!barDate.equals(currentSessionDate.minusDays(daysBack))) continue;

      int endIdx = findIbEndIndex(s, startIdx, out.sessionStartIndex - 1, candidateStart + cfg.ibMinutes * 60_000L);
      if (endIdx < startIdx) continue;

      double hi = Double.NEGATIVE_INFINITY;
      double lo = Double.POSITIVE_INFINITY;
      double vol = 0.0;
      int bars = 0;
      for (int j = startIdx; j <= endIdx; j++) {
        long barTime = s.getStartTime(j);
        if (barTime < candidateStart) continue;
        if (barTime >= candidateStart + cfg.ibMinutes * 60_000L) break;
        hi = Math.max(hi, s.getHigh(j));
        lo = Math.min(lo, s.getLow(j));
        vol += safeVolume(s, j);
        bars++;
      }
      if (bars > 0 && hi > lo) {
        ranges.add(hi - lo);
        ibVols.add(vol);
        sessionsSeen++;
      }
    }

    out.lookbackSessionsUsed = sessionsSeen;
    out.recentMedianIbRange = median(ranges);
    out.normalizedIbSize = (!Double.isNaN(out.recentMedianIbRange) && out.recentMedianIbRange > 0.0)
        ? out.ibRange / out.recentMedianIbRange : Double.NaN;

    out.atrLikeRange = average(ranges);
    out.recentMedianIbVolume = median(ibVols);
    out.normalizedIbVolume = (!Double.isNaN(out.recentMedianIbVolume) && out.recentMedianIbVolume > 0.0)
        ? out.ibVolume / out.recentMedianIbVolume : Double.NaN;

    applyManualAidFallback(out, cfg, sessionsSeen);

    out.sessionGapAtr = (!Double.isNaN(out.atrLikeRange) && out.atrLikeRange > 0.0 && !Double.isNaN(out.priorSessionClose))
        ? (out.open - out.priorSessionClose) / out.atrLikeRange : Double.NaN;
    out.gapUp = !Double.isNaN(out.sessionGapTicks) && out.sessionGapTicks > 0.0;
    out.gapDown = !Double.isNaN(out.sessionGapTicks) && out.sessionGapTicks < 0.0;

    out.debugNotes.add("LookbackSessionsUsed=" + sessionsSeen + "/" + cfg.recentLookbackSessions);
  }

  private static void applyManualAidFallback(InitialBalanceContext out, DayTypeConfig cfg, int sessionsSeen) {
    if (out == null || cfg == null || !cfg.useManualAid) return;

    boolean insufficientLookback = sessionsSeen < Math.max(1, cfg.recentLookbackSessions);

    if (shouldUseManual(cfg.preferManualAid, insufficientLookback, out.recentMedianIbRange, cfg.manualRecentMedianIbRange)) {
      out.recentMedianIbRange = cfg.manualRecentMedianIbRange;
      out.manualAidUsed = true;
    }
    if (shouldUseManual(cfg.preferManualAid, insufficientLookback, out.recentMedianIbVolume, cfg.manualRecentMedianIbVolume)) {
      out.recentMedianIbVolume = cfg.manualRecentMedianIbVolume;
      out.manualAidUsed = true;
    }
    if (shouldUseManual(cfg.preferManualAid, insufficientLookback, out.atrLikeRange, cfg.manualAtrLikeRange)) {
      out.atrLikeRange = cfg.manualAtrLikeRange;
      out.manualAidUsed = true;
    }
    if (shouldUseManual(cfg.preferManualAid, insufficientLookback, out.priorSessionClose, cfg.manualPriorSessionClose)) {
      out.priorSessionClose = cfg.manualPriorSessionClose;
      if (out.tickSize > 0.0 && !Double.isNaN(out.open)) {
        out.sessionGapTicks = (out.open - out.priorSessionClose) / out.tickSize;
      }
      out.manualAidUsed = true;
    }

    out.normalizedIbSize = (!Double.isNaN(out.recentMedianIbRange) && out.recentMedianIbRange > 0.0)
        ? out.ibRange / out.recentMedianIbRange : Double.NaN;
    out.normalizedIbVolume = (!Double.isNaN(out.recentMedianIbVolume) && out.recentMedianIbVolume > 0.0)
        ? out.ibVolume / out.recentMedianIbVolume : Double.NaN;

    if (out.manualAidUsed) {
      out.debugNotes.add("ManualAidUsed");
    }
  }

  private static boolean shouldUseManual(boolean preferManualAid, boolean insufficientLookback, double autoValue, double manualValue) {
    if (Double.isNaN(manualValue)) return false;
    return preferManualAid || insufficientLookback || Double.isNaN(autoValue);
  }

  private static void populatePostIbStats(InitialBalanceContext out, DataSeries s, DayTypeConfig cfg) {
    int postStart = out.ibEndIndex + 1;
    if (postStart > out.index) return;

    double tol = cfg.reentryToleranceTicks * out.tickSize;
    boolean brokeAbove = false;
    boolean brokeBelow = false;
    boolean closedOutsideAbove = false;
    boolean closedOutsideBelow = false;
    int firstUpBar = -1;
    int firstDownBar = -1;
    double totalTravelTicks = 0.0;
    double bestUp = 0.0;
    double bestDn = 0.0;
    double prevClose = s.getClose(out.ibEndIndex);

    double postVol = 0.0;
    double postPv = 0.0;

    for (int i = postStart; i <= out.index; i++) {
      if (s.getStartTime(i) < out.sessionStartTime) continue;

      double h = s.getHigh(i);
      double l = s.getLow(i);
      double c = s.getClose(i);
      double v = safeVolume(s, i);
      postVol += v;
      postPv += typicalPrice(s, i) * v;

      totalTravelTicks += out.tickSize > 0.0 ? Math.abs(c - prevClose) / out.tickSize : 0.0;
      prevClose = c;

      if (h > out.ibHigh + tol) {
        if (!brokeAbove) {
          brokeAbove = true;
          firstUpBar = i;
        }
        bestUp = Math.max(bestUp, (h - out.ibHigh) / out.tickSize);
      }
      if (l < out.ibLow - tol) {
        if (!brokeBelow) {
          brokeBelow = true;
          firstDownBar = i;
        }
        bestDn = Math.max(bestDn, (out.ibLow - l) / out.tickSize);
      }

      if (c > out.ibHigh + tol) {
        out.barsHeldAboveIb++;
        out.closesAboveIb++;
        closedOutsideAbove = true;
      }
      else if (c < out.ibLow - tol) {
        out.barsHeldBelowIb++;
        out.closesBelowIb++;
        closedOutsideBelow = true;
      }
      else if ((brokeAbove && closedOutsideAbove) || (brokeBelow && closedOutsideBelow)) {
        out.reenteredIb = true;
      }

      boolean failedUp = h > out.ibHigh + tol && c < out.ibHigh - tol * 0.25;
      boolean failedDn = l < out.ibLow - tol && c > out.ibLow + tol * 0.25;
      if (failedUp || failedDn) out.failedBreakCount++;
    }

    out.firstExtensionUpTicks = bestUp;
    out.firstExtensionDownTicks = bestDn;
    out.acceptedAboveIb = out.barsHeldAboveIb >= cfg.acceptanceHoldBars;
    out.acceptedBelowIb = out.barsHeldBelowIb >= cfg.acceptanceHoldBars;

    if (bestUp > bestDn) out.dominantDirection = 1;
    else if (bestDn > bestUp) out.dominantDirection = -1;
    else out.dominantDirection = 0;

    if (out.tickSize > 0.0) {
      out.netMoveTicks = (s.getClose(out.index) - s.getClose(out.ibEndIndex)) / out.tickSize;
      double absNet = Math.abs(out.netMoveTicks);
      out.directionalEfficiency = totalTravelTicks > 0.0 ? clamp01(absNet / totalTravelTicks) : 0.0;

      if (out.dominantDirection > 0) {
        double bestPrice = out.ibHigh + bestUp * out.tickSize;
        out.pullbackFromExtremeTicks = Math.max(0.0, (bestPrice - s.getClose(out.index)) / out.tickSize);
      }
      else if (out.dominantDirection < 0) {
        double bestPrice = out.ibLow - bestDn * out.tickSize;
        out.pullbackFromExtremeTicks = Math.max(0.0, (s.getClose(out.index) - bestPrice) / out.tickSize);
      }
      else {
        out.pullbackFromExtremeTicks = 0.0;
      }
    }

    if (firstUpBar >= 0) {
      out.impulseDistanceTicks = out.firstExtensionUpTicks;
      out.impulseSpeedBars = Math.max(1, firstUpBar - out.ibEndIndex);
    }
    if (firstDownBar >= 0 && (Double.isNaN(out.impulseDistanceTicks) || out.firstExtensionDownTicks > out.impulseDistanceTicks)) {
      out.impulseDistanceTicks = out.firstExtensionDownTicks;
      out.impulseSpeedBars = Math.max(1, firstDownBar - out.ibEndIndex);
    }

    out.postIbVwap = postVol > 0.0 ? postPv / postVol : s.getClose(out.index);
    double structuralVwap = Double.isNaN(out.sessionVwap) ? out.postIbVwap : out.sessionVwap;
    out.valueMigrationTicks = out.tickSize > 0.0 ? (structuralVwap - out.ibVwap) / out.tickSize : 0.0;
  }

  private static void populateRegimeProxies(InitialBalanceContext out, DataSeries s, DayTypeConfig cfg) {
    int postStart = out.ibEndIndex + 1;
    if (postStart > out.index) return;

    int midCrosses = 0;
    double overlapSum = 0.0;
    int overlapCount = 0;
    int edgeReversals = 0;

    double prevClose = s.getClose(postStart);
    for (int i = postStart + 1; i <= out.index; i++) {
      double c = s.getClose(i);
      if ((prevClose <= out.ibMid && c > out.ibMid) || (prevClose >= out.ibMid && c < out.ibMid)) {
        midCrosses++;
      }
      prevClose = c;

      double overlap = Math.max(0.0,
          Math.min(s.getHigh(i), s.getHigh(i - 1)) - Math.max(s.getLow(i), s.getLow(i - 1)));
      double union = Math.max(s.getHigh(i), s.getHigh(i - 1)) - Math.min(s.getLow(i), s.getLow(i - 1));
      if (union > 1e-9) {
        overlapSum += overlap / union;
        overlapCount++;
      }

      boolean rejectHigh = s.getHigh(i) > out.ibHigh && s.getClose(i) < out.ibHigh;
      boolean rejectLow = s.getLow(i) < out.ibLow && s.getClose(i) > out.ibLow;
      if (rejectHigh || rejectLow) edgeReversals++;
    }

    out.midCrossCount = midCrosses;
    out.rotationCount = midCrosses;
    out.overlapRatio = overlapCount > 0 ? overlapSum / overlapCount : 0.0;
    out.edgeReversalCount = edgeReversals;

    double impulseRef = !Double.isNaN(out.atrLikeRange) && out.atrLikeRange > 0.0 ? out.atrLikeRange : out.ibRange;
    double impulseThreshTicks = out.tickSize > 0.0 ? (cfg.liquidationImpulseAtrMultiple * impulseRef) / out.tickSize : Double.POSITIVE_INFINITY;
    boolean fastImpulse = !Double.isNaN(out.impulseDistanceTicks) && out.impulseDistanceTicks >= impulseThreshTicks && out.impulseSpeedBars <= 3;
    boolean poorAcceptance = !out.acceptedAboveIb && !out.acceptedBelowIb;
    boolean quickGiveback = !Double.isNaN(out.pullbackFromExtremeTicks) && out.pullbackFromExtremeTicks >= Math.max(2.0, 0.50 * Math.max(out.firstExtensionUpTicks, out.firstExtensionDownTicks));
    out.liquidationWarning = fastImpulse && poorAcceptance && (out.rotationCount >= 1 || quickGiveback);
  }

  private static int findFirstBarAtOrAfter(DataSeries s, int maxIndex, long targetTime) {
    if (s == null || s.size() == 0 || maxIndex < 0) return -1;
    int lim = Math.min(maxIndex, s.size() - 1);
    for (int i = 0; i <= lim; i++) {
      if (s.getStartTime(i) >= targetTime) return i;
    }
    return -1;
  }

  private static int findSessionStartIndex(DataSeries s, int index, long sessionStartTime) {
    if (s == null || index < 0 || index >= s.size()) return -1;

    int i = index;

    // Walk backward until we find the first bar whose start time is at or after the
    // intended RTH session start. This avoids pulling in overnight/premarket bars
    // that may share the same session anchor from getStartOfDay(..., true).
    while (i > 0 && s.getStartTime(i - 1) >= sessionStartTime) {
      i--;
    }

    if (s.getStartTime(i) < sessionStartTime) {
      while (i <= index && s.getStartTime(i) < sessionStartTime) {
        i++;
      }
      if (i > index) return -1;
    }

    return i;
  }

  private static int findIbEndIndex(DataSeries s, int fromIndex, int maxIndex, long ibEndTime) {
    int end = -1;
    for (int i = Math.max(0, fromIndex); i <= maxIndex; i++) {
      if (s.getStartTime(i) < ibEndTime) end = i;
      else break;
    }
    return end;
  }

  private static double safeVolume(DataSeries s, int index) {
    try {
      return s.getVolume(index);
    }
    catch (Exception ex) {
      return 1.0;
    }
  }

  private static double typicalPrice(DataSeries s, int index) {
    return (s.getHigh(index) + s.getLow(index) + s.getClose(index)) / 3.0;
  }

  private static double median(List<Double> vals) {
    if (vals == null || vals.isEmpty()) return Double.NaN;
    double[] arr = new double[vals.size()];
    for (int i = 0; i < vals.size(); i++) arr[i] = vals.get(i);
    Arrays.sort(arr);
    int mid = arr.length / 2;
    return (arr.length % 2 == 1) ? arr[mid] : (arr[mid - 1] + arr[mid]) * 0.5;
  }

  private static double average(List<Double> vals) {
    if (vals == null || vals.isEmpty()) return Double.NaN;
    double sum = 0.0;
    for (double v : vals) sum += v;
    return sum / vals.size();
  }

  private static double safeTickSize(Instrument instr) {
    try {
      double tick = instr.getTickSize();
      return tick > 0 ? tick : 0.25;
    }
    catch (Exception ex) {
      return 0.25;
    }
  }

  private static double clamp01(double v) {
    return Math.max(0.0, Math.min(1.0, v));
  }

  private static double normalizeTicks(double ticks, double threshold) {
    if (threshold <= 0.0) return 0.0;
    return clamp01(ticks / threshold);
  }

  private static double safe01(double v) {
    if (Double.isNaN(v) || Double.isInfinite(v)) return 0.0;
    return clamp01(v);
  }

  private static double secondBest(double a, double b, double c) {
    double[] vals = new double[] {a, b, c};
    Arrays.sort(vals);
    return vals[1];
  }

  private static String join(List<String> vals, String delim) {
    if (vals == null || vals.isEmpty()) return "";
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < vals.size(); i++) {
      if (i > 0) sb.append(delim);
      sb.append(vals.get(i));
    }
    return sb.toString();
  }
}
