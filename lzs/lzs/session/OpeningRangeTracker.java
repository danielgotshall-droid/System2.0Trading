package study_examples.lzs.session;

import com.motivewave.platform.sdk.common.DataContext;
import com.motivewave.platform.sdk.common.DataSeries;

public final class OpeningRangeTracker {
  private long sessionStartTime = Long.MIN_VALUE;
  private long orEndTime = Long.MIN_VALUE;
  private int orMinutes = 5;
  private int lastProcessedIndex = -1;
  private int lastOrIndex = -1;

  private double orHigh = Double.NaN;
  private double orLow = Double.NaN;
  private boolean complete;

  public void reset() {
    sessionStartTime = Long.MIN_VALUE;
    orEndTime = Long.MIN_VALUE;
    lastProcessedIndex = -1;
    lastOrIndex = -1;
    orHigh = Double.NaN;
    orLow = Double.NaN;
    complete = false;
  }

  public void update(int index, DataContext ctx, RthSessionTracker sessionTracker, int orMinutesSetting) {
    if (ctx == null || sessionTracker == null) return;
    DataSeries s = ctx.getDataSeries();
    if (s == null || index < 0 || index >= s.size()) return;

    if (orMinutes != orMinutesSetting || index < lastProcessedIndex) {
      orMinutes = orMinutesSetting;
      reset();
    }

    long currentSessionStart = sessionTracker.getCurrentSessionStartTime();
    if (currentSessionStart == Long.MIN_VALUE) {
      lastProcessedIndex = index;
      return;
    }

    if (sessionStartTime != currentSessionStart) {
      sessionStartTime = currentSessionStart;
      orEndTime = sessionStartTime + Math.max(1, orMinutes) * 60_000L;
      orHigh = Double.NaN;
      orLow = Double.NaN;
      complete = false;
      lastOrIndex = -1;
    }

    if (index == lastProcessedIndex) {
      refreshCurrentBar(index, s);
      updateCompleteFlag(s.getStartTime(index));
      return;
    }

    int start = Math.max(sessionTracker.getCurrentSessionStartIndex(), Math.max(0, lastProcessedIndex + 1));
    if (lastProcessedIndex < sessionTracker.getCurrentSessionStartIndex()) {
      start = sessionTracker.getCurrentSessionStartIndex();
    }
    for (int i = Math.max(0, start); i <= index; i++) {
      processBar(i, s);
    }
    lastProcessedIndex = index;
  }

  private void processBar(int index, DataSeries s) {
    long barTime = s.getStartTime(index);
    if (barTime < sessionStartTime || barTime >= orEndTime) {
      updateCompleteFlag(barTime);
      return;
    }
    orHigh = Double.isNaN(orHigh) ? s.getHigh(index) : Math.max(orHigh, s.getHigh(index));
    orLow = Double.isNaN(orLow) ? s.getLow(index) : Math.min(orLow, s.getLow(index));
    lastOrIndex = index;
    updateCompleteFlag(barTime);
  }

  private void refreshCurrentBar(int index, DataSeries s) {
    if (index != lastOrIndex) return;
    long barTime = s.getStartTime(index);
    if (barTime < sessionStartTime || barTime >= orEndTime) return;
    orHigh = Double.isNaN(orHigh) ? s.getHigh(index) : Math.max(orHigh, s.getHigh(index));
    orLow = Double.isNaN(orLow) ? s.getLow(index) : Math.min(orLow, s.getLow(index));
  }

  private void updateCompleteFlag(long barTime) {
    if (sessionStartTime == Long.MIN_VALUE) return;
    if (barTime >= orEndTime) complete = true;
  }

  public long getOrEndTime() { return orEndTime; }
  public boolean isComplete() { return complete; }
  public boolean hasOrValues() { return !Double.isNaN(orHigh) && !Double.isNaN(orLow); }
  public double getOrHigh() { return orHigh; }
  public double getOrLow() { return orLow; }
  public double getOrMid() { return (Double.isNaN(orHigh) || Double.isNaN(orLow)) ? Double.NaN : (orHigh + orLow) * 0.5; }
}
