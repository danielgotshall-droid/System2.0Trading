package study_examples.lzs.model;

import java.util.ArrayList;
import java.util.List;

public final class LzsSnapshot {
  public long time;
  public int barIndex = -1;
  public long barStartTime = Long.MIN_VALUE;

  public double tickSize;
  public double lastPrice = Double.NaN;
  public double barHigh = Double.NaN;
  public double barLow = Double.NaN;

  public double bestBid = Double.NaN;
  public double bestAsk = Double.NaN;

  public final List<LzsRow> bidRowsNear = new ArrayList<LzsRow>();
  public final List<LzsRow> askRowsNear = new ArrayList<LzsRow>();
  public final List<LzsExecRow> execRows = new ArrayList<LzsExecRow>();

  public double totalBidNear;
  public double totalAskNear;
  public double largestBidBlock;
  public double largestAskBlock;

  public boolean hasBook() {
    return !bidRowsNear.isEmpty() || !askRowsNear.isEmpty();
  }
}
