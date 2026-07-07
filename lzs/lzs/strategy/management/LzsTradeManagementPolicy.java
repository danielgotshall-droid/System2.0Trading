package study_examples.lzs.strategy.management;

import study_examples.lzs.strategy.LzsPositionState;

/** First actionable management policy layer. */
public class LzsTradeManagementPolicy {
  private final LzsTradeManagementConfig config;

  public LzsTradeManagementPolicy(LzsTradeManagementConfig config) {
    this.config = config == null ? new LzsTradeManagementConfig() : config;
  }

  public LzsTradeManagementDecision decide(LzsTradeManagementScores scores, LzsTradeManagementMetrics metrics,
      double structuralRefPrice, LzsPositionState position, double tickSize, long nowMs, long stateSinceMs) {
    if (!config.actionsEnabled || scores == null || position == null || !position.isActive()) {
      return LzsTradeManagementDecision.none("off");
    }
    LzsTradeManagementDecision d = new LzsTradeManagementDecision();
    d.state = scores.state;
    if (scores.state == null || scores.state == LzsTradeManagementState.MGMT_INACTIVE) {
      d.reason = "inactive";
      return d;
    }
    if (scores.state == LzsTradeManagementState.MGMT_HOLD) {
      d.action = LzsTradeManagementAction.KEEP_STOP;
      d.reason = "hold";
      return d;
    }

    double ts = Math.max(0.0000001, tickSize);

    if (scores.state == LzsTradeManagementState.MGMT_FORCE_EXIT) {
      if (!config.forceExitActionEnabled) {
        d.reason = "forceOff";
        return d;
      }
      d.action = LzsTradeManagementAction.EXIT_MARKET;
      d.reason = "forceExit";
      return d;
    }

    if (scores.state == LzsTradeManagementState.MGMT_EXIT_RISK) {
      if (!config.exitRiskActionEnabled) {
        d.reason = "exitRiskOff";
        return d;
      }
      if (stateSinceMs > 0L && nowMs - stateSinceMs < config.exitRiskPersistenceMs) {
        d.reason = "exitPersist";
        return d;
      }
      if (config.exitRiskActionMode == 1) {
        d.action = LzsTradeManagementAction.EXIT_MARKET;
        d.reason = "exitRisk";
        return d;
      }
      double buffer = Math.max(0.0, config.exitRiskTightenBufferTicks) * ts;
      double basis = Double.isNaN(position.lastPrice) ? position.entryPrice : position.lastPrice;
      double candidate = position.side == study_examples.lzs.model.LzsSide.LONG
          ? basis - buffer
          : basis + buffer;
      if (config.exitRiskUseStructuralRef && !Double.isNaN(structuralRefPrice)) {
        double structCandidate = position.side == study_examples.lzs.model.LzsSide.LONG
            ? structuralRefPrice - buffer
            : structuralRefPrice + buffer;
        if (position.side == study_examples.lzs.model.LzsSide.LONG) candidate = Math.max(candidate, structCandidate);
        else candidate = Math.min(candidate, structCandidate);
      }
      boolean tighter = Double.isNaN(position.stopPrice)
          || (position.side == study_examples.lzs.model.LzsSide.LONG ? candidate > position.stopPrice : candidate < position.stopPrice);
      if (!tighter) {
        d.action = LzsTradeManagementAction.KEEP_STOP;
        d.reason = "noTighten";
        return d;
      }
      d.action = LzsTradeManagementAction.TIGHTEN_STOP;
      d.targetStopPrice = candidate;
      d.reason = config.exitRiskUseStructuralRef && !Double.isNaN(structuralRefPrice) ? "exitRiskStruct" : "exitRiskTighten";
      return d;
    }

    if (scores.state != LzsTradeManagementState.MGMT_PROTECT || !config.protectActionEnabled) {
      d.reason = scores.state == LzsTradeManagementState.MGMT_PROTECT ? "protectOff" : "advisory";
      return d;
    }
    if (Double.isNaN(position.mfeTicks) || position.mfeTicks < config.protectArmMfeTicks) {
      d.reason = "mfeArm";
      return d;
    }
    if (stateSinceMs > 0L && nowMs - stateSinceMs < config.protectPersistenceMs) {
      d.reason = "persist";
      return d;
    }
    double cushion = Math.max(0.0, config.protectBreakevenCushionTicks) * ts;
    double beCandidate = position.side == study_examples.lzs.model.LzsSide.LONG
        ? position.entryPrice + cushion
        : position.entryPrice - cushion;
    double candidate = beCandidate;
    String modeReason = "protectBE";

    boolean baiOk = metrics == null || !config.structuralProtectRequireBaiOk || (!Double.isNaN(metrics.bai) && metrics.bai >= 0.60);
    boolean drrOk = metrics == null || Double.isNaN(metrics.drr) || metrics.drr >= config.structuralProtectMinDrr;
    if (config.structuralProtectEnabled && baiOk && drrOk && !Double.isNaN(structuralRefPrice)) {
      double buffer = Math.max(0.0, config.structuralProtectBufferTicks) * ts;
      double structCandidate = position.side == study_examples.lzs.model.LzsSide.LONG
          ? structuralRefPrice - buffer
          : structuralRefPrice + buffer;
      boolean candidateInFront = Double.isNaN(position.lastPrice)
          || (position.side == study_examples.lzs.model.LzsSide.LONG ? structCandidate < position.lastPrice : structCandidate > position.lastPrice);
      if (candidateInFront) {
        if (position.side == study_examples.lzs.model.LzsSide.LONG) candidate = Math.max(candidate, structCandidate);
        else candidate = Math.min(candidate, structCandidate);
        if ((position.side == study_examples.lzs.model.LzsSide.LONG && candidate == structCandidate)
            || (position.side == study_examples.lzs.model.LzsSide.SHORT && candidate == structCandidate)) {
          modeReason = "protectStruct";
        }
      }
    }

    boolean tighter = Double.isNaN(position.stopPrice)
        || (position.side == study_examples.lzs.model.LzsSide.LONG ? candidate > position.stopPrice : candidate < position.stopPrice);
    if (!tighter) {
      d.action = LzsTradeManagementAction.KEEP_STOP;
      d.reason = "noTighten";
      return d;
    }
    d.action = LzsTradeManagementAction.TIGHTEN_STOP;
    d.targetStopPrice = candidate;
    d.reason = modeReason;
    return d;
  }
}
