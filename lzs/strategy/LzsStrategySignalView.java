package study_examples.lzs.strategy;

import com.motivewave.platform.sdk.common.DataSeries;

import study_examples.lzs.LzsStudyValues;
import study_examples.lzs.context.LzsContextIntent;
import study_examples.lzs.model.LzsPhase;

/**
 * Normalized strategy-facing snapshot of the study's exported state.
 *
 * The strategy reads the study; it should not duplicate the study's internal
 * detection logic. This class keeps the read surface explicit and stable.
 */
public class LzsStrategySignalView {
  public int index;
  public LzsPhase longPhase;
  public LzsPhase shortPhase;
  public double longContextScore;
  public double shortContextScore;
  public boolean longContextPass;
  public boolean shortContextPass;
  public LzsContextIntent longIntent;
  public LzsContextIntent shortIntent;
  public double longExecRef;
  public double shortExecRef;
  public double longPathClearTicks;
  public double shortPathClearTicks;
  public double longZoneLow;
  public double longZoneHigh;
  public double shortZoneLow;
  public double shortZoneHigh;
  public double longSignalZoneLow;
  public double longSignalZoneHigh;
  public double shortSignalZoneLow;
  public double shortSignalZoneHigh;

  public static LzsStrategySignalView from(DataSeries series, int index) {
    LzsStrategySignalView v = new LzsStrategySignalView();
    v.index = index;
    v.longPhase = decodePhase(d(series.getDouble(index, LzsStudyValues.LONG_PHASE)));
    v.shortPhase = decodePhase(d(series.getDouble(index, LzsStudyValues.SHORT_PHASE)));
    v.longContextScore = d(series.getDouble(index, LzsStudyValues.LONG_CONTEXT_SCORE));
    v.shortContextScore = d(series.getDouble(index, LzsStudyValues.SHORT_CONTEXT_SCORE));
    v.longContextPass = b(series.getBoolean(index, LzsStudyValues.LONG_CONTEXT_PASS));
    v.shortContextPass = b(series.getBoolean(index, LzsStudyValues.SHORT_CONTEXT_PASS));
    v.longIntent = decodeIntent(d(series.getDouble(index, LzsStudyValues.LONG_CONTEXT_INTENT)));
    v.shortIntent = decodeIntent(d(series.getDouble(index, LzsStudyValues.SHORT_CONTEXT_INTENT)));
    v.longExecRef = d(series.getDouble(index, LzsStudyValues.LONG_SIGNAL_EXEC_REF));
    if (Double.isNaN(v.longExecRef)) v.longExecRef = d(series.getDouble(index, LzsStudyValues.LONG_EXEC_REF));
    v.shortExecRef = d(series.getDouble(index, LzsStudyValues.SHORT_SIGNAL_EXEC_REF));
    if (Double.isNaN(v.shortExecRef)) v.shortExecRef = d(series.getDouble(index, LzsStudyValues.SHORT_EXEC_REF));
    v.longPathClearTicks = d(series.getDouble(index, LzsStudyValues.LONG_PATH_CLEAR));
    v.shortPathClearTicks = d(series.getDouble(index, LzsStudyValues.SHORT_PATH_CLEAR));
    v.longSignalZoneLow = d(series.getDouble(index, LzsStudyValues.LONG_SIGNAL_ZONE_LOW));
    v.longSignalZoneHigh = d(series.getDouble(index, LzsStudyValues.LONG_SIGNAL_ZONE_HIGH));
    v.shortSignalZoneLow = d(series.getDouble(index, LzsStudyValues.SHORT_SIGNAL_ZONE_LOW));
    v.shortSignalZoneHigh = d(series.getDouble(index, LzsStudyValues.SHORT_SIGNAL_ZONE_HIGH));
    v.longZoneLow = !Double.isNaN(v.longSignalZoneLow) ? v.longSignalZoneLow : d(series.getDouble(index, LzsStudyValues.LONG_ZONE_LOW));
    v.longZoneHigh = !Double.isNaN(v.longSignalZoneHigh) ? v.longSignalZoneHigh : d(series.getDouble(index, LzsStudyValues.LONG_ZONE_HIGH));
    v.shortZoneLow = !Double.isNaN(v.shortSignalZoneLow) ? v.shortSignalZoneLow : d(series.getDouble(index, LzsStudyValues.SHORT_ZONE_LOW));
    v.shortZoneHigh = !Double.isNaN(v.shortSignalZoneHigh) ? v.shortSignalZoneHigh : d(series.getDouble(index, LzsStudyValues.SHORT_ZONE_HIGH));
    return v;
  }

  public boolean isLongSignalReady(int minPhaseOrdinal) {
    return longPhase != null && longPhase.ordinal() >= minPhaseOrdinal;
  }

  public boolean isShortSignalReady(int minPhaseOrdinal) {
    return shortPhase != null && shortPhase.ordinal() >= minPhaseOrdinal;
  }

  private static double d(Double v) { return v == null ? Double.NaN : v.doubleValue(); }

  private static boolean b(Boolean v) { return v != null && v.booleanValue(); }

  private static LzsPhase decodePhase(double ordinal) {
    if (Double.isNaN(ordinal)) return null;
    int i = (int) Math.round(ordinal);
    LzsPhase[] values = LzsPhase.values();
    return i >= 0 && i < values.length ? values[i] : null;
  }

  private static LzsContextIntent decodeIntent(double ordinal) {
    if (Double.isNaN(ordinal)) return null;
    int i = (int) Math.round(ordinal);
    LzsContextIntent[] values = LzsContextIntent.values();
    return i >= 0 && i < values.length ? values[i] : null;
  }
}
