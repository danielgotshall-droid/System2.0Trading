package study_examples.lzs.strategy.management;

import study_examples.lzs.model.LzsSide;

/**
 * Position-aware market snapshot fed into the strategy-owned management engine.
 */
public class LzsTradeManagementSnapshot {
  public long nowMs;
  public int index;
  public LzsSide side;
  public double tickSize = 0.25;

  public double open = Double.NaN;
  public double high = Double.NaN;
  public double low = Double.NaN;
  public double close = Double.NaN;
  public double volume = Double.NaN;
  public double askVolume = Double.NaN;
  public double bidVolume = Double.NaN;
  public double delta = Double.NaN;

  public double entryPrice = Double.NaN;
  public double referencePrice = Double.NaN;
  public double stopPrice = Double.NaN;
  public double targetPrice = Double.NaN;
  public double lastPrice = Double.NaN;
  public double mfeTicks = Double.NaN;
  public double maeTicks = Double.NaN;
}
