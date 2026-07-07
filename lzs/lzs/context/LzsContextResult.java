package study_examples.lzs.context;

import java.util.ArrayList;
import java.util.List;

public final class LzsContextResult {
  public double structuralScore;
  public double overnightScore;
  public double vwapScore;
  public double ibScore;
  public double openingRangeScore;
  public double valueAreaScore;
  public double dayTypeScore;
  public double totalScore;
  public boolean freeFloating = true;

  public boolean nearSessionOpen;
  public boolean nearPriorDayHigh;
  public boolean nearPriorDayLow;
  public boolean nearOvernightHigh;
  public boolean nearOvernightLow;
  public boolean nearVwap;
  public boolean nearIbHigh;
  public boolean nearIbLow;
  public boolean nearOpeningRangeHigh;
  public boolean nearOpeningRangeLow;
  public boolean nearPriorValueAreaHigh;
  public boolean nearPriorValueAreaLow;
  public boolean nearPriorPoc;
  public boolean dayTypeSupportsLong;
  public boolean dayTypeSupportsShort;
  public boolean dayTypeSupportsFade;
  public String dayTypeState = "UNKNOWN";
  public double dayTypeConfidence = Double.NaN;

  public String summary = "";
  public String debugText = "";
  public final List<String> reasons = new ArrayList<String>();

  public String buildReasonSummary() {
    if (reasons.isEmpty()) return freeFloating ? "FREE" : "";
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < reasons.size(); i++) {
      if (i > 0) sb.append("|");
      sb.append(reasons.get(i));
    }
    return sb.toString();
  }
}
