package study_examples.lzs.util;

import study_examples.lzs.context.LzsContextConfig;
import study_examples.lzs.context.LzsContextMode;
import study_examples.lzs.context.LzsContextResult;
import study_examples.lzs.context.LzsContextSnapshot;
import study_examples.lzs.context.LzsHudDisplayMode;
import study_examples.lzs.context.LzsMergedContextResult;
import study_examples.lzs.context.LzsReferenceValue;
import study_examples.lzs.model.LzsConfig;
import study_examples.lzs.model.LzsSideState;

public final class LzsFormatUtils {
  private LzsFormatUtils() {}

  public static String fmt1(double v) {
    if (Double.isNaN(v)) return "NA";
    return String.format(java.util.Locale.US, "%.1f", v);
  }

  public static String fmt2(double v) {
    if (Double.isNaN(v)) return "NA";
    return String.format(java.util.Locale.US, "%.2f", v);
  }

  public static String buildGlobalContextLine(LzsContextSnapshot snap, LzsContextConfig cfg) {
    if (snap == null || !snap.inRthSession) return "CTX | n/a";
    LzsHudDisplayMode mode = cfg == null ? LzsHudDisplayMode.STANDARD : cfg.hudDisplayMode;
    StringBuilder sb = new StringBuilder();
    sb.append("CTX | ");
    if (cfg != null && cfg.showDayTypeOnHud && snap.dayType != null) {
      if (snap.dayType.isApplicable()) {
        sb.append("DT ").append(snap.dayType.state.name());
        if (!Double.isNaN(snap.dayType.confidence)) sb.append(" ").append(fmt1(snap.dayType.confidence));
      }
      else if (!snap.dayType.ibComplete) {
        sb.append("DT N/A[IB]");
      }
      else if (!snap.dayType.historicalNormReady) {
        sb.append("DT N/A[HIST]");
      }
      else {
        sb.append("DT N/A");
      }
      if (snap.dayType.manualAidUsed) sb.append("[MA]");
      else if (snap.dayType.lookbackSessionsUsed >= 0) sb.append("[L").append(snap.dayType.lookbackSessionsUsed).append("]");
      sb.append(" | ");
    }
    appendRef(sb, "Open", snap.sessionOpen, cfg, true);
    appendRef(sb, "VWAP", snap.sessionVwap, cfg, false);
    if (mode != LzsHudDisplayMode.COMPACT) {
      appendRef(sb, "PDH", snap.priorDayHigh, cfg, false);
      appendRef(sb, "PDL", snap.priorDayLow, cfg, false);
      appendRef(sb, "PDC", snap.priorDayClose, cfg, false);
      appendRef(sb, "ONH", snap.overnightHigh, cfg, false);
      appendRef(sb, "ONL", snap.overnightLow, cfg, false);
      appendRef(sb, "ORH", snap.openingRangeHigh, cfg, false);
      appendRef(sb, "ORL", snap.openingRangeLow, cfg, false);
      appendRef(sb, "IBH", snap.ibHigh, cfg, false);
      appendRef(sb, "IBL", snap.ibLow, cfg, false);
      appendRef(sb, "VAH", snap.priorValueAreaHigh, cfg, false);
      appendRef(sb, "VAL", snap.priorValueAreaLow, cfg, false);
      appendRef(sb, "POC", snap.priorPoc, cfg, false);
    }
    return sb.toString();
  }

  private static void appendRef(StringBuilder sb, String label, LzsReferenceValue ref, LzsContextConfig cfg, boolean first) {
    if (sb == null || ref == null) return;
    boolean showDev = cfg == null || cfg.showDevelopingReferencesOnHud;
    if (!ref.isReady() && !(showDev && ref.isDeveloping())) return;
    if (!first) sb.append(" | ");
    sb.append(label).append(" ").append(fmt2(ref.value));
    if (cfg != null && cfg.showReferenceSourcesOnHud) {
      sb.append("[").append(ref.sourceTag());
      if (ref.isDeveloping()) sb.append("*");
      sb.append("]");
    }
    else if (ref.isDeveloping()) {
      sb.append("*");
    }
  }


  private static String contextVerdictLabel(LzsMergedContextResult merged, LzsContextConfig cfg) {
    if (cfg == null || cfg.contextMode != LzsContextMode.OPTIONAL_FILTER) return "N/A";
    if (merged == null) return "N/A";
    return merged.passesFilter ? "PASS" : "BLOCK";
  }

  private static String filterReasonLabel(LzsMergedContextResult merged) {
    if (merged == null) return "N/A";
    if (merged.passesFilter) return "OK";
    if (merged.filterReason == null || merged.filterReason.length() == 0) return "NO";
    if ("mrg".equals(merged.filterReason)) return "mrg";
    if ("str".equals(merged.filterReason)) return "str";
    if ("dt".equals(merged.filterReason)) return "dt";
    if ("side".equals(merged.filterReason)) return "side";
    if ("free".equals(merged.filterReason)) return "free";
    return merged.filterReason;
  }
  public static String buildHudSide(LzsSideState state, LzsConfig cfg) {
    return buildHudSide(state, cfg, null, null, null);
  }

  public static String buildHudSide(LzsSideState state, LzsConfig cfg, LzsContextResult context, LzsMergedContextResult merged, LzsContextConfig ctxCfg) {
    if (state == null) return "";
    LzsHudDisplayMode mode = ctxCfg == null ? LzsHudDisplayMode.STANDARD : ctxCfg.hudDisplayMode;
    StringBuilder sb = new StringBuilder();
    sb.append(state.side.name()).append(" | ").append(state.interaction.phase);

    if (mode != LzsHudDisplayMode.COMPACT && state.candidate != null && state.candidate.valid) {
      sb.append("\n")
        .append(fmt2(state.candidate.zoneLow)).append("-")
        .append(fmt2(state.candidate.zoneHigh))
        .append(" | Anch ").append(fmt2(state.candidate.anchorPrice))
        .append(" | Dist ").append(fmt1(state.candidate.distanceFromPriceTicks)).append("t")
        .append(" | Lock ").append(state.activeLifecycleLocked ? "Y" : "N");
    }

    if (mode != LzsHudDisplayMode.COMPACT && (cfg == null || cfg.showCandidateMetrics)) {
      sb.append("\nExec ").append(fmt1(state.interaction.execSameSideVol))
        .append(" | Bub ").append(state.interaction.bubbleCount)
        .append(" | Agg ").append(fmt2(state.interaction.aggressionShare))
        .append(" | Ref ").append(fmt2(state.interaction.reversalRefPrice))
        .append(" | Rev ").append(fmt1(state.interaction.reversalTicks)).append("t");
      if (!Double.isNaN(state.interaction.execBandLow) && !Double.isNaN(state.interaction.execBandHigh) && mode == LzsHudDisplayMode.DEBUG) {
        sb.append("\nBand ").append(fmt2(state.interaction.execBandLow))
          .append("-").append(fmt2(state.interaction.execBandHigh))
          .append(" | Rem ").append(fmt2(state.interaction.remainingZonePct))
          .append(" | Rel ").append(fmt2(state.interaction.reloadPct))
          .append(" | Path ").append(fmt1(state.interaction.pathClearTicks)).append("t");
      }
    }

    if (ctxCfg != null && ctxCfg.showContextOnHud) {
      if (ctxCfg.showMergedContextOnHud && merged != null && merged.summary != null && merged.summary.length() > 0) {
        sb.append("\n").append(merged.summary);
        if (ctxCfg.showFilterStatusOnHud) {
          sb.append(" | CTX ").append(contextVerdictLabel(merged, ctxCfg));
          if (ctxCfg.contextMode == LzsContextMode.OPTIONAL_FILTER) {
            sb.append(" | FLT ").append(filterReasonLabel(merged));
          }
        }
        if (mode == LzsHudDisplayMode.DEBUG && merged.debugText != null && merged.debugText.length() > 0) {
          sb.append("\n").append(merged.debugText);
        }
      }
      else if (context != null) {
        sb.append("\n").append(context.summary == null || context.summary.length() == 0 ? "CTX FREE" : context.summary);
        if (ctxCfg.showFilterStatusOnHud) {
          sb.append(" | CTX ").append(ctxCfg.contextMode == LzsContextMode.OPTIONAL_FILTER ? "N/A" : "N/A");
        }
        if (mode == LzsHudDisplayMode.DEBUG && context.debugText != null && context.debugText.length() > 0) {
          sb.append("\n").append(context.debugText);
        }
      }
    }

    if (mode == LzsHudDisplayMode.DEBUG && cfg != null && cfg.showPhaseDetails && state.interaction.debug != null && state.interaction.debug.length() > 0) {
      sb.append("\n").append(state.interaction.debug);
    }
    return sb.toString();
  }
}
