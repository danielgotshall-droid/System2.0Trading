package study_examples.lzs.util;

import study_examples.lzs.model.*;

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

  public static String buildHudSide(LzsSideState state, LzsConfig cfg) {
    if (state == null) return "";
    StringBuilder sb = new StringBuilder();
    sb.append(state.side.name()).append(" | ").append(state.interaction.phase);

    if (state.candidate != null && state.candidate.valid) {
      sb.append("\n")
        .append(fmt2(state.candidate.zoneLow)).append("-")
        .append(fmt2(state.candidate.zoneHigh))
        .append(" | Anch ").append(fmt2(state.candidate.anchorPrice))
        .append(" | Dist ").append(fmt1(state.candidate.distanceFromPriceTicks)).append("t")
        .append(" | Lock ").append(state.activeLifecycleLocked ? "Y" : "N");
    }

    if (cfg == null || cfg.showCandidateMetrics) {
      sb.append("\nExec ").append(fmt1(state.interaction.execSameSideVol))
        .append(" | Bub ").append(state.interaction.bubbleCount)
        .append(" | Agg ").append(fmt2(state.interaction.aggressionShare))
        .append(" | Ref ").append(fmt2(state.interaction.reversalRefPrice))
        .append(" | Rev ").append(fmt1(state.interaction.reversalTicks)).append("t");
      if (!Double.isNaN(state.interaction.execBandLow) && !Double.isNaN(state.interaction.execBandHigh)) {
        sb.append("\nBand ").append(fmt2(state.interaction.execBandLow))
          .append("-").append(fmt2(state.interaction.execBandHigh))
          .append(" | Rem ").append(fmt2(state.interaction.remainingZonePct))
          .append(" | Rel ").append(fmt2(state.interaction.reloadPct))
          .append(" | Path ").append(fmt1(state.interaction.pathClearTicks)).append("t");
      }
    }

    if (cfg != null && cfg.showPhaseDetails && state.interaction.debug != null && state.interaction.debug.length() > 0) {
      sb.append("\n").append(state.interaction.debug);
    }
    return sb.toString();
  }
}
