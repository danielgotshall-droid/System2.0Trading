package study_examples.lzs.strategy.management;

import java.util.ArrayDeque;
import java.util.Deque;

import study_examples.lzs.model.LzsSide;

/**
 * Lightweight time-based rolling window for management metrics.
 */
public class LzsTradeManagementWindow {
  private final long durationMs;
  private final Deque<LzsTradeManagementSnapshot> items = new ArrayDeque<>();

  public LzsTradeManagementWindow(long durationMs) {
    this.durationMs = Math.max(1L, durationMs);
  }

  public void clear() {
    items.clear();
  }

  public boolean isEmpty() {
    return items.isEmpty();
  }

  public int size() {
    return items.size();
  }

  public void add(LzsTradeManagementSnapshot snapshot) {
    if (snapshot == null) return;
    items.addLast(snapshot);
    trim(snapshot.nowMs);
  }

  public LzsTradeManagementSnapshot first() {
    return items.peekFirst();
  }

  public LzsTradeManagementSnapshot last() {
    return items.peekLast();
  }

  public void trim(long nowMs) {
    long minTime = nowMs - durationMs;
    while (!items.isEmpty() && items.peekFirst().nowMs < minTime) items.removeFirst();
  }

  public double sumVolume() {
    double out = 0.0;
    for (LzsTradeManagementSnapshot s : items) out += nz(s.volume);
    return out;
  }

  public double sumDelta() {
    double out = 0.0;
    for (LzsTradeManagementSnapshot s : items) out += nz(s.delta);
    return out;
  }

  public double sumPositiveDelta() {
    double out = 0.0;
    for (LzsTradeManagementSnapshot s : items) {
      double d = nz(s.delta);
      if (d > 0) out += d;
    }
    return out;
  }

  public double sumNegativeDeltaMagnitude() {
    double out = 0.0;
    for (LzsTradeManagementSnapshot s : items) {
      double d = nz(s.delta);
      if (d < 0) out += -d;
    }
    return out;
  }

  public double sumAbsDelta() {
    double out = 0.0;
    for (LzsTradeManagementSnapshot s : items) out += Math.abs(nz(s.delta));
    return out;
  }

  public double signedDisplacementTicks(LzsSide side, double tickSize) {
    if (items.size() < 1) return 0.0;
    LzsTradeManagementSnapshot first = first();
    LzsTradeManagementSnapshot last = last();
    double ts = Math.max(0.0000001, tickSize);
    if (side == LzsSide.LONG) return (nz(last.close) - nz(first.open)) / ts;
    return (nz(first.open) - nz(last.close)) / ts;
  }

  public double closesBeyondReferenceRatio(LzsSide side, double referencePrice) {
    if (items.isEmpty() || Double.isNaN(referencePrice)) return 0.0;
    int good = 0;
    for (LzsTradeManagementSnapshot s : items) {
      if (side == LzsSide.LONG) {
        if (nz(s.close) >= referencePrice) good++;
      }
      else {
        if (nz(s.close) <= referencePrice) good++;
      }
    }
    return ((double) good) / ((double) items.size());
  }

  public double opposingDeltaMagnitude(LzsSide side) {
    return side == LzsSide.LONG ? sumNegativeDeltaMagnitude() : sumPositiveDelta();
  }

  public double favorableDeltaMagnitude(LzsSide side) {
    return side == LzsSide.LONG ? sumPositiveDelta() : sumNegativeDeltaMagnitude();
  }

  private double nz(double v) {
    return Double.isNaN(v) ? 0.0 : v;
  }
}
