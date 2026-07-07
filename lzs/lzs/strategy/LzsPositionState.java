package study_examples.lzs.strategy;

import study_examples.lzs.model.LzsSide;

/**
 * Strategy-owned position state.
 */
public class LzsPositionState {
  public LzsPositionLifecycle lifecycle = LzsPositionLifecycle.FLAT;
  public LzsSide side = null;
  public double entryPrice = Double.NaN;
  public double initialStopPrice = Double.NaN;
  public double stopPrice = Double.NaN;
  public double targetPrice = Double.NaN;
  public double signalReferencePrice = Double.NaN;
  public int quantity = 0;
  public long entryTime = 0L;
  public String entryReason = "";
  public String stopMode = "INIT";

  public double lastPrice = Double.NaN;
  public double mfeTicks = Double.NaN;
  public double maeTicks = Double.NaN;
  private double bestPrice = Double.NaN;
  private double worstPrice = Double.NaN;

  public boolean isFlat() {
    return lifecycle == LzsPositionLifecycle.FLAT;
  }

  public boolean isActive() {
    return lifecycle == LzsPositionLifecycle.ACTIVE;
  }

  public void markEntryPending(LzsSide side, double entryPrice, double stopPrice, double targetPrice,
      double signalReferencePrice, int quantity, long entryTime, String entryReason) {
    this.lifecycle = LzsPositionLifecycle.ENTRY_PENDING;
    this.side = side;
    this.entryPrice = entryPrice;
    this.initialStopPrice = stopPrice;
    this.stopPrice = stopPrice;
    this.targetPrice = targetPrice;
    this.signalReferencePrice = signalReferencePrice;
    this.quantity = quantity;
    this.entryTime = entryTime;
    this.entryReason = entryReason == null ? "" : entryReason;
    this.stopMode = "INIT";
    this.lastPrice = entryPrice;
    this.bestPrice = entryPrice;
    this.worstPrice = entryPrice;
    this.mfeTicks = 0.0;
    this.maeTicks = 0.0;
  }

  public void markActive(long fillTime, double fillPrice, int filledQty) {
    this.lifecycle = LzsPositionLifecycle.ACTIVE;
    this.entryTime = fillTime;
    this.entryPrice = fillPrice;
    if (Double.isNaN(this.signalReferencePrice)) this.signalReferencePrice = fillPrice;
    this.quantity = filledQty;
    this.lastPrice = fillPrice;
    this.bestPrice = fillPrice;
    this.worstPrice = fillPrice;
    this.mfeTicks = 0.0;
    this.maeTicks = 0.0;
  }

  public void updateMarket(double lastPrice, double tickSize) {
    if (side == null || Double.isNaN(lastPrice) || Double.isNaN(entryPrice)) return;
    this.lastPrice = lastPrice;
    if (Double.isNaN(bestPrice)) bestPrice = entryPrice;
    if (Double.isNaN(worstPrice)) worstPrice = entryPrice;

    if (side == LzsSide.LONG) {
      if (lastPrice > bestPrice) bestPrice = lastPrice;
      if (lastPrice < worstPrice) worstPrice = lastPrice;
      mfeTicks = Math.max(0.0, (bestPrice - entryPrice) / Math.max(0.0000001, tickSize));
      maeTicks = Math.max(0.0, (entryPrice - worstPrice) / Math.max(0.0000001, tickSize));
    }
    else {
      if (lastPrice < bestPrice || Double.isNaN(bestPrice)) bestPrice = lastPrice;
      if (lastPrice > worstPrice || Double.isNaN(worstPrice)) worstPrice = lastPrice;
      mfeTicks = Math.max(0.0, (entryPrice - bestPrice) / Math.max(0.0000001, tickSize));
      maeTicks = Math.max(0.0, (worstPrice - entryPrice) / Math.max(0.0000001, tickSize));
    }
  }


  public boolean tightenStop(double newStopPrice, String mode) {
    if (side == null || Double.isNaN(newStopPrice)) return false;
    boolean tighter;
    if (Double.isNaN(stopPrice)) tighter = true;
    else if (side == LzsSide.LONG) tighter = newStopPrice > stopPrice;
    else tighter = newStopPrice < stopPrice;
    if (!tighter) return false;
    this.stopPrice = newStopPrice;
    this.stopMode = mode == null || mode.isEmpty() ? this.stopMode : mode;
    return true;
  }

  public void markExitPending() {
    this.lifecycle = LzsPositionLifecycle.EXIT_PENDING;
  }

  public void reset() {
    this.lifecycle = LzsPositionLifecycle.FLAT;
    this.side = null;
    this.entryPrice = Double.NaN;
    this.initialStopPrice = Double.NaN;
    this.stopPrice = Double.NaN;
    this.targetPrice = Double.NaN;
    this.signalReferencePrice = Double.NaN;
    this.quantity = 0;
    this.entryTime = 0L;
    this.entryReason = "";
    this.stopMode = "INIT";
    this.lastPrice = Double.NaN;
    this.mfeTicks = Double.NaN;
    this.maeTicks = Double.NaN;
    this.bestPrice = Double.NaN;
    this.worstPrice = Double.NaN;
  }
}
