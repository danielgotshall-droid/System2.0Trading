package study_examples.lzs.context;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import study_examples.lzs.model.LzsSide;
import study_examples.lzs.model.LzsZoneCandidate;

public final class LzsContextMergeEngine {

  private static final int STATE_PROBING_BELOW = -2;
  private static final int STATE_IN_ZONE = 0;
  private static final int STATE_PROBING_ABOVE = 1;
  private static final int STATE_ACCEPTED_BELOW = -3;
  private static final int STATE_ACCEPTED_ABOVE = 2;

  private static final class RefRoleState {
    int prevRelation = Integer.MIN_VALUE;
    int zoneState = STATE_IN_ZONE;
    int prevZoneState = STATE_IN_ZONE;
    long lastTouchMs = Long.MIN_VALUE;
    long lastReclaimMs = Long.MIN_VALUE;
    long lastRejectMs = Long.MIN_VALUE;
    long lastHoldAboveMs = Long.MIN_VALUE;
    long lastHoldBelowMs = Long.MIN_VALUE;
    long lastExtensionAboveMs = Long.MIN_VALUE;
    long lastExtensionBelowMs = Long.MIN_VALUE;
    long probeAboveSinceMs = Long.MIN_VALUE;
    long probeBelowSinceMs = Long.MIN_VALUE;
    long acceptedAboveSinceMs = Long.MIN_VALUE;
    long acceptedBelowSinceMs = Long.MIN_VALUE;
    long lastZoneStateChangeMs = Long.MIN_VALUE;
    long lastSeenMs = Long.MIN_VALUE;
    double lastDistanceTicks = Double.NaN;
  }

  private final Map<String, RefRoleState> refStates = new HashMap<String, RefRoleState>();
  private long activeSessionStartTime = Long.MIN_VALUE;

  public LzsMergedContextResult merge(LzsSide side, LzsZoneCandidate candidate, LzsContextResult raw, LzsContextSnapshot snap, LzsContextConfig cfg) {
    LzsMergedContextResult out = new LzsMergedContextResult();
    out.side = side;
    if (side == null || raw == null || snap == null || cfg == null || !cfg.enableContext || !cfg.enableMergedContext) {
      out.summary = "MCTX OFF";
      return out;
    }

    rotateSessionIfNeeded(snap.sessionStartTime);

    LzsContextWeights w = cfg.mergeWeights == null ? LzsContextWeights.defaults() : cfg.mergeWeights;
    double tick = Math.max(1e-9, snap.tickSize);
    double marketPrice = !Double.isNaN(snap.lastPrice) ? snap.lastPrice : mid(candidate);
    long now = snap.barTime > 0L ? snap.barTime : System.currentTimeMillis();
    out.sessionRangePct = computeSessionRangePct(snap);

    // Reference role scoring.
    applyRef(out, side, snap.priorDayHigh, "PDH", true, false, raw.nearPriorDayHigh, cfg.structuralProximityTicks, 1.00, marketPrice, tick, now, cfg);
    applyRef(out, side, snap.priorDayLow, "PDL", false, true, raw.nearPriorDayLow, cfg.structuralProximityTicks, 1.00, marketPrice, tick, now, cfg);
    applyRef(out, side, snap.overnightHigh, "ONH", true, false, raw.nearOvernightHigh, cfg.overnightProximityTicks, 0.85, marketPrice, tick, now, cfg);
    applyRef(out, side, snap.overnightLow, "ONL", false, true, raw.nearOvernightLow, cfg.overnightProximityTicks, 0.85, marketPrice, tick, now, cfg);
    applyRef(out, side, snap.priorValueAreaHigh, "VAH", true, false, raw.nearPriorValueAreaHigh, cfg.valueAreaProximityTicks, 0.80, marketPrice, tick, now, cfg);
    applyRef(out, side, snap.priorValueAreaLow, "VAL", false, true, raw.nearPriorValueAreaLow, cfg.valueAreaProximityTicks, 0.80, marketPrice, tick, now, cfg);
    applyRef(out, side, snap.ibHigh, "IBH", true, false, raw.nearIbHigh, cfg.ibProximityTicks, 0.70, marketPrice, tick, now, cfg);
    applyRef(out, side, snap.ibLow, "IBL", false, true, raw.nearIbLow, cfg.ibProximityTicks, 0.70, marketPrice, tick, now, cfg);
    applyRef(out, side, snap.openingRangeHigh, "ORH", true, false, raw.nearOpeningRangeHigh, cfg.openingRangeProximityTicks, 0.60, marketPrice, tick, now, cfg);
    applyRef(out, side, snap.openingRangeLow, "ORL", false, true, raw.nearOpeningRangeLow, cfg.openingRangeProximityTicks, 0.60, marketPrice, tick, now, cfg);

    applyRef(out, side, snap.sessionOpen, "OPEN", false, false, raw.nearSessionOpen, cfg.structuralProximityTicks, 0.35, marketPrice, tick, now, cfg);
    applyRef(out, side, snap.sessionVwap, "VWAP", false, false, raw.nearVwap, cfg.vwapProximityTicks, 0.65, marketPrice, tick, now, cfg);
    applyRef(out, side, snap.priorPoc, "POC", false, false, raw.nearPriorPoc, cfg.valueAreaProximityTicks, 0.45, marketPrice, tick, now, cfg);

    out.structuralScore = calibrate(out.supportiveEdgeScore + 0.65 * out.holdScore + 0.85 * out.reclaimScore, 2.40);
    out.sessionReferenceScore = calibrate(out.valueAlignmentScore + 0.55 * out.freshnessScore, 2.40);
    out.dayTypeApplicable = isDayTypeApplicable(snap, cfg);
    out.dayTypeScore = out.dayTypeApplicable ? calibrate(scoreDayType(side, raw, snap, cfg), 1.25) : 0.0;

    out.continuationSupport = calibrate(
        0.90 * out.reclaimScore +
        0.75 * out.holdScore +
        0.45 * out.valueAlignmentScore +
        0.60 * out.dayTypeScore,
        2.70);

    out.fadeSupport = calibrate(
        0.95 * out.extensionScore +
        0.80 * out.rejectScore +
        0.35 * out.valueAlignmentScore +
        0.55 * (raw.dayTypeSupportsFade ? 1.0 : 0.0),
        2.70);

    // New higher-order suitability layer.
    applyRangeLocationSuitability(out, side, raw, snap, cfg);
    applyTrendContinuationSuitability(out, side, raw, snap, cfg, marketPrice, tick, now);
    applyForwardObstructionPenalty(out, side, snap, cfg, marketPrice, tick, now, w);

    out.intent = inferIntent(side, out, raw);

    if (raw.freeFloating) {
      out.penaltyScore += Math.max(0.0, w.freeFloatingPenalty);
      out.freeFloating = true;
      addReasonIf(out, true, "FREE");
    }
    else {
      out.freeFloating = false;
    }

    if (hasOppositeBias(side, snap)) {
      out.penaltyScore += Math.max(0.0, w.oppositeBiasPenalty);
      out.oppositeBias = true;
      addReasonIf(out, true, "OPP");
    }

    out.penaltyScore += out.referenceConflictPenalty;

    double nominalWeightSum =
        Math.max(0.0, w.structuralWeight) +
        Math.max(0.0, w.sessionReferenceWeight) +
        Math.max(0.0, w.dayTypeWeight) +
        Math.max(0.0, w.locationSuitabilityWeight) +
        Math.max(0.0, w.trendContinuationWeight);

    double activeWeightSum =
        Math.max(0.0, w.structuralWeight) +
        Math.max(0.0, w.sessionReferenceWeight) +
        (out.dayTypeApplicable ? Math.max(0.0, w.dayTypeWeight) : 0.0) +
        (out.locationApplicable ? Math.max(0.0, w.locationSuitabilityWeight) : 0.0) +
        (out.trendApplicable ? Math.max(0.0, w.trendContinuationWeight) : 0.0);

    double weightedBaseRaw =
        Math.max(0.0, w.structuralWeight) * out.structuralScore +
        Math.max(0.0, w.sessionReferenceWeight) * out.sessionReferenceScore +
        (out.dayTypeApplicable ? Math.max(0.0, w.dayTypeWeight) * out.dayTypeScore : 0.0) +
        (out.locationApplicable ? Math.max(0.0, w.locationSuitabilityWeight) * out.locationSuitabilityScore : 0.0) +
        (out.trendApplicable ? Math.max(0.0, w.trendContinuationWeight) * out.trendContinuationSuitability : 0.0);

    out.activeWeightSum = activeWeightSum;
    out.normalizationFactor = activeWeightSum <= 1e-9 || nominalWeightSum <= 1e-9
        ? 1.0
        : (nominalWeightSum / activeWeightSum);

    double weightedBase = weightedBaseRaw * out.normalizationFactor;

    double intentBoost = 0.0;
    if (out.intent == LzsContextIntent.CONTINUATION) {
      intentBoost = Math.max(0.0, w.continuationBiasBoost) * out.continuationSupport;
      addReasonIf(out, out.continuationSupport >= w.minReasonContribution, "CONT");
    }
    else if (out.intent == LzsContextIntent.FADE) {
      intentBoost = Math.max(0.0, w.fadeBiasBoost) * out.fadeSupport;
      addReasonIf(out, out.fadeSupport >= w.minReasonContribution, "FADE");
    }
    else {
      addReasonIf(out, !out.freeFloating, "NEUT");
    }

    addReasonIf(out, out.dayTypeScore >= w.minReasonContribution, dayTypeReason(side, raw));

    out.sideScore = clamp(weightedBase + intentBoost - out.penaltyScore, 0.0, 4.0);
    out.supportsSide = out.sideScore >= Math.max(0.0, w.minSupportThreshold);
    out.supportsContinuation = out.continuationSupport >= Math.max(0.0, w.minSupportThreshold);
    out.supportsFade = out.fadeSupport >= Math.max(0.0, w.minSupportThreshold);

    out.mergedFilterThreshold = Math.max(0.0, cfg.minMergedContextScoreForFilter);
    out.structuralFilterThreshold = Math.max(0.0, cfg.minStructuralScoreForFilter);
    out.dayTypeFilterThreshold = Math.max(0.0, cfg.minDayTypeScoreForFilter);

    applyFilterStatus(out, cfg);

    String reasons = out.buildReasonSummary(6);
    if (out.freeFloating && out.sideScore <= 0.0) {
      out.summary = out.oppositeBias ? "MCTX FREE/OPP" : "MCTX FREE";
    }
    else if (reasons.length() > 0) {
      out.summary = String.format(Locale.US, "MCTX %.1f %s %s", out.sideScore, out.intent.shortLabel(), reasons);
    }
    else {
      out.summary = String.format(Locale.US, "MCTX %.1f %s", out.sideScore, out.intent.shortLabel());
    }

    String refSig = candidate == null || !candidate.valid
        ? "REF LAST"
        : String.format(Locale.US, "REF %.2f-%.2f", candidate.zoneLow, candidate.zoneHigh);
    out.debugText = String.format(Locale.US,
        "%s | sup %.2f | val %.2f | dt %s | loc %s | tr %s | obs %.2f | ctp %.2f | rcl %.2f | rej %.2f | hld %.2f | ext %.2f | fr %.2f | pen %.2f | rng %.2f | aw %.2f | nf %.2f | cap %s@%.1ft%s | flt m%.2f/s%.2f/d%.2f | score %.2f | rsn %s",
        refSig,
        out.structuralScore,
        out.sessionReferenceScore,
        fmtApplicable(out.dayTypeApplicable, out.dayTypeScore),
        fmtApplicable(out.locationApplicable, out.locationSuitabilityScore),
        fmtApplicable(out.trendApplicable, out.trendContinuationSuitability),
        out.forwardObstructionScore,
        out.countertrendPenalty,
        out.reclaimScore,
        out.rejectScore,
        out.holdScore,
        out.extensionScore,
        out.freshnessScore,
        out.penaltyScore,
        out.sessionRangePct,
        out.activeWeightSum,
        out.normalizationFactor,
        out.nearestOpposingReference == null || out.nearestOpposingReference.length() == 0 ? "-" : out.nearestOpposingReference,
        Double.isNaN(out.nearestOpposingDistanceTicks) ? 0.0 : out.nearestOpposingDistanceTicks,
        out.nearestOpposingZoneState == null || out.nearestOpposingZoneState.length() == 0 ? "" : (":" + out.nearestOpposingZoneState),
        out.mergedFilterThreshold,
        out.structuralFilterThreshold,
        out.dayTypeFilterThreshold,
        out.sideScore,
        out.buildReasonSummary());
    return out;
  }

  private void applyFilterStatus(LzsMergedContextResult out, LzsContextConfig cfg) {
    out.passesFilter = true;
    out.filterReason = "";
    if (cfg == null || cfg.contextMode != LzsContextMode.OPTIONAL_FILTER) return;
    if (out.sideScore < Math.max(0.0, cfg.minMergedContextScoreForFilter)) {
      out.passesFilter = false;
      out.filterReason = "mrg";
      return;
    }
    if (out.structuralScore < Math.max(0.0, cfg.minStructuralScoreForFilter)) {
      out.passesFilter = false;
      out.filterReason = "str";
      return;
    }
    if (out.dayTypeApplicable && out.dayTypeScore < Math.max(0.0, cfg.minDayTypeScoreForFilter)) {
      out.passesFilter = false;
      out.filterReason = "dt";
      return;
    }
    if (cfg.requireSupportsSideWhenFilterEnabled && !out.supportsSide) {
      out.passesFilter = false;
      out.filterReason = "side";
      return;
    }
    if (cfg.blockFreeFloatingWhenFilterEnabled && out.freeFloating) {
      out.passesFilter = false;
      out.filterReason = "free";
    }
  }

  private void applyForwardObstructionPenalty(LzsMergedContextResult out, LzsSide side, LzsContextSnapshot snap,
      LzsContextConfig cfg, double marketPrice, double tick, long now, LzsContextWeights w) {
    if (out == null || side == null || snap == null || cfg == null || Double.isNaN(marketPrice) || tick <= 0.0) return;

    ObstructionCandidate best = null;
    best = pickStronger(best, evaluateForwardObstruction(side, snap.priorDayHigh, "PDH", 1.00, marketPrice, tick, now, cfg, w));
    best = pickStronger(best, evaluateForwardObstruction(side, snap.priorDayLow, "PDL", 1.00, marketPrice, tick, now, cfg, w));
    best = pickStronger(best, evaluateForwardObstruction(side, snap.overnightHigh, "ONH", 0.85, marketPrice, tick, now, cfg, w));
    best = pickStronger(best, evaluateForwardObstruction(side, snap.overnightLow, "ONL", 0.85, marketPrice, tick, now, cfg, w));
    best = pickStronger(best, evaluateForwardObstruction(side, snap.priorValueAreaHigh, "VAH", 0.80, marketPrice, tick, now, cfg, w));
    best = pickStronger(best, evaluateForwardObstruction(side, snap.priorValueAreaLow, "VAL", 0.80, marketPrice, tick, now, cfg, w));
    best = pickStronger(best, evaluateForwardObstruction(side, snap.ibHigh, "IBH", 0.72, marketPrice, tick, now, cfg, w));
    best = pickStronger(best, evaluateForwardObstruction(side, snap.ibLow, "IBL", 0.72, marketPrice, tick, now, cfg, w));
    best = pickStronger(best, evaluateForwardObstruction(side, snap.openingRangeHigh, "ORH", 0.82, marketPrice, tick, now, cfg, w));
    best = pickStronger(best, evaluateForwardObstruction(side, snap.openingRangeLow, "ORL", 0.82, marketPrice, tick, now, cfg, w));
    best = pickStronger(best, evaluateForwardObstruction(side, snap.sessionOpen, "OPEN", 0.45, marketPrice, tick, now, cfg, w));
    best = pickStronger(best, evaluateForwardObstruction(side, snap.sessionVwap, "VWAP", 0.65, marketPrice, tick, now, cfg, w));
    best = pickStronger(best, evaluateForwardObstruction(side, snap.priorPoc, "POC", 0.45, marketPrice, tick, now, cfg, w));

    if (best == null || best.penalty <= 0.0) return;

    out.forwardObstructionScore = best.penalty;
    out.penaltyScore += best.penalty;
    out.nearestOpposingReference = best.label;
    out.nearestOpposingDistanceTicks = best.aheadTicks;
    out.nearestOpposingZoneState = zoneStateLabel(best.zoneState);

    String reason = "into" + best.label;
    addPriorityReasonIf(out, best.penalty >= Math.max(0.18, w.minReasonContribution), reason);
  }

  private static final class ObstructionCandidate {
    final String label;
    final double aheadTicks;
    final double penalty;
    final int zoneState;

    ObstructionCandidate(String label, double aheadTicks, double penalty, int zoneState) {
      this.label = label;
      this.aheadTicks = aheadTicks;
      this.penalty = penalty;
      this.zoneState = zoneState;
    }
  }

  private ObstructionCandidate evaluateForwardObstruction(LzsSide side, LzsReferenceValue ref, String label, double baseWeight,
      double marketPrice, double tick, long now, LzsContextConfig cfg, LzsContextWeights w) {
    return considerObstruction(side, ref, label, baseWeight, marketPrice, tick, now, cfg, w);
  }

  private ObstructionCandidate considerObstruction(LzsSide side, LzsReferenceValue ref, String label, double baseWeight, double marketPrice, double tick, long now, LzsContextConfig cfg, LzsContextWeights w) {
    if (side == null || ref == null || !ref.isReady() || Double.isNaN(ref.value) || Double.isNaN(marketPrice)) return null;

    LzsReferenceZoneProfile profile = zoneProfile(label, cfg);
    double zoneHalf = profile.zoneHalfWidthTicks;
    double zoneLower = ref.value - (zoneHalf * tick);
    double zoneUpper = ref.value + (zoneHalf * tick);
    double aheadTicks = side == LzsSide.LONG
        ? Math.max(0.0, (zoneLower - marketPrice) / tick)
        : Math.max(0.0, (marketPrice - zoneUpper) / tick);

    if (aheadTicks > Math.max(cfg.forwardObstructionNearTicks + 1, cfg.forwardObstructionFadeTicks)) return null;

    int zoneState = classifyZoneState(ref, label, marketPrice, tick, now, cfg);
    if (side == LzsSide.LONG && zoneState == STATE_ACCEPTED_ABOVE) return null;
    if (side == LzsSide.SHORT && zoneState == STATE_ACCEPTED_BELOW) return null;

    RefRoleState state = stateFor(label);
    double recentInteraction = max5(
        freshnessWeight(state.lastTouchMs, now, cfg.referenceInteractionFreshMs, cfg.referenceInteractionMaxAgeMs),
        freshnessWeight(state.lastReclaimMs, now, cfg.referenceInteractionFreshMs, cfg.referenceInteractionMaxAgeMs),
        freshnessWeight(state.lastRejectMs, now, cfg.referenceInteractionFreshMs, cfg.referenceInteractionMaxAgeMs),
        freshnessWeight(state.lastHoldAboveMs, now, cfg.referenceInteractionFreshMs, cfg.referenceInteractionMaxAgeMs),
        freshnessWeight(state.lastHoldBelowMs, now, cfg.referenceInteractionFreshMs, cfg.referenceInteractionMaxAgeMs));

    double staleFactor = 0.55 + (0.45 * (1.0 - recentInteraction));
    double nearWeight = distanceWeight(aheadTicks, cfg.forwardObstructionNearTicks, cfg.forwardObstructionFadeTicks);
    if (zoneState == STATE_IN_ZONE || zoneState == STATE_PROBING_ABOVE || zoneState == STATE_PROBING_BELOW) nearWeight = Math.max(nearWeight, 0.90);
    if (nearWeight <= 0.0) return null;

    double penalty = Math.max(0.0, w.forwardObstructionPenaltyWeight) * baseWeight * nearWeight * staleFactor;
    if (zoneState == STATE_IN_ZONE) penalty *= 1.12;
    else if (zoneState == STATE_PROBING_ABOVE || zoneState == STATE_PROBING_BELOW) penalty *= 1.06;
    if (aheadTicks <= 2.0) penalty *= 1.05;

    penalty = clamp(penalty, 0.0, 1.10);
    if (penalty <= 0.0) return null;
    return new ObstructionCandidate(label, aheadTicks, penalty, zoneState);
  }

  private ObstructionCandidate pickStronger(ObstructionCandidate current, ObstructionCandidate candidate) {
    if (candidate == null) return current;
    if (current == null) return candidate;
    if (candidate.penalty > current.penalty) return candidate;
    if (candidate.penalty == current.penalty && candidate.aheadTicks < current.aheadTicks) return candidate;
    return current;
  }

  private void rotateSessionIfNeeded(long sessionStartTime) {
    if (sessionStartTime <= 0L) return;
    if (activeSessionStartTime == Long.MIN_VALUE) {
      activeSessionStartTime = sessionStartTime;
      return;
    }
    if (activeSessionStartTime != sessionStartTime) {
      refStates.clear();
      activeSessionStartTime = sessionStartTime;
    }
  }

  private void applyRangeLocationSuitability(LzsMergedContextResult out, LzsSide side, LzsContextResult raw, LzsContextSnapshot snap, LzsContextConfig cfg) {
    double pct = out.sessionRangePct;
    out.locationApplicable = raw != null && raw.dayTypeSupportsFade && !Double.isNaN(pct);
    if (!out.locationApplicable) return;

    if (side == LzsSide.SHORT) {
      if (pct >= cfg.rangeUpperThirdPct) {
        double boost = 0.45;
        out.locationSuitabilityScore += boost;
        out.fadeSupport += 0.35;
        addReasonIf(out, true, "hi3");
      }
      else if (pct <= cfg.rangeLowerThirdPct) {
        double pen = 0.55;
        out.countertrendPenalty += pen;
        out.penaltyScore += pen;
        addReasonIf(out, true, "lo3bad");
      }
    }
    else {
      if (pct <= cfg.rangeLowerThirdPct) {
        double boost = 0.45;
        out.locationSuitabilityScore += boost;
        out.fadeSupport += 0.35;
        addReasonIf(out, true, "lo3");
      }
      else if (pct >= cfg.rangeUpperThirdPct) {
        double pen = 0.55;
        out.countertrendPenalty += pen;
        out.penaltyScore += pen;
        addReasonIf(out, true, "hi3bad");
      }
    }
  }

  private void applyTrendContinuationSuitability(LzsMergedContextResult out, LzsSide side, LzsContextResult raw, LzsContextSnapshot snap, LzsContextConfig cfg,
      double marketPrice, double tick, long now) {
    boolean trendUp = raw.dayTypeSupportsLong && !raw.dayTypeSupportsShort;
    boolean trendDown = raw.dayTypeSupportsShort && !raw.dayTypeSupportsLong;
    out.trendApplicable = trendUp || trendDown;
    if (!out.trendApplicable) return;

    double pct = out.sessionRangePct;

    boolean accAboveVwap = acceptedAbove(snap.sessionVwap, "VWAP", marketPrice, tick, now, cfg);
    boolean accBelowVwap = acceptedBelow(snap.sessionVwap, "VWAP", marketPrice, tick, now, cfg);
    boolean accAboveIbh = acceptedAbove(snap.ibHigh, "IBH", marketPrice, tick, now, cfg);
    boolean accBelowIbl = acceptedBelow(snap.ibLow, "IBL", marketPrice, tick, now, cfg);
    boolean accAboveOrh = acceptedAbove(snap.openingRangeHigh, "ORH", marketPrice, tick, now, cfg);
    boolean accBelowOrl = acceptedBelow(snap.openingRangeLow, "ORL", marketPrice, tick, now, cfg);

    if (trendUp) {
      if (side == LzsSide.LONG) {
        double boost = 0.0;
        if (accAboveVwap) { boost += 0.28; addReasonIf(out, true, "accUp"); }
        if (accAboveIbh) { boost += 0.32; addReasonIf(out, true, "accIBH"); }
        if (accAboveOrh) { boost += 0.24; addReasonIf(out, true, "accORH"); }
        if (!Double.isNaN(pct) && pct >= cfg.trendMidLowerPct) { boost += 0.18; addReasonIf(out, true, "contLoc"); }
        if (out.holdScore > out.rejectScore) { boost += 0.12; addReasonIf(out, true, "hldCont"); }
        out.acceptanceBiasScore += boost;
        out.trendContinuationSuitability += boost;
        out.locationSuitabilityScore += 0.35 * boost;
        out.continuationSupport += 0.55 * boost;
      }
      else {
        double pen = 0.0;
        if (accAboveVwap) { pen += 0.24; addReasonIf(out, true, "accUp"); }
        if (accAboveIbh) { pen += 0.28; addReasonIf(out, true, "accIBH"); }
        if (accAboveOrh) { pen += 0.20; addReasonIf(out, true, "accORH"); }
        if (!Double.isNaN(pct)) {
          if (pct <= cfg.rangeLowerThirdPct) { pen += 0.42; addReasonIf(out, true, "lo3bad"); }
          else if (pct <= cfg.trendMidUpperPct) { pen += 0.22; addReasonIf(out, true, "midBad"); }
        }
        if (!Double.isNaN(pct) && pct >= cfg.rangeUpperThirdPct && (out.rejectScore + out.extensionScore) >= 0.35) {
          pen -= 0.18;
          addReasonIf(out, true, "hiFade");
        }
        pen = Math.max(0.0, pen);
        out.countertrendPenalty += pen;
        out.penaltyScore += pen;
        addReasonIf(out, pen >= 0.20, "ctrPen");
      }
      return;
    }

    if (trendDown) {
      if (side == LzsSide.SHORT) {
        double boost = 0.0;
        if (accBelowVwap) { boost += 0.28; addReasonIf(out, true, "accDn"); }
        if (accBelowIbl) { boost += 0.32; addReasonIf(out, true, "accIBL"); }
        if (accBelowOrl) { boost += 0.24; addReasonIf(out, true, "accORL"); }
        if (!Double.isNaN(pct) && pct <= cfg.trendMidUpperPct) { boost += 0.18; addReasonIf(out, true, "contLoc"); }
        if (out.holdScore > out.reclaimScore) { boost += 0.12; addReasonIf(out, true, "hldCont"); }
        out.acceptanceBiasScore += boost;
        out.trendContinuationSuitability += boost;
        out.locationSuitabilityScore += 0.35 * boost;
        out.continuationSupport += 0.55 * boost;
      }
      else {
        double pen = 0.0;
        if (accBelowVwap) { pen += 0.24; addReasonIf(out, true, "accDn"); }
        if (accBelowIbl) { pen += 0.28; addReasonIf(out, true, "accIBL"); }
        if (accBelowOrl) { pen += 0.20; addReasonIf(out, true, "accORL"); }
        if (!Double.isNaN(pct)) {
          if (pct >= cfg.rangeUpperThirdPct) { pen += 0.42; addReasonIf(out, true, "hi3bad"); }
          else if (pct >= cfg.trendMidLowerPct) { pen += 0.22; addReasonIf(out, true, "midBad"); }
        }
        if (!Double.isNaN(pct) && pct <= cfg.rangeLowerThirdPct && (out.reclaimScore + out.extensionScore) >= 0.35) {
          pen -= 0.18;
          addReasonIf(out, true, "loFade");
        }
        pen = Math.max(0.0, pen);
        out.countertrendPenalty += pen;
        out.penaltyScore += pen;
        addReasonIf(out, pen >= 0.20, "ctrPen");
      }
    }
  }

  private boolean acceptedAbove(LzsReferenceValue ref, String label, double marketPrice, double tick, long now, LzsContextConfig cfg) {
    return classifyZoneState(ref, label, marketPrice, tick, now, cfg) == STATE_ACCEPTED_ABOVE;
  }

  private boolean acceptedBelow(LzsReferenceValue ref, String label, double marketPrice, double tick, long now, LzsContextConfig cfg) {
    return classifyZoneState(ref, label, marketPrice, tick, now, cfg) == STATE_ACCEPTED_BELOW;
  }

  private int classifyZoneState(LzsReferenceValue ref, String label, double marketPrice, double tick, long now, LzsContextConfig cfg) {
    if (ref == null || !ref.isReady() || Double.isNaN(ref.value) || Double.isNaN(marketPrice)) return STATE_IN_ZONE;
    LzsReferenceZoneProfile profile = zoneProfile(label, cfg);
    RefRoleState state = stateFor(label);
    updateZoneAcceptanceState(state, (marketPrice - ref.value) / tick, now, profile);
    return state.zoneState;
  }

  private LzsReferenceZoneProfile zoneProfile(String label, LzsContextConfig cfg) {
    return LzsReferenceZoneProfile.forLabel(label, cfg == null ? LzsContextConfig.defaults() : cfg);
  }

  private String zoneStateLabel(int zoneState) {
    switch (zoneState) {
      case STATE_ACCEPTED_ABOVE: return "accUp";
      case STATE_ACCEPTED_BELOW: return "accDn";
      case STATE_PROBING_ABOVE: return "probeUp";
      case STATE_PROBING_BELOW: return "probeDn";
      default: return "zone";
    }
  }

  private boolean isDayTypeApplicable(LzsContextSnapshot snap, LzsContextConfig cfg) {
    return cfg != null && cfg.enableDayTypeContext && snap != null && snap.dayType != null && snap.dayType.isApplicable();
  }

  private String fmtApplicable(boolean applicable, double value) {
    return applicable ? String.format(Locale.US, "%.2f", value) : "N/A";
  }

  private double computeSessionRangePct(LzsContextSnapshot snap) {
    if (snap == null || Double.isNaN(snap.lastPrice) || Double.isNaN(snap.sessionHigh) || Double.isNaN(snap.sessionLow)) return Double.NaN;
    double rng = snap.sessionHigh - snap.sessionLow;
    if (rng <= Math.max(1e-9, snap.tickSize)) return Double.NaN;
    return clamp((snap.lastPrice - snap.sessionLow) / rng, 0.0, 1.0);
  }

  private void applyRef(
      LzsMergedContextResult out,
      LzsSide side,
      LzsReferenceValue ref,
      String label,
      boolean upperRef,
      boolean lowerRef,
      boolean near,
      int proxTicks,
      double baseWeight,
      double marketPrice,
      double tick,
      long now,
      LzsContextConfig cfg) {
    if (ref == null || !ref.isReady() || Double.isNaN(ref.value) || Double.isNaN(marketPrice)) return;

    double distTicks = (marketPrice - ref.value) / tick;
    double absTicks = Math.abs(distTicks);
    LzsReferenceZoneProfile profile = zoneProfile(label, cfg);
    double distWeight = distanceWeight(Math.max(0.0, absTicks - profile.zoneHalfWidthTicks), cfg.referenceFullWeightDistanceTicks, cfg.referenceFadeDistanceTicks);
    if (distWeight <= 0.0 && !near) return;

    RefRoleState state = stateFor(label);
    updateRoleState(state, distTicks, now, cfg, profile);
    int zoneState = state.zoneState;

    double touchFresh = freshnessWeight(state.lastTouchMs, now, cfg.referenceInteractionFreshMs, cfg.referenceInteractionMaxAgeMs);
    double reclaimFresh = freshnessWeight(state.lastReclaimMs, now, cfg.referenceInteractionFreshMs, cfg.referenceInteractionMaxAgeMs);
    double rejectFresh = freshnessWeight(state.lastRejectMs, now, cfg.referenceInteractionFreshMs, cfg.referenceInteractionMaxAgeMs);
    double holdFresh = freshnessWeight(zoneState >= STATE_PROBING_ABOVE ? state.lastHoldAboveMs : state.lastHoldBelowMs, now, cfg.referenceInteractionFreshMs, cfg.referenceInteractionMaxAgeMs);
    double extFresh = freshnessWeight(zoneState >= STATE_PROBING_ABOVE ? state.lastExtensionAboveMs : state.lastExtensionBelowMs, now, cfg.referenceInteractionFreshMs, cfg.referenceInteractionMaxAgeMs);

    double zoneWeight = Math.max(0.75, distWeight);
    double probeWeight = Math.max(0.80, distWeight);
    double acceptWeight = Math.max(0.90, distWeight);
    double liveFresh = Math.max(touchFresh, Math.max(Math.max(reclaimFresh, rejectFresh), Math.max(holdFresh, extFresh)));
    out.freshnessScore += 0.25 * baseWeight * liveFresh;

    boolean acceptedAbove = zoneState == STATE_ACCEPTED_ABOVE;
    boolean acceptedBelow = zoneState == STATE_ACCEPTED_BELOW;
    boolean probingAbove = zoneState == STATE_PROBING_ABOVE;
    boolean probingBelow = zoneState == STATE_PROBING_BELOW;
    boolean inZone = zoneState == STATE_IN_ZONE;

    if (isNeutralRef(label)) {
      scoreNeutralRef(out, side, label, zoneState, baseWeight, Math.max(distWeight, inZone ? 0.75 : distWeight), reclaimFresh, rejectFresh, holdFresh, extFresh);
      addReasonIf(out, inZone && near, label + "zone");
      addReasonIf(out, probingAbove || probingBelow, label + "probe");
      addReasonIf(out, acceptedAbove || acceptedBelow, label + "acc");
      return;
    }

    if (side == LzsSide.LONG) {
      if (acceptedAbove) {
        double reclaim = baseWeight * acceptWeight * Math.max(reclaimFresh, 0.55);
        double hold = 0.78 * baseWeight * acceptWeight * Math.max(holdFresh, touchFresh);
        out.reclaimScore += reclaim;
        out.holdScore += hold;
        out.supportiveEdgeScore += 0.45 * baseWeight * acceptWeight;
        out.valueAlignmentScore += 0.12 * baseWeight * acceptWeight;
        addReasonIf(out, true, label + "acc");
        addReasonIf(out, reclaim >= 0.15, label + "rcl");
        addReasonIf(out, hold >= 0.15, label + "hld");
        if (upperRef && extFresh > 0.0) {
          double ext = 0.55 * baseWeight * extFresh * acceptWeight;
          out.extensionScore += ext;
          out.extremeLocationScore += 0.20 * ext;
          addReasonIf(out, ext >= 0.12, label + "ext");
        }
      }
      else if (inZone || probingAbove) {
        out.valueAlignmentScore += 0.22 * baseWeight * (inZone ? zoneWeight : probeWeight) * Math.max(touchFresh, 0.70);
        if (upperRef) {
          out.referenceConflictPenalty += 0.12 * baseWeight * (inZone ? 1.00 : 0.85);
        }
        addReasonIf(out, inZone && near, label + "zone");
        addReasonIf(out, probingAbove, label + "probe");
      }
      else if (probingBelow || acceptedBelow) {
        double adverse = acceptedBelow ? acceptWeight : probeWeight;
        double reject = baseWeight * adverse * Math.max(rejectFresh, 0.55);
        double severity = acceptedBelow ? 0.80 : 0.58;
        out.rejectScore += 0.30 * reject;
        out.referenceConflictPenalty += severity * baseWeight * adverse * Math.max(rejectFresh, 0.55);
        addReasonIf(out, probingBelow, label + "probe");
        addReasonIf(out, acceptedBelow, label + "acc");
        addReasonIf(out, reject >= 0.15, label + "rej");
      }
      else {
        out.valueAlignmentScore += 0.16 * baseWeight * Math.max(distWeight, touchFresh);
        addReasonIf(out, near, label + "tap");
      }
    }
    else { // SHORT
      if (acceptedBelow) {
        double reject = baseWeight * acceptWeight * Math.max(rejectFresh, 0.55);
        double hold = 0.78 * baseWeight * acceptWeight * Math.max(holdFresh, touchFresh);
        out.rejectScore += reject;
        out.holdScore += hold;
        out.supportiveEdgeScore += 0.45 * baseWeight * acceptWeight;
        out.valueAlignmentScore += 0.12 * baseWeight * acceptWeight;
        addReasonIf(out, true, label + "acc");
        addReasonIf(out, reject >= 0.15, label + "rej");
        addReasonIf(out, hold >= 0.15, label + "hld");
        if (lowerRef && extFresh > 0.0) {
          double ext = 0.55 * baseWeight * extFresh * acceptWeight;
          out.extensionScore += ext;
          out.extremeLocationScore += 0.20 * ext;
          addReasonIf(out, ext >= 0.12, label + "ext");
        }
      }
      else if (inZone || probingBelow) {
        out.valueAlignmentScore += 0.22 * baseWeight * (inZone ? zoneWeight : probeWeight) * Math.max(touchFresh, 0.70);
        if (lowerRef) {
          out.referenceConflictPenalty += 0.12 * baseWeight * (inZone ? 1.00 : 0.85);
        }
        addReasonIf(out, inZone && near, label + "zone");
        addReasonIf(out, probingBelow, label + "probe");
      }
      else if (probingAbove || acceptedAbove) {
        double adverse = acceptedAbove ? acceptWeight : probeWeight;
        double reclaim = baseWeight * adverse * Math.max(reclaimFresh, 0.55);
        double severity = acceptedAbove ? 0.80 : 0.58;
        out.reclaimScore += 0.30 * reclaim;
        out.referenceConflictPenalty += severity * baseWeight * adverse * Math.max(reclaimFresh, 0.55);
        addReasonIf(out, probingAbove, label + "probe");
        addReasonIf(out, acceptedAbove, label + "acc");
        addReasonIf(out, reclaim >= 0.15, label + "rcl");
      }
      else {
        out.valueAlignmentScore += 0.16 * baseWeight * Math.max(distWeight, touchFresh);
        addReasonIf(out, near, label + "tap");
      }
    }
  }

  private void scoreNeutralRef(
      LzsMergedContextResult out,
      LzsSide side,
      String label,
      int zoneState,
      double baseWeight,
      double distanceWeight,
      double reclaimFresh,
      double rejectFresh,
      double holdFresh,
      double extFresh) {
    boolean inZone = zoneState == STATE_IN_ZONE;
    boolean probingAbove = zoneState == STATE_PROBING_ABOVE;
    boolean probingBelow = zoneState == STATE_PROBING_BELOW;
    boolean acceptedAbove = zoneState == STATE_ACCEPTED_ABOVE;
    boolean acceptedBelow = zoneState == STATE_ACCEPTED_BELOW;

    out.valueAlignmentScore += (inZone ? 0.52 : 0.45) * baseWeight * distanceWeight;

    if (side == LzsSide.LONG) {
      if (acceptedAbove) {
        double reclaim = 0.58 * baseWeight * distanceWeight * Math.max(reclaimFresh, 0.55);
        double hold = 0.48 * baseWeight * distanceWeight * Math.max(holdFresh, 0.55);
        out.reclaimScore += reclaim;
        out.holdScore += hold;
        addReasonIf(out, true, label + "acc");
        addReasonIf(out, reclaim >= 0.10, label + "rcl");
        addReasonIf(out, hold >= 0.10, label + "hld");
      }
      else if (probingBelow || acceptedBelow) {
        double reject = 0.48 * baseWeight * distanceWeight * Math.max(rejectFresh, 0.55);
        out.referenceConflictPenalty += 0.30 * baseWeight * distanceWeight * Math.max(rejectFresh, 0.55);
        out.rejectScore += 0.18 * reject;
        addReasonIf(out, probingBelow, label + "probe");
        addReasonIf(out, acceptedBelow, label + "acc");
        addReasonIf(out, reject >= 0.10, label + "rej");
      }
    }
    else {
      if (acceptedBelow) {
        double reject = 0.58 * baseWeight * distanceWeight * Math.max(rejectFresh, 0.55);
        double hold = 0.48 * baseWeight * distanceWeight * Math.max(holdFresh, 0.55);
        out.rejectScore += reject;
        out.holdScore += hold;
        addReasonIf(out, true, label + "acc");
        addReasonIf(out, reject >= 0.10, label + "rej");
        addReasonIf(out, hold >= 0.10, label + "hld");
      }
      else if (probingAbove || acceptedAbove) {
        double reclaim = 0.48 * baseWeight * distanceWeight * Math.max(reclaimFresh, 0.55);
        out.referenceConflictPenalty += 0.30 * baseWeight * distanceWeight * Math.max(reclaimFresh, 0.55);
        out.reclaimScore += 0.18 * reclaim;
        addReasonIf(out, probingAbove, label + "probe");
        addReasonIf(out, acceptedAbove, label + "acc");
        addReasonIf(out, reclaim >= 0.10, label + "rcl");
      }
    }

    if (extFresh > 0.0) {
      double ext = 0.25 * baseWeight * extFresh * distanceWeight;
      out.extensionScore += ext;
      addReasonIf(out, ext >= 0.10, label + "ext");
    }
  }

  private void updateRoleState(RefRoleState state, double distTicks, long now, LzsContextConfig cfg, LzsReferenceZoneProfile profile) {
    if (state == null || profile == null) return;
    state.lastSeenMs = now;
    state.lastDistanceTicks = distTicks;

    int relation = classifyLegacyRelation(distTicks, cfg.referenceRoleTouchTicks);
    double absDist = Math.abs(distTicks);
    if (absDist <= Math.max(profile.zoneHalfWidthTicks, cfg.referenceRoleTouchTicks)) {
      state.lastTouchMs = now;
    }

    int prevZoneState = state.zoneState;
    updateZoneAcceptanceState(state, distTicks, now, profile);
    int newZoneState = state.zoneState;
    state.prevZoneState = prevZoneState;
    if (newZoneState != prevZoneState) {
      state.lastZoneStateChangeMs = now;
      if (newZoneState == STATE_IN_ZONE) state.lastTouchMs = now;
      if (isNegativeSideState(prevZoneState) && (newZoneState == STATE_IN_ZONE || isPositiveSideState(newZoneState))) {
        state.lastReclaimMs = now;
      }
      if (isPositiveSideState(prevZoneState) && (newZoneState == STATE_IN_ZONE || isNegativeSideState(newZoneState))) {
        state.lastRejectMs = now;
      }
    }

    if (state.zoneState == STATE_ACCEPTED_ABOVE && absDist <= Math.max(profile.acceptanceDistanceTicks, cfg.referenceFullWeightDistanceTicks + profile.zoneHalfWidthTicks)) {
      state.lastHoldAboveMs = now;
    }
    if (state.zoneState == STATE_ACCEPTED_BELOW && absDist <= Math.max(profile.acceptanceDistanceTicks, cfg.referenceFullWeightDistanceTicks + profile.zoneHalfWidthTicks)) {
      state.lastHoldBelowMs = now;
    }
    if ((state.zoneState == STATE_PROBING_ABOVE || state.zoneState == STATE_ACCEPTED_ABOVE) && absDist >= Math.max(profile.acceptanceDistanceTicks, cfg.referenceExtensionTicks)) {
      state.lastExtensionAboveMs = now;
    }
    if ((state.zoneState == STATE_PROBING_BELOW || state.zoneState == STATE_ACCEPTED_BELOW) && absDist >= Math.max(profile.acceptanceDistanceTicks, cfg.referenceExtensionTicks)) {
      state.lastExtensionBelowMs = now;
    }
    state.prevRelation = relation;
  }

  private RefRoleState stateFor(String label) {
    RefRoleState state = refStates.get(label);
    if (state == null) {
      state = new RefRoleState();
      refStates.put(label, state);
    }
    return state;
  }

  private void updateZoneAcceptanceState(RefRoleState state, double distTicks, long now, LzsReferenceZoneProfile profile) {
    double zoneHalf = profile.zoneHalfWidthTicks;
    double acceptDist = profile.acceptanceDistanceTicks;
    boolean inZone = Math.abs(distTicks) <= zoneHalf;
    if (inZone) {
      state.zoneState = STATE_IN_ZONE;
      state.probeAboveSinceMs = Long.MIN_VALUE;
      state.probeBelowSinceMs = Long.MIN_VALUE;
      state.acceptedAboveSinceMs = Long.MIN_VALUE;
      state.acceptedBelowSinceMs = Long.MIN_VALUE;
      return;
    }
    if (distTicks > zoneHalf) {
      state.probeBelowSinceMs = Long.MIN_VALUE;
      state.acceptedBelowSinceMs = Long.MIN_VALUE;
      if (state.probeAboveSinceMs == Long.MIN_VALUE) state.probeAboveSinceMs = now;
      if (distTicks >= acceptDist && (now - state.probeAboveSinceMs) >= profile.acceptanceHoldMs) {
        if (state.acceptedAboveSinceMs == Long.MIN_VALUE) state.acceptedAboveSinceMs = now;
        state.zoneState = STATE_ACCEPTED_ABOVE;
      }
      else {
        state.acceptedAboveSinceMs = Long.MIN_VALUE;
        state.zoneState = STATE_PROBING_ABOVE;
      }
      return;
    }
    state.probeAboveSinceMs = Long.MIN_VALUE;
    state.acceptedAboveSinceMs = Long.MIN_VALUE;
    if (state.probeBelowSinceMs == Long.MIN_VALUE) state.probeBelowSinceMs = now;
    if (distTicks <= -acceptDist && (now - state.probeBelowSinceMs) >= profile.acceptanceHoldMs) {
      if (state.acceptedBelowSinceMs == Long.MIN_VALUE) state.acceptedBelowSinceMs = now;
      state.zoneState = STATE_ACCEPTED_BELOW;
    }
    else {
      state.acceptedBelowSinceMs = Long.MIN_VALUE;
      state.zoneState = STATE_PROBING_BELOW;
    }
  }

  private boolean isPositiveSideState(int zoneState) {
    return zoneState == STATE_PROBING_ABOVE || zoneState == STATE_ACCEPTED_ABOVE;
  }

  private boolean isNegativeSideState(int zoneState) {
    return zoneState == STATE_PROBING_BELOW || zoneState == STATE_ACCEPTED_BELOW;
  }

  private int classifyLegacyRelation(double distTicks, int touchTicks) {
    double tol = Math.max(0.5, touchTicks);
    if (distTicks > tol) return 1;
    if (distTicks < -tol) return -1;
    return 0;
  }

  private double distanceWeight(double absDistTicks, int fullWeightTicks, int fadeTicks) {
    double full = Math.max(0.0, fullWeightTicks);
    double fade = Math.max(full + 1.0, fadeTicks);
    if (absDistTicks <= full) return 1.0;
    if (absDistTicks >= fade) return 0.0;
    return 1.0 - ((absDistTicks - full) / (fade - full));
  }

  private double freshnessWeight(long eventMs, long now, long freshMs, long maxAgeMs) {
    if (eventMs <= 0L || now <= 0L || now < eventMs) return 0.0;
    long age = now - eventMs;
    long fresh = Math.max(0L, freshMs);
    long maxAge = Math.max(fresh + 1L, maxAgeMs);
    if (age <= fresh) return 1.0;
    if (age >= maxAge) return 0.0;
    return 1.0 - ((double) (age - fresh) / (double) (maxAge - fresh));
  }

  private boolean isNeutralRef(String label) {
    return "VWAP".equals(label) || "POC".equals(label) || "OPEN".equals(label);
  }

  private double scoreDayType(LzsSide side, LzsContextResult raw, LzsContextSnapshot snap, LzsContextConfig cfg) {
    if (snap.dayType == null || !snap.dayType.hasSignal()) return 0.0;
    if (!Double.isNaN(snap.dayType.confidence) && snap.dayType.confidence < Math.max(0.0, cfg.dayTypeMinConfidence)) {
      return 0.0;
    }
    double base = 0.0;
    if (side == LzsSide.LONG && raw.dayTypeSupportsLong) base = 1.0;
    else if (side == LzsSide.SHORT && raw.dayTypeSupportsShort) base = 1.0;
    else if (raw.dayTypeSupportsFade) base = 0.65;
    return base * Math.max(0.0, cfg.dayTypeScoreWeight);
  }

  private LzsContextIntent inferIntent(LzsSide side, LzsMergedContextResult out, LzsContextResult raw) {
    boolean dtCont = (side == LzsSide.LONG && raw.dayTypeSupportsLong) || (side == LzsSide.SHORT && raw.dayTypeSupportsShort);
    boolean dtFade = raw.dayTypeSupportsFade;

    if ((dtCont && out.continuationSupport >= out.fadeSupport) || out.continuationSupport >= (out.fadeSupport + 0.20)) {
      return LzsContextIntent.CONTINUATION;
    }
    if ((dtFade && out.fadeSupport >= out.continuationSupport) || out.fadeSupport >= (out.continuationSupport + 0.20)) {
      return LzsContextIntent.FADE;
    }
    return LzsContextIntent.NEUTRAL;
  }

  private boolean hasOppositeBias(LzsSide side, LzsContextSnapshot snap) {
    if (snap == null || snap.dayType == null || !snap.dayType.hasSignal()) return false;
    if (side == LzsSide.LONG) return snap.dayType.supportsShortContinuation && !snap.dayType.supportsLongContinuation;
    return snap.dayType.supportsLongContinuation && !snap.dayType.supportsShortContinuation;
  }

  private double max5(double a, double b, double c, double d, double e) {
    return Math.max(a, Math.max(b, Math.max(c, Math.max(d, e))));
  }

  private String dayTypeReason(LzsSide side, LzsContextResult raw) {
    if (side == LzsSide.LONG && raw.dayTypeSupportsLong) return "DT_UP";
    if (side == LzsSide.SHORT && raw.dayTypeSupportsShort) return "DT_DN";
    if (raw.dayTypeSupportsFade) return "DT_BAL";
    return "DT";
  }

  private double mid(LzsZoneCandidate candidate) {
    if (candidate == null || !candidate.valid) return Double.NaN;
    return (candidate.zoneLow + candidate.zoneHigh) * 0.5;
  }

  private double calibrate(double raw, double cap) {
    return clamp(raw, 0.0, cap);
  }

  private double clamp(double v, double lo, double hi) {
    return Math.max(lo, Math.min(hi, v));
  }

  private void addPriorityReasonIf(LzsMergedContextResult out, boolean cond, String reason) {
    if (!cond || reason == null || reason.length() == 0) return;
    if (out.reasons.contains(reason)) return;
    out.reasons.add(0, reason);
  }

  private void addReasonIf(LzsMergedContextResult out, boolean cond, String reason) {
    if (!cond || reason == null || reason.length() == 0) return;
    if (!out.reasons.contains(reason)) out.reasons.add(reason);
  }
}
