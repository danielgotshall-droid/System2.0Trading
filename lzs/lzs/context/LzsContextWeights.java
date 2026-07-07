package study_examples.lzs.context;

public final class LzsContextWeights {
  public double structuralWeight = 1.05;
  public double sessionReferenceWeight = 0.95;
  public double dayTypeWeight = 0.75;
  public double freeFloatingPenalty = 0.28;
  public double oppositeBiasPenalty = 0.45;
  public double continuationBiasBoost = 0.40;
  public double fadeBiasBoost = 0.40;
  public double minSupportThreshold = 0.55;
  public double minReasonContribution = 0.16;

  public double locationSuitabilityWeight = 0.60;
  public double trendContinuationWeight = 0.75;
  public double countertrendPenaltyWeight = 0.75;
  public double forwardObstructionPenaltyWeight = 0.65;

  public static LzsContextWeights defaults() {
    return new LzsContextWeights();
  }
}
