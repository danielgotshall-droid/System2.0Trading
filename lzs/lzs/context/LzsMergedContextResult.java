package study_examples.lzs.context;

import java.util.ArrayList;
import java.util.List;

import study_examples.lzs.model.LzsSide;

public final class LzsMergedContextResult {
  public LzsSide side;
  public LzsContextIntent intent = LzsContextIntent.NEUTRAL;

  public double sideScore;
  public double structuralScore;
  public double sessionReferenceScore;
  public double dayTypeScore;
  public double penaltyScore;
  public double continuationSupport;
  public double fadeSupport;

  public double supportiveEdgeScore;
  public double valueAlignmentScore;
  public double extremeLocationScore;
  public double reclaimScore;
  public double rejectScore;
  public double holdScore;
  public double extensionScore;
  public double freshnessScore;
  public double referenceConflictPenalty;

  public double sessionRangePct = Double.NaN;
  public double locationSuitabilityScore;
  public double trendContinuationSuitability;
  public double countertrendPenalty;
  public double acceptanceBiasScore;
  public double forwardObstructionScore;
  public String nearestOpposingReference = "";
  public double nearestOpposingDistanceTicks = Double.NaN;
  public String nearestOpposingZoneState = "";

  public boolean dayTypeApplicable;
  public boolean locationApplicable;
  public boolean trendApplicable;
  public double activeWeightSum;
  public double normalizationFactor = 1.0;

  public double mergedFilterThreshold = Double.NaN;
  public double structuralFilterThreshold = Double.NaN;
  public double dayTypeFilterThreshold = Double.NaN;

  public boolean supportsSide;
  public boolean supportsContinuation;
  public boolean supportsFade;
  public boolean freeFloating;
  public boolean oppositeBias;
  public boolean passesFilter = true;
  public String filterReason = "";

  public String summary = "";
  public String debugText = "";
  public final List<String> reasons = new ArrayList<String>();

  public String buildReasonSummary() {
    return buildReasonSummary(reasons.size());
  }

  public String buildReasonSummary(int maxReasons) {
    if (reasons.isEmpty() || maxReasons <= 0) return "";
    StringBuilder sb = new StringBuilder();
    int lim = Math.min(maxReasons, reasons.size());
    for (int i = 0; i < lim; i++) {
      if (i > 0) sb.append("/");
      sb.append(reasons.get(i));
    }
    return sb.toString();
  }
}
