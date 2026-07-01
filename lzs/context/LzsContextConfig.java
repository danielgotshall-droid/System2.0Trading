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


  public LzsContextMode contextMode = LzsContextMode.ANNOTATION_ONLY;
  public LzsHudDisplayMode hudDisplayMode = LzsHudDisplayMode.STANDARD;
  public double minMergedContextScoreForFilter = 0.90;
  public double minStructuralScoreForFilter = 0.05;
  public double minDayTypeScoreForFilter = 0.0;
  public boolean requireSupportsSideWhenFilterEnabled = true;
  public boolean blockFreeFloatingWhenFilterEnabled = false;
  public boolean showFilterStatusOnHud = true;


  public boolean enableMergedContext = true;
  public boolean showMergedContextOnHud = true;
  public boolean showMergedContextReasons = false;
  public final LzsContextWeights mergeWeights = LzsContextWeights.defaults();
  public boolean showDayTypeOnHud = true;
  public boolean showDayTypeDebug = false;
  public boolean showContextReasons = false;
  public boolean showReferenceSourcesOnHud = true;
  public boolean showDevelopingReferencesOnHud = true;


  // Time-and-distance reference freshness tuned for 500 ms charts.
  public long referenceInteractionFreshMs = 5000L;
  public long referenceInteractionMaxAgeMs = 30000L;
  public int referenceFullWeightDistanceTicks = 4;
  public int referenceFadeDistanceTicks = 20;
  public int referenceRoleTouchTicks = 2;
  public int referenceExtensionTicks = 8;

  // Session location suitability defaults.
  public double rangeLowerThirdPct = 0.33;
  public double rangeUpperThirdPct = 0.67;
  public double trendMidLowerPct = 0.45;
  public double trendMidUpperPct = 0.55;


  // Reference-zone defaults by family (ES-oriented starter values).
  public int valueZoneHalfWidthTicks = 4;
  public int valueAcceptanceDistanceTicks = 6;
  public long valueAcceptanceHoldMs = 15000L;

  public int windowZoneHalfWidthTicks = 6;
  public int windowAcceptanceDistanceTicks = 8;
  public long windowAcceptanceHoldMs = 20000L;

  public int extremeZoneHalfWidthTicks = 8;
  public int extremeAcceptanceDistanceTicks = 12;
  public long extremeAcceptanceHoldMs = 30000L;

  // Forward structural obstruction penalty defaults.
  public int forwardObstructionNearTicks = 5;
  public int forwardObstructionFadeTicks = 18;

  public static LzsContextConfig defaults() {
    return new LzsContextConfig();
  }
}
