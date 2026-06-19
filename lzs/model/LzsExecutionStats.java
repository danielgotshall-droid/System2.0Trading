package study_examples.lzs.model;

public final class LzsExecutionStats {
  public double sameSideExecVol;
  public double totalExecVol;
  public int bubbleCount;
  public double aggressionShare;
  public double execBandLow = Double.NaN;
  public double execBandHigh = Double.NaN;
  public double weightedExecPriceSum;
  public double referencePrice = Double.NaN;

  public boolean qualifies(LzsConfig cfg) {
    if (cfg == null) return false;
    boolean bubbleOk = bubbleCount >= Math.max(1, cfg.minBubbleCount);
    boolean aggressionOk = sameSideExecVol >= Math.max(1.0, cfg.minBubbleSize)
        && aggressionShare >= Math.max(0.0, cfg.minAggressionShare);
    return bubbleOk || aggressionOk;
  }
}
