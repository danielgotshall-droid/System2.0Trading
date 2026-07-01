package study_examples.lzs.context;

import study_examples.lzs.session.SessionDayTypeHelper.DayTypeState;

public final class LzsDayTypeContext {
  public DayTypeState state = DayTypeState.UNKNOWN;
  public DayTypeState archetypeState = DayTypeState.UNKNOWN;
  public DayTypeState liveState = DayTypeState.UNKNOWN;
  public boolean ibComplete;
  public boolean ready;
  public boolean applicable;
  public double confidence = Double.NaN;
  public double trendUpScore = Double.NaN;
  public double trendDownScore = Double.NaN;
  public double rangeScore = Double.NaN;
  public double liquidationScore = Double.NaN;
  public boolean supportsLongContinuation;
  public boolean supportsShortContinuation;
  public boolean supportsFade;
  public boolean manualAidUsed;
  public boolean historicalNormReady;
  public int lookbackSessionsUsed = -1;
  public String summary = "";
  public String reasons = "";
  public String debugText = "";

  public void reset() {
    state = DayTypeState.UNKNOWN;
    archetypeState = DayTypeState.UNKNOWN;
    liveState = DayTypeState.UNKNOWN;
    ibComplete = false;
    ready = false;
    applicable = false;
    confidence = Double.NaN;
    trendUpScore = Double.NaN;
    trendDownScore = Double.NaN;
    rangeScore = Double.NaN;
    liquidationScore = Double.NaN;
    supportsLongContinuation = false;
    supportsShortContinuation = false;
    supportsFade = false;
    manualAidUsed = false;
    historicalNormReady = false;
    lookbackSessionsUsed = -1;
    summary = "";
    reasons = "";
    debugText = "";
  }

  public boolean hasSignal() {
    return applicable && state != DayTypeState.UNKNOWN;
  }

  public boolean isApplicable() {
    return applicable;
  }
}
