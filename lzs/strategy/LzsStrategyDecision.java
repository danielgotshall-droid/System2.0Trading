package study_examples.lzs.strategy;

import study_examples.lzs.model.LzsSide;

/**
 * Strategy output: a planned entry plus initial bracket and reference metadata.
 */
public class LzsStrategyDecision {
  public LzsEntryDecision decision = LzsEntryDecision.NONE;
  public LzsSide side = null;
  public double entryPrice = Double.NaN;
  public double stopPrice = Double.NaN;
  public double targetPrice = Double.NaN;
  public double signalReferencePrice = Double.NaN;
  public String reason = "";

  public static LzsStrategyDecision none(String reason) {
    LzsStrategyDecision d = new LzsStrategyDecision();
    d.reason = reason == null ? "" : reason;
    return d;
  }
}
