package study_examples.lzs.context;

import java.util.Locale;

import study_examples.lzs.model.LzsSide;
import study_examples.lzs.model.LzsZoneCandidate;

public final class LzsContextEngine {

  public LzsContextResult evaluate(LzsSide side, LzsZoneCandidate candidate, LzsContextSnapshot snap, LzsContextConfig cfg) {
    LzsContextResult out = new LzsContextResult();
    if (side == null || snap == null || cfg == null || !cfg.enableContext || !snap.inRthSession) {
      out.summary = "CTX OFF";
      return out;
    }

    double tick = Math.max(1e-9, snap.tickSize);
    double refPrice = candidate != null && candidate.valid
        ? (candidate.zoneLow + candidate.zoneHigh) * 0.5
        : snap.lastPrice;

    if (cfg.enableStructuralRefs) {
      if (isNear(refPrice, snap.sessionOpen, tick, cfg.structuralProximityTicks)) {
        out.nearSessionOpen = true;
        out.structuralScore += 1.0;
        out.reasons.add("OPEN");
      }
      if (side == LzsSide.LONG && isNear(refPrice, snap.priorDayLow, tick, cfg.structuralProximityTicks)) {
        out.nearPriorDayLow = true;
        out.structuralScore += 1.0;
        out.reasons.add("PDL");
      }
      if (side == LzsSide.SHORT && isNear(refPrice, snap.priorDayHigh, tick, cfg.structuralProximityTicks)) {
        out.nearPriorDayHigh = true;
        out.structuralScore += 1.0;
        out.reasons.add("PDH");
      }
    }

    if (cfg.enableOvernightRefs) {
      if (side == LzsSide.LONG && isNear(refPrice, snap.overnightLow, tick, cfg.overnightProximityTicks)) {
        out.nearOvernightLow = true;
        out.overnightScore += 1.0;
        out.reasons.add("ONL");
      }
      if (side == LzsSide.SHORT && isNear(refPrice, snap.overnightHigh, tick, cfg.overnightProximityTicks)) {
        out.nearOvernightHigh = true;
        out.overnightScore += 1.0;
        out.reasons.add("ONH");
      }
    }

    if (cfg.enableVwapRef && isNear(refPrice, snap.sessionVwap, tick, cfg.vwapProximityTicks)) {
      out.nearVwap = true;
      out.vwapScore += 1.0;
      out.reasons.add("VWAP");
    }

    if (cfg.enableIbRefs) {
      if (side == LzsSide.LONG && isNear(refPrice, snap.ibLow, tick, cfg.ibProximityTicks)) {
        out.nearIbLow = true;
        out.ibScore += snap.ibLow.isReady() ? 1.0 : 0.0;
        if (snap.ibLow.isReady()) out.reasons.add("IBL");
      }
      if (side == LzsSide.SHORT && isNear(refPrice, snap.ibHigh, tick, cfg.ibProximityTicks)) {
        out.nearIbHigh = true;
        out.ibScore += snap.ibHigh.isReady() ? 1.0 : 0.0;
        if (snap.ibHigh.isReady()) out.reasons.add("IBH");
      }
    }

    if (cfg.enableOrRefs) {
      if (side == LzsSide.LONG && isNear(refPrice, snap.openingRangeLow, tick, cfg.openingRangeProximityTicks)) {
        out.nearOpeningRangeLow = true;
        out.openingRangeScore += snap.openingRangeLow.isReady() ? 1.0 : 0.0;
        if (snap.openingRangeLow.isReady()) out.reasons.add("ORL");
      }
      if (side == LzsSide.SHORT && isNear(refPrice, snap.openingRangeHigh, tick, cfg.openingRangeProximityTicks)) {
        out.nearOpeningRangeHigh = true;
        out.openingRangeScore += snap.openingRangeHigh.isReady() ? 1.0 : 0.0;
        if (snap.openingRangeHigh.isReady()) out.reasons.add("ORH");
      }
    }

    if (cfg.enableValueAreaRefs) {
      if (side == LzsSide.LONG && isNear(refPrice, snap.priorValueAreaLow, tick, cfg.valueAreaProximityTicks)) {
        out.nearPriorValueAreaLow = true;
        out.valueAreaScore += 1.0;
        out.reasons.add("VAL");
      }
      if (side == LzsSide.SHORT && isNear(refPrice, snap.priorValueAreaHigh, tick, cfg.valueAreaProximityTicks)) {
        out.nearPriorValueAreaHigh = true;
        out.valueAreaScore += 1.0;
        out.reasons.add("VAH");
      }
      if (isNear(refPrice, snap.priorPoc, tick, cfg.valueAreaProximityTicks)) {
        out.nearPriorPoc = true;
        out.valueAreaScore += 0.5;
        out.reasons.add("POC");
      }
    }

    if (cfg.enableDayTypeContext && snap.dayType != null && snap.dayType.hasSignal()) {
      out.dayTypeState = snap.dayType.state.name();
      out.dayTypeConfidence = snap.dayType.confidence;
      out.dayTypeSupportsLong = snap.dayType.supportsLongContinuation;
      out.dayTypeSupportsShort = snap.dayType.supportsShortContinuation;
      out.dayTypeSupportsFade = snap.dayType.supportsFade;
      double weight = Math.max(0.0, cfg.dayTypeScoreWeight);
      if (side == LzsSide.LONG && snap.dayType.supportsLongContinuation) {
        out.dayTypeScore += weight;
        out.reasons.add("DT_UP");
      }
      else if (side == LzsSide.SHORT && snap.dayType.supportsShortContinuation) {
        out.dayTypeScore += weight;
        out.reasons.add("DT_DN");
      }
      else if (snap.dayType.supportsFade) {
        out.dayTypeScore += 0.5 * weight;
        out.reasons.add("DT_BAL");
      }
    }

    out.totalScore = out.structuralScore + out.overnightScore + out.vwapScore + out.ibScore + out.openingRangeScore + out.valueAreaScore + out.dayTypeScore;
    out.freeFloating = out.totalScore <= 0.0;

    String reasonSummary = out.buildReasonSummary();
    out.summary = out.freeFloating
        ? "CTX FREE"
        : String.format(Locale.US, "CTX %.1f %s", out.totalScore, reasonSummary);

    out.debugText = String.format(
        Locale.US,
        "Ref %.2f | Open %s | PDH %s | PDL %s | ONH %s | ONL %s | VWAP %s | OR %s/%s | IB %s/%s | VAH %s | VAL %s | POC %s | DT %s %.1f | score %.1f",
        refPrice,
        fmtRef(snap.sessionOpen),
        fmtRef(snap.priorDayHigh),
        fmtRef(snap.priorDayLow),
        fmtRef(snap.overnightHigh),
        fmtRef(snap.overnightLow),
        fmtRef(snap.sessionVwap),
        fmtRef(snap.openingRangeLow),
        fmtRef(snap.openingRangeHigh),
        fmtRef(snap.ibLow),
        fmtRef(snap.ibHigh),
        fmtRef(snap.priorValueAreaHigh),
        fmtRef(snap.priorValueAreaLow),
        fmtRef(snap.priorPoc),
        out.dayTypeState,
        out.dayTypeConfidence,
        out.totalScore);
    return out;
  }

  private boolean isNear(double price, LzsReferenceValue ref, double tick, int proxTicks) {
    if (ref == null || !ref.isReady()) return false;
    if (Double.isNaN(price) || Double.isNaN(ref.value)) return false;
    return Math.abs(price - ref.value) / tick <= Math.max(0, proxTicks);
  }

  private String fmtRef(LzsReferenceValue ref) {
    if (ref == null || Double.isNaN(ref.value)) return "NA";
    return String.format(Locale.US, "%.2f[%s/%s]", ref.value, ref.sourceTag(), ref.availability.name().charAt(0));
  }
}
