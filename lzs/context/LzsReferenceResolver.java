package study_examples.lzs.context;

public final class LzsReferenceResolver {

  public LzsReferenceValue resolveStatic(
      String label,
      double autoValue,
      boolean autoReady,
      boolean manualEnabled,
      double manualValue,
      LzsContextConfig cfg) {
    boolean manualHasValue = manualEnabled && !Double.isNaN(manualValue);
    boolean preferManual = cfg != null && cfg.preferManualLevels;
    boolean allowFallback = cfg == null || cfg.useManualLevelFallback;

    if (preferManual && manualHasValue) {
      return new LzsReferenceValue(label).with(manualValue, LzsReferenceSource.MANUAL, LzsReferenceAvailability.READY, true);
    }
    if (autoReady && !Double.isNaN(autoValue)) {
      return new LzsReferenceValue(label).with(autoValue, manualHasValue ? LzsReferenceSource.HYBRID_AUTO : LzsReferenceSource.AUTO, LzsReferenceAvailability.READY, true);
    }
    if (allowFallback && manualHasValue) {
      return new LzsReferenceValue(label).with(manualValue, LzsReferenceSource.HYBRID_MANUAL, LzsReferenceAvailability.READY, true);
    }
    return LzsReferenceValue.unavailable(label);
  }

  public LzsReferenceValue resolveWindowed(
      String label,
      double autoValue,
      boolean autoHasValue,
      boolean windowComplete,
      boolean manualEnabled,
      double manualValue,
      LzsContextConfig cfg) {
    boolean manualHasValue = manualEnabled && !Double.isNaN(manualValue);
    boolean preferManual = cfg != null && cfg.preferManualLevels;
    boolean allowFallback = cfg == null || cfg.useManualLevelFallback;

    if (!windowComplete) {
      if (preferManual && manualHasValue) {
        return new LzsReferenceValue(label).with(manualValue, LzsReferenceSource.MANUAL, LzsReferenceAvailability.DEVELOPING, false);
      }
      if (autoHasValue && !Double.isNaN(autoValue)) {
        return new LzsReferenceValue(label).with(autoValue, manualHasValue ? LzsReferenceSource.HYBRID_AUTO : LzsReferenceSource.AUTO, LzsReferenceAvailability.DEVELOPING, false);
      }
      if (allowFallback && manualHasValue) {
        return new LzsReferenceValue(label).with(manualValue, LzsReferenceSource.HYBRID_MANUAL, LzsReferenceAvailability.DEVELOPING, false);
      }
      return LzsReferenceValue.unavailable(label);
    }

    if (preferManual && manualHasValue) {
      return new LzsReferenceValue(label).with(manualValue, LzsReferenceSource.MANUAL, LzsReferenceAvailability.READY, true);
    }
    if (autoHasValue && !Double.isNaN(autoValue)) {
      return new LzsReferenceValue(label).with(autoValue, manualHasValue ? LzsReferenceSource.HYBRID_AUTO : LzsReferenceSource.AUTO, LzsReferenceAvailability.READY, true);
    }
    if (allowFallback && manualHasValue) {
      return new LzsReferenceValue(label).with(manualValue, LzsReferenceSource.HYBRID_MANUAL, LzsReferenceAvailability.READY, true);
    }
    return LzsReferenceValue.unavailable(label);
  }
}
