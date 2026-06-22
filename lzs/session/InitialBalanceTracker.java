package study_examples.lzs.session;

import com.motivewave.platform.sdk.common.DataContext;
import com.motivewave.platform.sdk.common.DataSeries;

public final class InitialBalanceTracker {
  private long sessionStartTime = Long.MIN_VALUE;
  private long ibEndTime = Long.MIN_VALUE;
  private int ibMinutes = 60;
  private int lastProcessedIndex = -1;
  private int lastIbIndex = -1;

  private double ibOpen = Double.NaN;
  private double ibHigh = Double.NaN;
  private double ibLow = Double.NaN;
  private boolean ibComplete;

  public void reset() {
    sessionStartTime = Long.MIN_VALUE;
    ibEndTime = Long.MIN_VALUE;
    lastProcessedIndex = -1;
    lastIbIndex = -1;
    ibOpen = Double.NaN;
    ibHigh = Double.NaN;
    ibLow = Double.NaN;
    ibComplete = false;
  }

  public void update(int index, DataContext ctx, RthSessionTracker sessionTracker, int ibMinutesSetting) {
    if (ctx == null || sessionTracker == null) return;
    DataSeries s = ctx.getDataSeries();
    if (s == null || index < 0 || index >= s.size()) return;

    if (ibMinutes != ibMinutesSetting || index < lastProcessedIndex) {
      ibMinutes = ibMinutesSetting;
      reset();
    }

    long currentSessionStart = sessionTracker.getCurrentSessionStartTime();
    if (currentSessionStart == Long.MIN_VALUE) {
      lastProcessedIndex = index;
      return;
    }

    if (sessionStartTime != currentSessionStart) {
      sessionStartTime = currentSessionStart;
      ibEndTime = sessionStartTime + Math.max(1, ibMinutes) * 60_000L;
      ibOpen = sessionTracker.getSessionOpen();
      ibHigh = Double.NaN;
      ibLow = Double.NaN;
      ibComplete = false;
      lastIbIndex = -1;
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
    if (barTime < sessionStartTime || barTime >= ibEndTime) {
      updateCompleteFlag(barTime);
      return;
    }
    if (Double.isNaN(ibOpen)) ibOpen = s.getOpen(index);
    ibHigh = Double.isNaN(ibHigh) ? s.getHigh(index) : Math.max(ibHigh, s.getHigh(index));
    ibLow = Double.isNaN(ibLow) ? s.getLow(index) : Math.min(ibLow, s.getLow(index));
    lastIbIndex = index;
    updateCompleteFlag(barTime);
  }

  private void refreshCurrentBar(int index, DataSeries s) {
    if (index != lastIbIndex) return;
    long barTime = s.getStartTime(index);
    if (barTime < sessionStartTime || barTime >= ibEndTime) return;
    ibHigh = Double.isNaN(ibHigh) ? s.getHigh(index) : Math.max(ibHigh, s.getHigh(index));
    ibLow = Double.isNaN(ibLow) ? s.getLow(index) : Math.min(ibLow, s.getLow(index));
  }

  private void updateCompleteFlag(long barTime) {
    if (sessionStartTime == Long.MIN_VALUE) return;
    if (barTime >= ibEndTime) ibComplete = true;
  }

  public long getSessionStartTime() { return sessionStartTime; }
  public long getIbEndTime() { return ibEndTime; }
  public boolean isIbComplete() { return ibComplete; }
  public double getIbOpen() { return ibOpen; }
  public double getIbHigh() { return ibHigh; }
  public double getIbLow() { return ibLow; }
  public boolean hasIbValues() { return !Double.isNaN(ibHigh) && !Double.isNaN(ibLow); }
  public double getIbMid() {
    return (Double.isNaN(ibHigh) || Double.isNaN(ibLow)) ? Double.NaN : (ibHigh + ibLow) * 0.5;
  }
}
