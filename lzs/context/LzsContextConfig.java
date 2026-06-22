package study_examples.lzs.context;

public final class LzsContextConfig {
  public boolean enableContext = true;
  public boolean enableStructuralRefs = true;
  public boolean enableOvernightRefs = true;
  public boolean enableVwapRef = true;
  public boolean enableIbRefs = true;
  public boolean enableOrRefs = true;
  public boolean enableValueAreaRefs = true;
  public boolean enableDayTypeContext = true;


  public int structuralProximityTicks = 8;
  public int overnightProximityTicks = 8;
  public int vwapProximityTicks = 8;
  public int ibProximityTicks = 6;
  public int openingRangeProximityTicks = 6;
  public int valueAreaProximityTicks = 6;
  public int ibMinutes = 60;
  public int openingRangeMinutes = 5;
  public int dayTypeRefreshIntervalMs = 5000;
  public int dayTypeRecentLookbackSessions = 20;
  public double dayTypeScoreWeight = 1.0;
  public double dayTypeMinConfidence = 7.5;
  public double dayTypeSmallIbThreshold = 0.80;
  public double dayTypeLargeIbThreshold = 1.20;
  public int dayTypeAcceptanceHoldBars = 3;
  public int dayTypeExtensionMinTicks = 4;
  public int dayTypeReentryToleranceTicks = 2;
  public int dayTypeRotationThreshold = 3;
  public double dayTypeLiquidationImpulseAtrMultiple = 0.60;
  public double dayTypeValueMigrationMinTicks = 2.0;
  public boolean useManualDayTypeAid = false;
  public boolean preferManualDayTypeAid = false;
  public double manualRecentMedianIbRange = Double.NaN;
  public double manualRecentMedianIbVolume = Double.NaN;
  public double manualAtrLikeRange = Double.NaN;
  public double manualPriorSessionClose = Double.NaN;

  public boolean useManualLevelFallback = true;
  public boolean preferManualLevels = false;

  public boolean manualSessionOpenEnabled = false;
  public double manualSessionOpen = Double.NaN;

  public boolean manualPriorDayLevelsEnabled = false;
  public double manualPriorDayHigh = Double.NaN;
  public double manualPriorDayLow = Double.NaN;
  public double manualPriorDayClose = Double.NaN;

  public boolean manualOvernightLevelsEnabled = false;
  public double manualOvernightHigh = Double.NaN;
  public double manualOvernightLow = Double.NaN;

  public boolean manualOpeningRangeLevelsEnabled = false;
  public double manualOpeningRangeHigh = Double.NaN;
  public double manualOpeningRangeLow = Double.NaN;

  public boolean manualInitialBalanceLevelsEnabled = false;
  public double manualInitialBalanceHigh = Double.NaN;
  public double manualInitialBalanceLow = Double.NaN;

  public boolean manualValueAreaLevelsEnabled = false;
  public double manualPriorValueAreaHigh = Double.NaN;
  public double manualPriorValueAreaLow = Double.NaN;
  public double manualPriorPoc = Double.NaN;

  public boolean showContextOnHud = true;
  public boolean showDayTypeOnHud = true;
  public boolean showDayTypeDebug = false;
  public boolean showContextReasons = false;
  public boolean showReferenceSourcesOnHud = true;
  public boolean showDevelopingReferencesOnHud = true;

  public static LzsContextConfig defaults() {
    return new LzsContextConfig();
  }
}
