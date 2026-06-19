package study_examples.lzs.model;

public final class LzsInteraction {
  public LzsPhase phase = LzsPhase.IDLE;
  public boolean valid;

  public int updatesSeen;
  public int updatesSinceArm;
  public int updatesSinceTouch;
  public int updatesSinceExec;

  public long armedTime = Long.MIN_VALUE;
  public long touchTime = Long.MIN_VALUE;
  public long execConfirmTime = Long.MIN_VALUE;
  public long reverseTime = Long.MIN_VALUE;

  public double armedPrice = Double.NaN;
  public double touchPrice = Double.NaN;
  public double execConfirmPrice = Double.NaN;
  public double reversalRefPrice = Double.NaN;
  public double reversalTicks = 0.0;
  public double maxFavorableTicksFromExec = 0.0;

  public double execSameSideVol = 0.0;
  public double execTotalVol = 0.0;
  public int bubbleCount = 0;
  public double aggressionShare = 0.0;
  public double execBandLow = Double.NaN;
  public double execBandHigh = Double.NaN;

  public double originalZoneTotalSize = 0.0;
  public double minObservedZoneTotalSize = Double.NaN;
  public boolean zoneObservable = false;
  public double reloadPct;
  public double remainingZonePct;
  public double pathClearTicks;
  public boolean blockedByOpposingLiquidity;

  public double score;
  public String reason = "";
  public String debug = "";
}
