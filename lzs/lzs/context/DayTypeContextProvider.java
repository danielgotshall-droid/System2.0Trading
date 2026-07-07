package study_examples.lzs.context;

import com.motivewave.platform.sdk.common.DataContext;
import com.motivewave.platform.sdk.common.DataSeries;

import study_examples.lzs.session.SessionDayTypeHelper;
import study_examples.lzs.session.SessionDayTypeHelper.DayTypeConfig;
import study_examples.lzs.session.SessionDayTypeHelper.DayTypeResult;

public final class DayTypeContextProvider {
  private long lastEvalAt = Long.MIN_VALUE;
  private int lastIndex = -1;
  private long lastSessionStart = Long.MIN_VALUE;
  private final LzsDayTypeContext cached = new LzsDayTypeContext();

  public void reset() {
    lastEvalAt = Long.MIN_VALUE;
    lastIndex = -1;
    lastSessionStart = Long.MIN_VALUE;
    cached.reset();
  }

  public LzsDayTypeContext evaluate(int index, DataContext ctx, long sessionStartTime, LzsContextConfig cfg) {
    if (ctx == null || cfg == null || !cfg.enableDayTypeContext) {
      cached.reset();
      return cached;
    }
    DataSeries s = ctx.getDataSeries();
    if (s == null || index < 0 || index >= s.size()) {
      cached.reset();
      return cached;
    }

    long now = System.currentTimeMillis();
    boolean newSession = sessionStartTime != lastSessionStart;
    boolean mustRefresh = newSession || lastIndex < 0 || index < lastIndex;
    if (!mustRefresh && lastEvalAt != Long.MIN_VALUE && cfg.dayTypeRefreshIntervalMs > 0
        && (now - lastEvalAt) < cfg.dayTypeRefreshIntervalMs) {
      return cached;
    }

    DayTypeConfig helperCfg = new DayTypeConfig(
        cfg.ibMinutes,
        cfg.dayTypeRecentLookbackSessions,
        cfg.dayTypeSmallIbThreshold,
        cfg.dayTypeLargeIbThreshold,
        cfg.dayTypeAcceptanceHoldBars,
        cfg.dayTypeExtensionMinTicks,
        cfg.dayTypeReentryToleranceTicks,
        cfg.dayTypeRotationThreshold,
        cfg.dayTypeLiquidationImpulseAtrMultiple,
        cfg.dayTypeValueMigrationMinTicks,
        cfg.useManualDayTypeAid,
        cfg.preferManualDayTypeAid,
        cfg.manualRecentMedianIbRange,
        cfg.manualRecentMedianIbVolume,
        cfg.manualAtrLikeRange,
        cfg.manualPriorSessionClose);

    SessionDayTypeHelper.InitialBalanceContext helperCtx = SessionDayTypeHelper.buildContext(index, ctx, helperCfg);
    DayTypeResult helperRes = SessionDayTypeHelper.evaluate(helperCtx, helperCfg);

    cached.reset();
    cached.state = helperRes.state;
    cached.archetypeState = helperRes.archetypeState;
    cached.liveState = helperRes.liveState;
    cached.ibComplete = helperRes.ibComplete;
    boolean manualNormConfigured = cfg.useManualDayTypeAid
        && !Double.isNaN(cfg.manualRecentMedianIbRange)
        && !Double.isNaN(cfg.manualAtrLikeRange);
    cached.manualAidUsed = helperCtx.manualAidUsed || manualNormConfigured;
    cached.lookbackSessionsUsed = helperCtx.lookbackSessionsUsed;
    cached.historicalNormReady = !Double.isNaN(helperCtx.recentMedianIbRange)
        && !Double.isNaN(helperCtx.atrLikeRange)
        && (helperCtx.lookbackSessionsUsed >= cfg.dayTypeRecentLookbackSessions || helperCtx.manualAidUsed || manualNormConfigured);
    cached.ready = helperRes.ibComplete && cached.historicalNormReady;
    cached.applicable = cached.ready;
    cached.confidence = helperRes.confidence;
    cached.trendUpScore = helperRes.trendUpScore;
    cached.trendDownScore = helperRes.trendDownScore;
    cached.rangeScore = helperRes.rangeScore;
    cached.liquidationScore = helperRes.liquidationScore;
    cached.summary = helperRes.summary == null ? "" : helperRes.summary;
    if (cached.manualAidUsed) cached.summary = cached.summary + " [MA]";
    else if (cached.lookbackSessionsUsed >= 0) cached.summary = cached.summary + " [Lkb " + cached.lookbackSessionsUsed + "/" + cfg.dayTypeRecentLookbackSessions + "]";
    cached.reasons = helperRes.buildReasonSummary();
    cached.debugText = helperRes.debugText == null ? "" : helperRes.debugText;
    if (!cached.historicalNormReady) {
      cached.debugText = cached.debugText + (cached.debugText.length() == 0 ? "" : "\n")
          + "DT historical normalization incomplete"
          + " [Lkb " + helperCtx.lookbackSessionsUsed + "/" + cfg.dayTypeRecentLookbackSessions + "]"
          + (manualNormConfigured ? " [ManualConfigured]" : " [ManualMissing]");
    }

    switch (helperRes.liveState) {
      case TREND_UP:
        cached.supportsLongContinuation = helperRes.confidence >= cfg.dayTypeMinConfidence;
        break;
      case TREND_DOWN:
        cached.supportsShortContinuation = helperRes.confidence >= cfg.dayTypeMinConfidence;
        break;
      case RANGE_BALANCED:
      case RANGE_VOLATILE:
      case LIQUIDATION_THEN_BALANCE:
        cached.supportsFade = helperRes.confidence >= cfg.dayTypeMinConfidence || helperRes.state == SessionDayTypeHelper.DayTypeState.LIQUIDATION_THEN_BALANCE;
        break;
      default:
        break;
    }

    lastEvalAt = now;
    lastIndex = index;
    lastSessionStart = sessionStartTime;
    return cached;
  }
}
