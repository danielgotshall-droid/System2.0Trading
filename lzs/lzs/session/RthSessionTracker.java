package study_examples.lzs.session;

import com.motivewave.platform.sdk.common.DataContext;
import com.motivewave.platform.sdk.common.DataSeries;
import com.motivewave.platform.sdk.common.Instrument;

public final class RthSessionTracker {
  private long currentSessionStartTime = Long.MIN_VALUE;
  private int currentSessionStartIndex = -1;
  private int lastProcessedIndex = -1;
  private int lastContributionIndex = -1;

  private double sessionOpen = Double.NaN;
  private double sessionHigh = Double.NaN;
  private double sessionLow = Double.NaN;
  private double sessionClose = Double.NaN;
  private double sessionVolume = 0.0;
  private double sessionPv = 0.0;

  private double priorSessionHigh = Double.NaN;
  private double priorSessionLow = Double.NaN;
  private double priorSessionClose = Double.NaN;

  private long pendingOvernightSessionStart = Long.MIN_VALUE;
  private double pendingOvernightHigh = Double.NaN;
  private double pendingOvernightLow = Double.NaN;
  private double overnightHigh = Double.NaN;
  private double overnightLow = Double.NaN;

  private double lastContributionVolume = 0.0;
  private double lastContributionPv = 0.0;

  public void reset() {
    currentSessionStartTime = Long.MIN_VALUE;
    currentSessionStartIndex = -1;
    lastProcessedIndex = -1;
    lastContributionIndex = -1;
    sessionOpen = Double.NaN;
    sessionHigh = Double.NaN;
    sessionLow = Double.NaN;
    sessionClose = Double.NaN;
    sessionVolume = 0.0;
    sessionPv = 0.0;
    priorSessionHigh = Double.NaN;
    priorSessionLow = Double.NaN;
    priorSessionClose = Double.NaN;
    pendingOvernightSessionStart = Long.MIN_VALUE;
    pendingOvernightHigh = Double.NaN;
    pendingOvernightLow = Double.NaN;
    overnightHigh = Double.NaN;
    overnightLow = Double.NaN;
    lastContributionVolume = 0.0;
    lastContributionPv = 0.0;
  }

  public void update(int index, DataContext ctx) {
    if (ctx == null) return;
    DataSeries s = ctx.getDataSeries();
    Instrument instr = ctx.getInstrument();
    if (s == null || instr == null || index < 0 || index >= s.size()) return;

    if (index < lastProcessedIndex) reset();

    if (index == lastProcessedIndex) {
      refreshCurrentBar(index, s, instr);
      return;
    }

    int start = Math.max(0, lastProcessedIndex + 1);
    for (int i = start; i <= index; i++) {
      processBar(i, s, instr);
    }
    lastProcessedIndex = index;
  }

  private void processBar(int index, DataSeries s, Instrument instr) {
    long barTime = s.getStartTime(index);
    long sessionStart = safeSessionStart(instr, barTime);
    if (sessionStart == Long.MIN_VALUE) return;

    if (barTime < sessionStart) {
      updatePendingOvernight(sessionStart, s.getHigh(index), s.getLow(index));
      return;
    }

    if (currentSessionStartTime == Long.MIN_VALUE || sessionStart != currentSessionStartTime) {
      rolloverSession();
      currentSessionStartTime = sessionStart;
      currentSessionStartIndex = index;
      sessionOpen = s.getOpen(index);
      sessionHigh = s.getHigh(index);
      sessionLow = s.getLow(index);
      sessionClose = s.getClose(index);
      sessionVolume = 0.0;
      sessionPv = 0.0;
      lastContributionIndex = -1;
      lastContributionVolume = 0.0;
      lastContributionPv = 0.0;
      if (pendingOvernightSessionStart == sessionStart) {
        overnightHigh = pendingOvernightHigh;
        overnightLow = pendingOvernightLow;
      }
      else {
        overnightHigh = Double.NaN;
        overnightLow = Double.NaN;
      }
    }

    double vol = safeVolume(s, index);
    double pv = typicalPrice(s, index) * vol;
    sessionVolume += vol;
    sessionPv += pv;
    sessionHigh = Math.max(sessionHigh, s.getHigh(index));
    sessionLow = Math.min(sessionLow, s.getLow(index));
    sessionClose = s.getClose(index);
    lastContributionIndex = index;
    lastContributionVolume = vol;
    lastContributionPv = pv;
  }

  private void refreshCurrentBar(int index, DataSeries s, Instrument instr) {
    if (index != lastContributionIndex) return;
    long barTime = s.getStartTime(index);
    long sessionStart = safeSessionStart(instr, barTime);
    if (currentSessionStartTime == Long.MIN_VALUE || sessionStart != currentSessionStartTime || barTime < sessionStart) return;

    double vol = safeVolume(s, index);
    double pv = typicalPrice(s, index) * vol;
    sessionVolume += (vol - lastContributionVolume);
    sessionPv += (pv - lastContributionPv);
    sessionHigh = Math.max(sessionHigh, s.getHigh(index));
    sessionLow = Math.min(sessionLow, s.getLow(index));
    sessionClose = s.getClose(index);
    lastContributionVolume = vol;
    lastContributionPv = pv;
  }

  private void updatePendingOvernight(long sessionStart, double high, double low) {
    if (pendingOvernightSessionStart != sessionStart) {
      pendingOvernightSessionStart = sessionStart;
      pendingOvernightHigh = high;
      pendingOvernightLow = low;
      return;
    }
    pendingOvernightHigh = Double.isNaN(pendingOvernightHigh) ? high : Math.max(pendingOvernightHigh, high);
    pendingOvernightLow = Double.isNaN(pendingOvernightLow) ? low : Math.min(pendingOvernightLow, low);
  }

  private void rolloverSession() {
    if (!Double.isNaN(sessionHigh) && !Double.isNaN(sessionLow)) {
      priorSessionHigh = sessionHigh;
      priorSessionLow = sessionLow;
      priorSessionClose = sessionClose;
    }
  }

  private long safeSessionStart(Instrument instr, long barTime) {
    try {
      return instr.getStartOfDay(barTime, true);
    }
    catch (Throwable t) {
      return Long.MIN_VALUE;
    }
  }

  private double safeVolume(DataSeries s, int index) {
    try {
      return s.getVolume(index);
    }
    catch (Throwable t) {
      return 0.0;
    }
  }

  private double typicalPrice(DataSeries s, int index) {
    return (s.getHigh(index) + s.getLow(index) + s.getClose(index)) / 3.0;
  }

  public long getCurrentSessionStartTime() { return currentSessionStartTime; }
  public int getCurrentSessionStartIndex() { return currentSessionStartIndex; }
  public int getLastProcessedIndex() { return lastProcessedIndex; }
  public double getSessionOpen() { return sessionOpen; }
  public double getSessionHigh() { return sessionHigh; }
  public double getSessionLow() { return sessionLow; }
  public double getSessionClose() { return sessionClose; }
  public double getPriorSessionHigh() { return priorSessionHigh; }
  public double getPriorSessionLow() { return priorSessionLow; }
  public double getPriorSessionClose() { return priorSessionClose; }
  public double getOvernightHigh() { return overnightHigh; }
  public double getOvernightLow() { return overnightLow; }
  public double getSessionVwap() { return sessionVolume > 0.0 ? sessionPv / sessionVolume : Double.NaN; }
}
