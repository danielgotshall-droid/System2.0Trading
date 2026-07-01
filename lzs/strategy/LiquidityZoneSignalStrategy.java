package study_examples.lzs.strategy;

import java.lang.reflect.Method;
import java.util.Locale;

import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.common.desc.*;
import com.motivewave.platform.sdk.draw.Label;
import com.motivewave.platform.sdk.order_mgmt.OrderContext;
import com.motivewave.platform.sdk.study.*;

import study_examples.lzs.LiquidityZoneSignalStudy;
import study_examples.lzs.LzsStudySignals;
import study_examples.lzs.LzsStudyValues;
import study_examples.lzs.context.LzsContextIntent;
import study_examples.lzs.model.LzsSide;
import study_examples.lzs.strategy.management.LzsTradeManagementEngine;
import study_examples.lzs.strategy.management.LzsTradeManagementMetrics;
import study_examples.lzs.strategy.management.LzsTradeManagementScores;
import study_examples.lzs.strategy.management.LzsTradeManagementSnapshot;
import study_examples.lzs.strategy.management.LzsTradeManagementState;

/**
 * Phase 5A Step 3 strategy lifecycle + management foundation wrapper.
 *
 * <p>The study remains the signal/context producer. The strategy owns the real
 * position state and now also owns a lightweight, position-aware management
 * engine foundation (CSS / EXS / ERS plus raw metrics). The management engine is
 * informational only in this patch; it does not yet send protective orders.</p>
 */
@StudyHeader(
  namespace="custom.orderflow",
  id="LIQUIDITY_ZONE_SIGNAL_STRATEGY",
  name="Liquidity Zone Signal Strategy",
  desc="Strategy wrapper around the Liquidity Zone Signal study.",
  overlay=true,
  signals=true,
  strategy=true,
  autoEntry=true,
  supportsBarUpdates=true,
  requiresVolume=true,
  requiresBidAskHistory=true
)
public class LiquidityZoneSignalStrategy extends LiquidityZoneSignalStudy {
  private static final String STRAT_SHOW_HUD = "stratShowHud";
  private static final String STRAT_HUD_OFFSET_TICKS = "stratHudOffsetTicks";
  private static final String STRAT_USE_ZONE_BASED_INITIAL_STOP = "stratUseZoneBasedInitialStop";
  private static final String STRAT_ZONE_STOP_BUFFER_TICKS = "stratZoneStopBufferTicks";
  private final LzsStrategyConfig config;
  private final LzsPositionState position = new LzsPositionState();
  private final LzsTradeManagementEngine management;
  private String lastStatusLine = "STRAT FLAT";

  private String lastSignalLabel = "";
  private int lastSignalIndex = -1;
  private double lastSignalPrice = Double.NaN;
  private boolean lastSignalFoundBar = false;
  private String lastDecisionReason = "";
  private int lastDecisionCode = 0;
  private boolean lastDecisionCtxPass = false;
  private double lastDecisionCtxScore = Double.NaN;
  private double lastDecisionPathClear = Double.NaN;
  private String lastOrderAction = "";
  private int lastOrderQty = 0;
  private String lastOrderError = "";

  private Label strategyHudLabel;
  private String strategyHudTextCache = "";
  private double strategyHudPriceCache = Double.NaN;

  public LiquidityZoneSignalStrategy() {
    this(new LzsStrategyConfig());
  }

  public LiquidityZoneSignalStrategy(LzsStrategyConfig config) {
    this.config = config == null ? new LzsStrategyConfig() : config;
    this.management = new LzsTradeManagementEngine(this.config.management);
  }

  public LzsPositionState getPosition() {
    return position;
  }

  public String getLastStatusLine() {
    return lastStatusLine;
  }

  public LzsTradeManagementScores getManagementScores() {
    return management.getLastScores();
  }

  public LzsTradeManagementMetrics getManagementMetrics() {
    return management.getLastMetrics();
  }

  @Override
  public void initialize(Defaults defaults) {
    super.initialize(defaults);

    SettingsDescriptor sd = getSettingsDescriptor();
    if (sd != null) {
      SettingTab stratHudTab = sd.addTab("Strategy HUD / Stops");
      SettingGroup hud = stratHudTab.addGroup("Strategy HUD");
      hud.addRow(new BooleanDescriptor(STRAT_SHOW_HUD, "Show Strategy HUD", config.showStrategyHud));
      hud.addRow(new IntegerDescriptor(STRAT_HUD_OFFSET_TICKS, "Strategy HUD Offset (ticks)", config.strategyHudOffsetTicks, 0, 200, 1));

      SettingGroup risk = stratHudTab.addGroup("Initial Stop");
      risk.addRow(new BooleanDescriptor(STRAT_USE_ZONE_BASED_INITIAL_STOP, "Use Zone-Based Initial Stop", config.useZoneBasedInitialStop));
      risk.addRow(new DoubleDescriptor(STRAT_ZONE_STOP_BUFFER_TICKS, "Zone Stop Buffer (ticks)", config.zoneStopBufferTicks, 0.0, 50.0, 0.25));
    }

    RuntimeDescriptor rd = getRuntimeDescriptor();
    if (rd == null) return;

    rd.exportValue(new ValueDescriptor(LzsStrategyValues.POSITION_ACTIVE, "STRAT Position Active"));
    rd.exportValue(new ValueDescriptor(LzsStrategyValues.POSITION_SIDE, "STRAT Position Side"));
    rd.exportValue(new ValueDescriptor(LzsStrategyValues.POSITION_LIFECYCLE, "STRAT Position Lifecycle"));
    rd.exportValue(new ValueDescriptor(LzsStrategyValues.ENTRY_PRICE, "STRAT Entry Price"));
    rd.exportValue(new ValueDescriptor(LzsStrategyValues.INITIAL_STOP, "STRAT Initial Stop"));
    rd.exportValue(new ValueDescriptor(LzsStrategyValues.INITIAL_TARGET, "STRAT Initial Target"));
    rd.exportValue(new ValueDescriptor(LzsStrategyValues.SIGNAL_REFERENCE_PRICE, "STRAT Signal Reference Price"));
    rd.exportValue(new ValueDescriptor(LzsStrategyValues.LAST_PRICE, "STRAT Last Price"));
    rd.exportValue(new ValueDescriptor(LzsStrategyValues.MFE_TICKS, "STRAT MFE Ticks"));
    rd.exportValue(new ValueDescriptor(LzsStrategyValues.MAE_TICKS, "STRAT MAE Ticks"));

    rd.exportValue(new ValueDescriptor(LzsStrategyValues.MGMT_STATE, "STRAT MGMT State"));
    rd.exportValue(new ValueDescriptor(LzsStrategyValues.CSS, "STRAT CSS"));
    rd.exportValue(new ValueDescriptor(LzsStrategyValues.EXS, "STRAT EXS"));
    rd.exportValue(new ValueDescriptor(LzsStrategyValues.ERS, "STRAT ERS"));
    rd.exportValue(new ValueDescriptor(LzsStrategyValues.AER, "STRAT AER"));
    rd.exportValue(new ValueDescriptor(LzsStrategyValues.IC, "STRAT IC"));
    rd.exportValue(new ValueDescriptor(LzsStrategyValues.BAI, "STRAT BAI"));
    rd.exportValue(new ValueDescriptor(LzsStrategyValues.DRR, "STRAT DRR"));
    rd.exportValue(new ValueDescriptor(LzsStrategyValues.OER, "STRAT OER"));
    rd.exportValue(new ValueDescriptor(LzsStrategyValues.DFSF, "STRAT DFSF"));
    rd.exportValue(new ValueDescriptor(LzsStrategyValues.ERD, "STRAT ERD"));

    rd.exportValue(new ValueDescriptor(LzsStrategyValues.LAST_SIGNAL_SIDE, "STRAT Last Signal Side"));
    rd.exportValue(new ValueDescriptor(LzsStrategyValues.LAST_SIGNAL_INDEX, "STRAT Last Signal Index"));
    rd.exportValue(new ValueDescriptor(LzsStrategyValues.LAST_SIGNAL_PRICE, "STRAT Last Signal Price"));
    rd.exportValue(new ValueDescriptor(LzsStrategyValues.LAST_SIGNAL_FOUND_BAR, "STRAT Last Signal Found Bar"));
    rd.exportValue(new ValueDescriptor(LzsStrategyValues.LAST_DECISION_CODE, "STRAT Last Decision Code"));
    rd.exportValue(new ValueDescriptor(LzsStrategyValues.LAST_CTX_PASS, "STRAT Last Context Pass"));
    rd.exportValue(new ValueDescriptor(LzsStrategyValues.LAST_CTX_SCORE, "STRAT Last Context Score"));
    rd.exportValue(new ValueDescriptor(LzsStrategyValues.LAST_PATH_CLEAR, "STRAT Last Path Clear"));
    rd.exportValue(new ValueDescriptor(LzsStrategyValues.LAST_ORDER_ACTION, "STRAT Last Order Action"));
    rd.exportValue(new ValueDescriptor(LzsStrategyValues.LAST_ORDER_QTY, "STRAT Last Order Quantity"));
    rd.exportValue(new ValueDescriptor(LzsStrategyValues.LAST_ORDER_ERROR, "STRAT Last Order Error"));
  }

  @Override
  public void onActivate(OrderContext ctx) {
    syncPositionWithContext(ctx);
    if (!config.enabled) {
      updateRuntimeState("strategyDisabled");
      updateStrategyHud(ctx, true);
      return;
    }
    if (!getSettings().isEnterOnActivate()) {
      updateRuntimeState("activated");
      updateStrategyHud(ctx, true);
      return;
    }

    int index = resolveRuntimeIndex(ctx);
    if (index < 0) {
      updateRuntimeState("activateNoBar");
      updateStrategyHud(ctx, true);
      return;
    }

    LzsStrategyDecision decision = evaluate(resolveSeries(ctx), index, resolveLastPrice(ctx, index), resolveTickSize(ctx), resolveOrderQuantity(ctx));
    maybeSubmitEntry(ctx, decision, signalLabelFromDecision(decision));
    updateStrategyHud(ctx, true);
  }

  @Override
  public void onSignal(OrderContext ctx, Object signal) {
    syncPositionWithContext(ctx);
    if (!config.enabled) {
      updateRuntimeState("strategyDisabled");
      updateStrategyHud(ctx, true);
      return;
    }

    LzsStrategyDecision decision = decisionFromSignal(ctx, signal);
    maybeSubmitEntry(ctx, decision, signalToLabel(signal));
    updateStrategyHud(ctx, true);
  }

  @Override
  public void onBarUpdate(OrderContext ctx) {
    syncPositionWithContext(ctx);
    if (enforceInitialStop(ctx)) {
      updateStrategyHud(ctx, true);
      return;
    }
    refreshManagement(ctx);
    updateStrategyHud(ctx, false);
  }

  @Override
  public void onBarClose(OrderContext ctx) {
    syncPositionWithContext(ctx);
    if (enforceInitialStop(ctx)) {
      updateStrategyHud(ctx, true);
      return;
    }
    refreshManagement(ctx);
    updateStrategyHud(ctx, false);
  }

  @Override
  public void onDeactivate(OrderContext ctx) {
    syncPositionWithContext(ctx);
    if (ctx != null && ctx.getPosition() != 0) {
      position.markExitPending();
      updateRuntimeState("deactivateClose");
      ctx.closeAtMarket();
      updateStrategyHud(ctx, true);
      return;
    }
    position.reset();
    management.reset();
    updateRuntimeState("deactivatedFlat");
    clearStrategyHud(true);
  }

  @Override
  public void onReset(OrderContext ctx) {
    position.reset();
    management.reset();
    updateRuntimeState("reset");
    clearStrategyHud(true);
  }

  @Override
  public void onPositionClosed(OrderContext ctx) {
    position.reset();
    management.reset();
    updateRuntimeState("positionClosed");
    updateStrategyHud(ctx, true);
  }

  public LzsStrategyDecision evaluate(DataSeries series, int index, double lastPrice, double tickSize, int quantity) {
    if (!config.enabled) return LzsStrategyDecision.none("strategyDisabled");
    if (!position.isFlat()) return LzsStrategyDecision.none("positionActive");

    LzsStrategySignalView v = LzsStrategySignalView.from(series, index);

    if (config.enableLong) {
      LzsStrategyDecision longDecision = evaluateLong(v, lastPrice, tickSize, quantity);
      if (longDecision.decision == LzsEntryDecision.LONG) return longDecision;
    }

    if (config.enableShort) {
      LzsStrategyDecision shortDecision = evaluateShort(v, lastPrice, tickSize, quantity);
      if (shortDecision.decision == LzsEntryDecision.SHORT) return shortDecision;
    }

    return LzsStrategyDecision.none("noEligibleSignal");
  }

  public void acceptPlannedEntry(LzsStrategyDecision decision, long signalTime, int quantity) {
    if (decision == null || decision.side == null) return;
    position.markEntryPending(
      decision.side,
      decision.entryPrice,
      decision.stopPrice,
      decision.targetPrice,
      decision.signalReferencePrice,
      quantity,
      signalTime,
      decision.reason
    );
  }

  public void confirmEntryFill(long fillTime, double fillPrice, int filledQty) {
    position.markActive(fillTime, fillPrice, filledQty);
    management.reset();
  }

  public void confirmExitFill() {
    position.reset();
    management.reset();
  }

  public String buildStatusLine() {
    if (position.isFlat()) return "STRAT FLAT";
    StringBuilder sb = new StringBuilder();
    sb.append("STRAT ").append(position.side).append(' ').append(position.lifecycle)
      .append(" | EP ").append(fmt(position.entryPrice))
      .append(" | STP ").append(fmt(position.stopPrice))
      .append(" | TGT ").append(fmt(position.targetPrice));

    LzsTradeManagementScores scores = management.getLastScores();
    if (scores != null && scores.state != LzsTradeManagementState.MGMT_INACTIVE) {
      sb.append(" | MGMT ").append(shortState(scores.state))
        .append(" CSS ").append(fmt(scores.css))
        .append(" EXS ").append(fmt(scores.exs))
        .append(" ERS ").append(fmt(scores.ers));
      if (scores.reasons != null && !scores.reasons.isEmpty()) sb.append(" | ").append(scores.reasons);
    }

    if (position.entryReason != null && !position.entryReason.isEmpty()) sb.append(" | ").append(position.entryReason);
    if (lastSignalLabel != null && !lastSignalLabel.isEmpty()) {
      sb.append(" | SIG ").append(lastSignalLabel);
      if (lastSignalIndex >= 0) sb.append('@').append(lastSignalIndex);
      if (lastSignalFoundBar) sb.append(" found");
      if (!Double.isNaN(lastSignalPrice)) sb.append(" px ").append(fmt(lastSignalPrice));
    }
    if (lastDecisionReason != null && !lastDecisionReason.isEmpty()) sb.append(" | DEC ").append(lastDecisionReason);
    if (lastOrderAction != null && !lastOrderAction.isEmpty()) sb.append(" | ORD ").append(lastOrderAction);
    if (lastOrderError != null && !lastOrderError.isEmpty()) sb.append(" | ERR ").append(lastOrderError);
    return sb.toString();
  }

  private LzsStrategyDecision decisionFromSignal(OrderContext ctx, Object signal) {
    resetSignalDiagnostics(signalToLabel(signal));
    if (signal != LzsStudySignals.LZS_LONG && signal != LzsStudySignals.LZS_SHORT) {
      noteDecision("foreignSignal", 1, false, Double.NaN, Double.NaN);
      return LzsStrategyDecision.none("foreignSignal");
    }
    if (!position.isFlat() || (ctx != null && ctx.getPosition() != 0)) {
      noteDecision("positionActive", 2, false, Double.NaN, Double.NaN);
      return LzsStrategyDecision.none("positionActive");
    }

    DataSeries series = resolveSeries(ctx);
    int index = resolveFiredSignalIndex(ctx, signal);
    lastSignalIndex = index;
    lastSignalFoundBar = index >= 0;
    if (series == null || index < 0 || index >= series.size()) {
      noteDecision("noFiredBar", 3, false, Double.NaN, Double.NaN);
      return LzsStrategyDecision.none("noFiredBar");
    }

    double lastPrice = resolveLastPrice(ctx, index);
    lastSignalPrice = lastPrice;
    double tickSize = resolveTickSize(ctx);
    int qty = resolveOrderQuantity(ctx);
    LzsStrategySignalView v = LzsStrategySignalView.from(series, index);

    if (signal == LzsStudySignals.LZS_LONG) {
      return buildLongFromSignal(v, lastPrice, tickSize, qty);
    }
    return buildShortFromSignal(v, lastPrice, tickSize, qty);
  }

  private void maybeSubmitEntry(OrderContext ctx, LzsStrategyDecision decision, String sourceLabel) {
    if (ctx == null) {
      noteDecision(decision == null ? "noCtx" : decision.reason, 4, false, Double.NaN, Double.NaN);
      updateRuntimeState(decision == null ? "noCtx" : decision.reason);
      updateStrategyHud(ctx, true);
      return;
    }
    if (decision == null || decision.decision == LzsEntryDecision.NONE || decision.side == null) {
      if (decision != null) noteDecision(decision.reason, 5, false, Double.NaN, Double.NaN);
      updateRuntimeState(decision == null ? "noDecision" : decision.reason);
      writeStrategyValues(ctx);
      updateStrategyHud(ctx, true);
      return;
    }
    if (ctx.getPosition() != 0 || !position.isFlat()) {
      noteDecision("positionActive", 2, false, Double.NaN, Double.NaN);
      updateRuntimeState("positionActive");
      writeStrategyValues(ctx);
      updateStrategyHud(ctx, true);
      return;
    }

    int qty = Math.max(1, resolveOrderQuantity(ctx));
    long now = System.currentTimeMillis();
    acceptPlannedEntry(decision, now, qty);
    noteDecision(decision.reason, decision.decision == LzsEntryDecision.LONG ? 10 : -10,
        lastDecisionCtxPass, lastDecisionCtxScore, lastDecisionPathClear);
    updateRuntimeState(sourceLabel == null ? "entryPending" : sourceLabel + " pending");

    try {
      lastOrderQty = qty;
      if (decision.decision == LzsEntryDecision.LONG) {
        lastOrderAction = "BUY_SENT";
        ctx.buy(qty);
      }
      else {
        lastOrderAction = "SELL_SENT";
        ctx.sell(qty);
      }
      lastOrderError = "";
    }
    catch (Throwable t) {
      lastOrderAction = "ORDER_ERROR";
      lastOrderError = t == null ? "unknown" : (t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
      updateRuntimeState(lastOrderError);
      writeStrategyValues(ctx);
      updateStrategyHud(ctx, true);
      return;
    }

    int brokerPosition = ctx.getPosition();
    if (brokerPosition != 0) {
      confirmEntryFill(now, decision.entryPrice, Math.abs(brokerPosition));
      lastOrderAction = "FILLED";
    }
    else {
      lastOrderAction = "SUBMITTED_NO_POSITION";
    }
    syncPositionWithContext(ctx);
    refreshManagement(ctx);
    updateStrategyHud(ctx, true);
  }

  private void syncPositionWithContext(OrderContext ctx) {
    if (ctx == null) {
      lastStatusLine = buildStatusLine();
      return;
    }

    int brokerPosition = ctx.getPosition();
    if (brokerPosition == 0) {
      if (position.lifecycle == LzsPositionLifecycle.ENTRY_PENDING && (System.currentTimeMillis() - position.entryTime) < 2500L) {
        updateRuntimeState("awaitFill");
        writeStrategyValues(ctx);
        return;
      }
      if (!position.isFlat()) position.reset();
      management.reset();
      updateRuntimeState("flat");
      writeStrategyValues(ctx);
      return;
    }

    if (position.isFlat()) {
      LzsSide side = brokerPosition > 0 ? LzsSide.LONG : LzsSide.SHORT;
      double px = resolveLastPrice(ctx, resolveRuntimeIndex(ctx));
      position.markEntryPending(side, px, Double.NaN, Double.NaN, px, Math.abs(brokerPosition), System.currentTimeMillis(), "sync");
      position.markActive(System.currentTimeMillis(), px, Math.abs(brokerPosition));
      management.reset();
    }
    else if (!position.isActive()) {
      position.markActive(System.currentTimeMillis(), position.entryPrice, Math.abs(brokerPosition));
      management.reset();
    }

    updateRuntimeState("active");
    writeStrategyValues(ctx);
  }

  private void refreshManagement(OrderContext ctx) {
    if (ctx == null) {
      updateRuntimeState("noCtx");
      return;
    }
    int index = resolveRuntimeIndex(ctx);
    if (index < 0) {
      updateRuntimeState("noBar");
      return;
    }

    if (!config.managementEnabled || position.isFlat() || !position.isActive()) {
      management.reset();
      writeStrategyValues(ctx);
      updateRuntimeState(position.isFlat() ? "flat" : "mgmtOff");
      return;
    }

    DataSeries series = resolveSeries(ctx);
    if (series == null || index >= series.size()) {
      updateRuntimeState("noSeries");
      return;
    }

    double tickSize = resolveTickSize(ctx);
    double lastPrice = resolveLastPrice(ctx, index);
    position.updateMarket(lastPrice, tickSize);

    LzsTradeManagementSnapshot snapshot = buildManagementSnapshot(series, index, tickSize, System.currentTimeMillis());
    management.update(snapshot);
    writeStrategyValues(ctx);
    updateRuntimeState("mgmt");
  }

  private LzsTradeManagementSnapshot buildManagementSnapshot(DataSeries series, int index, double tickSize, long nowMs) {
    LzsTradeManagementSnapshot s = new LzsTradeManagementSnapshot();
    s.nowMs = nowMs;
    s.index = index;
    s.side = position.side;
    s.tickSize = tickSize;
    s.open = series.getOpen(index);
    s.high = series.getHigh(index);
    s.low = series.getLow(index);
    s.close = series.getClose(index);
    s.volume = series.getVolume(index);
    s.askVolume = optionalSeriesValue(series, index, "getAskVolume", "getAskTradeVolume");
    s.bidVolume = optionalSeriesValue(series, index, "getBidVolume", "getBidTradeVolume");
    if (Double.isNaN(s.askVolume) || Double.isNaN(s.bidVolume)) {
      double body = s.close - s.open;
      double range = Math.max(tickSize, s.high - s.low);
      double bodyFrac = Math.min(1.0, Math.abs(body) / range);
      double proxyDelta = Math.signum(body) * nz(s.volume) * bodyFrac;
      s.delta = proxyDelta;
      if (proxyDelta >= 0) {
        s.askVolume = nz(s.volume + proxyDelta) * 0.5;
        s.bidVolume = nz(s.volume - proxyDelta) * 0.5;
      }
      else {
        s.askVolume = nz(s.volume + proxyDelta) * 0.5;
        s.bidVolume = nz(s.volume - proxyDelta) * 0.5;
      }
    }
    else {
      s.delta = s.askVolume - s.bidVolume;
    }
    s.entryPrice = position.entryPrice;
    s.referencePrice = Double.isNaN(position.signalReferencePrice) ? position.entryPrice : position.signalReferencePrice;
    s.stopPrice = position.stopPrice;
    s.targetPrice = position.targetPrice;
    s.lastPrice = position.lastPrice;
    s.mfeTicks = position.mfeTicks;
    s.maeTicks = position.maeTicks;
    return s;
  }

  private void writeStrategyValues(OrderContext ctx) {
    DataSeries series = resolveSeries(ctx);
    int index = resolveRuntimeIndex(ctx);
    if (series == null || index < 0 || index >= series.size()) return;

    series.setBoolean(index, LzsStrategyValues.POSITION_ACTIVE, position.isActive());
    series.setDouble(index, LzsStrategyValues.POSITION_SIDE, position.side == null ? Double.NaN : (position.side == LzsSide.LONG ? 1.0 : -1.0));
    series.setDouble(index, LzsStrategyValues.POSITION_LIFECYCLE, (double) position.lifecycle.ordinal());
    series.setDouble(index, LzsStrategyValues.ENTRY_PRICE, position.entryPrice);
    series.setDouble(index, LzsStrategyValues.INITIAL_STOP, position.stopPrice);
    series.setDouble(index, LzsStrategyValues.INITIAL_TARGET, position.targetPrice);
    series.setDouble(index, LzsStrategyValues.SIGNAL_REFERENCE_PRICE, position.signalReferencePrice);
    series.setDouble(index, LzsStrategyValues.LAST_PRICE, position.lastPrice);
    series.setDouble(index, LzsStrategyValues.MFE_TICKS, position.mfeTicks);
    series.setDouble(index, LzsStrategyValues.MAE_TICKS, position.maeTicks);

    LzsTradeManagementScores scores = management.getLastScores();
    LzsTradeManagementMetrics metrics = management.getLastMetrics();
    series.setDouble(index, LzsStrategyValues.MGMT_STATE, scores == null ? Double.NaN : (double) scores.state.ordinal());
    series.setDouble(index, LzsStrategyValues.CSS, scores == null ? Double.NaN : scores.css);
    series.setDouble(index, LzsStrategyValues.EXS, scores == null ? Double.NaN : scores.exs);
    series.setDouble(index, LzsStrategyValues.ERS, scores == null ? Double.NaN : scores.ers);
    series.setDouble(index, LzsStrategyValues.AER, metrics == null ? Double.NaN : metrics.aer);
    series.setDouble(index, LzsStrategyValues.IC, metrics == null ? Double.NaN : metrics.ic);
    series.setDouble(index, LzsStrategyValues.BAI, metrics == null ? Double.NaN : metrics.bai);
    series.setDouble(index, LzsStrategyValues.DRR, metrics == null ? Double.NaN : metrics.drr);
    series.setDouble(index, LzsStrategyValues.OER, metrics == null ? Double.NaN : metrics.oer);
    series.setDouble(index, LzsStrategyValues.DFSF, metrics == null ? Double.NaN : metrics.dfsf);
    series.setDouble(index, LzsStrategyValues.ERD, metrics == null ? Double.NaN : metrics.erd);
    series.setDouble(index, LzsStrategyValues.LAST_SIGNAL_SIDE,
        "signalLong".equals(lastSignalLabel) ? 1.0 : ("signalShort".equals(lastSignalLabel) ? -1.0 : Double.NaN));
    series.setDouble(index, LzsStrategyValues.LAST_SIGNAL_INDEX, lastSignalIndex < 0 ? Double.NaN : (double) lastSignalIndex);
    series.setDouble(index, LzsStrategyValues.LAST_SIGNAL_PRICE, lastSignalPrice);
    series.setBoolean(index, LzsStrategyValues.LAST_SIGNAL_FOUND_BAR, lastSignalFoundBar);
    series.setDouble(index, LzsStrategyValues.LAST_DECISION_CODE, (double) lastDecisionCode);
    series.setBoolean(index, LzsStrategyValues.LAST_CTX_PASS, lastDecisionCtxPass);
    series.setDouble(index, LzsStrategyValues.LAST_CTX_SCORE, lastDecisionCtxScore);
    series.setDouble(index, LzsStrategyValues.LAST_PATH_CLEAR, lastDecisionPathClear);
    series.setDouble(index, LzsStrategyValues.LAST_ORDER_ACTION,
        "BUY_SENT".equals(lastOrderAction) ? 1.0 :
        ("SELL_SENT".equals(lastOrderAction) ? -1.0 :
        ("FILLED".equals(lastOrderAction) ? 2.0 :
        ("SUBMITTED_NO_POSITION".equals(lastOrderAction) ? 3.0 :
        ("STOP_EXIT_SENT".equals(lastOrderAction) ? 4.0 :
        ("ORDER_ERROR".equals(lastOrderAction) ? -2.0 :
        ("STOP_EXIT_ERROR".equals(lastOrderAction) ? -4.0 : Double.NaN)))))));
    series.setDouble(index, LzsStrategyValues.LAST_ORDER_QTY, lastOrderQty <= 0 ? Double.NaN : (double) lastOrderQty);
    series.setBoolean(index, LzsStrategyValues.LAST_ORDER_ERROR, lastOrderError != null && !lastOrderError.isEmpty());
  }

  private LzsStrategyDecision evaluateLong(LzsStrategySignalView v, double lastPrice, double tickSize, int quantity) {
    if (!v.isLongSignalReady(config.minEligiblePhaseOrdinal)) return LzsStrategyDecision.none("longPhase");
    if (config.requireContextPass && !v.longContextPass) return LzsStrategyDecision.none("longCtx");
    if (!Double.isNaN(v.longContextScore) && v.longContextScore < config.minLongContextScore) return LzsStrategyDecision.none("longMrg");
    if (config.requirePathClear && !Double.isNaN(v.longPathClearTicks) && v.longPathClearTicks < config.minPathClearTicks) return LzsStrategyDecision.none("longPath");

    LzsStrategyDecision d = new LzsStrategyDecision();
    d.decision = LzsEntryDecision.LONG;
    d.side = LzsSide.LONG;
    d.entryPrice = lastPrice;
    d.stopPrice = computeInitialLongStop(v, lastPrice, tickSize);
    d.targetPrice = lastPrice + config.initialTargetTicks * tickSize;
    d.signalReferencePrice = Double.isNaN(v.longExecRef) ? lastPrice : v.longExecRef;
    d.reason = buildEntryReason("LONG", v.longContextScore, v.longIntent, v.longPathClearTicks);
    return d;
  }

  private LzsStrategyDecision evaluateShort(LzsStrategySignalView v, double lastPrice, double tickSize, int quantity) {
    if (!v.isShortSignalReady(config.minEligiblePhaseOrdinal)) return LzsStrategyDecision.none("shortPhase");
    if (config.requireContextPass && !v.shortContextPass) return LzsStrategyDecision.none("shortCtx");
    if (!Double.isNaN(v.shortContextScore) && v.shortContextScore < config.minShortContextScore) return LzsStrategyDecision.none("shortMrg");
    if (config.requirePathClear && !Double.isNaN(v.shortPathClearTicks) && v.shortPathClearTicks < config.minPathClearTicks) return LzsStrategyDecision.none("shortPath");

    LzsStrategyDecision d = new LzsStrategyDecision();
    d.decision = LzsEntryDecision.SHORT;
    d.side = LzsSide.SHORT;
    d.entryPrice = lastPrice;
    d.stopPrice = computeInitialShortStop(v, lastPrice, tickSize);
    d.targetPrice = lastPrice - config.initialTargetTicks * tickSize;
    d.signalReferencePrice = Double.isNaN(v.shortExecRef) ? lastPrice : v.shortExecRef;
    d.reason = buildEntryReason("SHORT", v.shortContextScore, v.shortIntent, v.shortPathClearTicks);
    return d;
  }

  private LzsStrategyDecision buildLongFromSignal(LzsStrategySignalView v, double lastPrice, double tickSize, int quantity) {
    lastDecisionCtxPass = v.longContextPass;
    lastDecisionCtxScore = v.longContextScore;
    lastDecisionPathClear = v.longPathClearTicks;

    if (!config.enableLong) {
      noteDecision("longDisabled", 6, v.longContextPass, v.longContextScore, v.longPathClearTicks);
      return LzsStrategyDecision.none("longDisabled");
    }

    LzsStrategyDecision d = new LzsStrategyDecision();
    d.decision = LzsEntryDecision.LONG;
    d.side = LzsSide.LONG;
    d.entryPrice = lastPrice;
    d.stopPrice = computeInitialLongStop(v, lastPrice, tickSize);
    d.targetPrice = lastPrice + config.initialTargetTicks * tickSize;
    d.signalReferencePrice = Double.isNaN(v.longExecRef) ? lastPrice : v.longExecRef;
    d.reason = buildEntryReason("LONG", v.longContextScore, v.longIntent, v.longPathClearTicks);
    noteDecision(d.reason, 10, v.longContextPass, v.longContextScore, v.longPathClearTicks);
    return d;
  }

  private LzsStrategyDecision buildShortFromSignal(LzsStrategySignalView v, double lastPrice, double tickSize, int quantity) {
    lastDecisionCtxPass = v.shortContextPass;
    lastDecisionCtxScore = v.shortContextScore;
    lastDecisionPathClear = v.shortPathClearTicks;

    if (!config.enableShort) {
      noteDecision("shortDisabled", 7, v.shortContextPass, v.shortContextScore, v.shortPathClearTicks);
      return LzsStrategyDecision.none("shortDisabled");
    }

    LzsStrategyDecision d = new LzsStrategyDecision();
    d.decision = LzsEntryDecision.SHORT;
    d.side = LzsSide.SHORT;
    d.entryPrice = lastPrice;
    d.stopPrice = computeInitialShortStop(v, lastPrice, tickSize);
    d.targetPrice = lastPrice - config.initialTargetTicks * tickSize;
    d.signalReferencePrice = Double.isNaN(v.shortExecRef) ? lastPrice : v.shortExecRef;
    d.reason = buildEntryReason("SHORT", v.shortContextScore, v.shortIntent, v.shortPathClearTicks);
    noteDecision(d.reason, -10, v.shortContextPass, v.shortContextScore, v.shortPathClearTicks);
    return d;
  }

  private String buildEntryReason(String side, double score, LzsContextIntent intent, double pathClearTicks) {
    StringBuilder sb = new StringBuilder();
    sb.append(side).append("Step3");
    if (!Double.isNaN(score)) sb.append(" mrg=").append(fmt(score));
    if (intent != null) sb.append(' ').append(intent.shortLabel());
    if (!Double.isNaN(pathClearTicks)) sb.append(" path=").append(fmt(pathClearTicks)).append('t');
    return sb.toString();
  }


  private double computeInitialLongStop(LzsStrategySignalView v, double lastPrice, double tickSize) {
    double configuredBuffer = zoneStopBufferTicks();
    double bufferTicks = Math.max(0.0, configuredBuffer > 0.0 ? configuredBuffer : config.initialStopTicks);
    if (useZoneBasedInitialStop() && v != null) {
      double zoneLow = !Double.isNaN(v.longSignalZoneLow) ? v.longSignalZoneLow : v.longZoneLow;
      if (!Double.isNaN(zoneLow)) {
        return zoneLow - (bufferTicks * tickSize);
      }
    }
    return lastPrice - config.initialStopTicks * tickSize;
  }

  private double computeInitialShortStop(LzsStrategySignalView v, double lastPrice, double tickSize) {
    double configuredBuffer = zoneStopBufferTicks();
    double bufferTicks = Math.max(0.0, configuredBuffer > 0.0 ? configuredBuffer : config.initialStopTicks);
    if (useZoneBasedInitialStop() && v != null) {
      double zoneHigh = !Double.isNaN(v.shortSignalZoneHigh) ? v.shortSignalZoneHigh : v.shortZoneHigh;
      if (!Double.isNaN(zoneHigh)) {
        return zoneHigh + (bufferTicks * tickSize);
      }
    }
    return lastPrice + config.initialStopTicks * tickSize;
  }

  private boolean enforceInitialStop(OrderContext ctx) {
    if (ctx == null || position.isFlat() || !position.isActive() || position.lifecycle == LzsPositionLifecycle.EXIT_PENDING) return false;
    if (Double.isNaN(position.stopPrice)) return false;

    DataSeries series = resolveSeries(ctx);
    int index = resolveRuntimeIndex(ctx);
    if (series == null || index < 0 || index >= series.size()) return false;

    double last = resolveLastPrice(ctx, index);
    double low = series.getLow(index);
    double high = series.getHigh(index);
    boolean hit;
    if (position.side == LzsSide.LONG) {
      hit = (!Double.isNaN(last) && last <= position.stopPrice) || (!Double.isNaN(low) && low <= position.stopPrice);
    }
    else {
      hit = (!Double.isNaN(last) && last >= position.stopPrice) || (!Double.isNaN(high) && high >= position.stopPrice);
    }
    if (!hit) return false;

    position.markExitPending();
    management.reset();
    lastOrderAction = "STOP_EXIT_SENT";
    lastOrderQty = position.quantity;
    lastOrderError = "";
    updateRuntimeState("stopHit");
    writeStrategyValues(ctx);
    try {
      ctx.closeAtMarket();
    }
    catch (Throwable t) {
      lastOrderAction = "STOP_EXIT_ERROR";
      lastOrderError = t == null ? "unknown" : (t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
      updateRuntimeState(lastOrderError);
    }
    return true;
  }

  private int resolveRuntimeIndex(OrderContext ctx) {
    DataSeries series = resolveSeries(ctx);
    if (series == null || series.size() <= 0) return -1;
    return series.size() - 1;
  }

  private void updateStrategyHud(OrderContext ctx, boolean force) {
    if (!showStrategyHud()) {
      clearStrategyHud(false);
      return;
    }
    DataSeries series = resolveSeries(ctx);
    int index = resolveRuntimeIndex(ctx);
    if (ctx == null || series == null || index < 0 || index >= series.size()) {
      clearStrategyHud(false);
      return;
    }

    double tick = resolveTickSize(ctx);
    double anchorPrice = Math.max(series.getHigh(index), Double.isNaN(position.lastPrice) ? series.getClose(index) : position.lastPrice)
        + (strategyHudOffsetTicks() * tick);
    String text = buildStrategyHudText();
    boolean sameText = text.equals(strategyHudTextCache);
    boolean samePrice = !Double.isNaN(strategyHudPriceCache)
        && Math.abs(strategyHudPriceCache - anchorPrice) < Math.max(1e-9, 0.25 * Math.max(tick, 1e-9));
    if (!force && sameText && samePrice && strategyHudLabel != null) return;

    if (strategyHudLabel != null) removeFigure(strategyHudLabel);
    strategyHudLabel = new Label(new Coordinate(series.getStartTime(index), anchorPrice), text);
    strategyHudLabel.setShowLine(false);
    strategyHudLabel.setPosition(Enums.Position.TOP_RIGHT);
    strategyHudLabel.setOffsetPixels(20);
    addFigure(strategyHudLabel);
    strategyHudTextCache = text;
    strategyHudPriceCache = anchorPrice;
    notifyRedraw();
  }

  private String buildStrategyHudText() {
    StringBuilder sb = new StringBuilder();
    sb.append("STRAT HUD\n");
    if (position.isFlat()) {
      sb.append("FLAT");
      if (lastOrderAction != null && !lastOrderAction.isEmpty()) sb.append(" | ORD ").append(lastOrderAction);
      if (lastDecisionReason != null && !lastDecisionReason.isEmpty()) sb.append(" | DEC ").append(lastDecisionReason);
      return sb.toString();
    }

    sb.append("POS ").append(position.side).append(' ').append(position.lifecycle)
      .append(" Q ").append(position.quantity)
      .append(" | EP ").append(fmt(position.entryPrice))
      .append(" | STP ").append(fmt(position.stopPrice));
    if (!Double.isNaN(position.targetPrice)) sb.append(" | TGT ").append(fmt(position.targetPrice));
    if (!Double.isNaN(position.lastPrice)) sb.append(" | PX ").append(fmt(position.lastPrice));
    if (!Double.isNaN(position.mfeTicks)) sb.append(" | MFE ").append(fmt(position.mfeTicks)).append('t');
    if (!Double.isNaN(position.maeTicks)) sb.append(" | MAE ").append(fmt(position.maeTicks)).append('t');

    LzsTradeManagementScores scores = management.getLastScores();
    sb.append("\n");
    if (scores == null || scores.state == null) {
      sb.append("MGMT INACTIVE");
    }
    else {
      sb.append("MGMT ").append(shortState(scores.state))
        .append(" | CSS ").append(fmt(scores.css))
        .append(" | EXS ").append(fmt(scores.exs))
        .append(" | ERS ").append(fmt(scores.ers));
      if (scores.reasons != null && !scores.reasons.isEmpty()) sb.append(" | ").append(scores.reasons);
    }

    if (lastOrderAction != null && !lastOrderAction.isEmpty()) sb.append("\nORD ").append(lastOrderAction);
    if (lastOrderError != null && !lastOrderError.isEmpty()) sb.append(" | ERR ").append(lastOrderError);
    return sb.toString();
  }

  private void clearStrategyHud(boolean redraw) {
    if (strategyHudLabel != null) {
      removeFigure(strategyHudLabel);
      strategyHudLabel = null;
      strategyHudTextCache = "";
      strategyHudPriceCache = Double.NaN;
      if (redraw) notifyRedraw();
    }
  }

  private int resolveEvaluationIndex(OrderContext ctx) {
    DataSeries series = resolveSeries(ctx);
    if (series == null || series.size() <= 0) return -1;
    int index = series.isLastBarComplete() ? series.size() - 1 : series.size() - 2;
    return Math.max(-1, Math.min(index, series.size() - 1));
  }

  private int resolveFiredSignalIndex(OrderContext ctx, Object signal) {
    DataSeries series = resolveSeries(ctx);
    if (series == null || series.size() <= 0) return -1;

    Object firedKey = signal == LzsStudySignals.LZS_LONG ? LzsStudyValues.LONG_FIRED : LzsStudyValues.SHORT_FIRED;
    int start = series.size() - 1;
    int end = Math.max(0, start - 24);
    for (int i = start; i >= end; i--) {
      if (series.getBoolean(i, firedKey, false)) return i;
    }
    return start;
  }

  private DataSeries resolveSeries(OrderContext ctx) {
    if (ctx == null) return null;
    DataContext dc = ctx.getDataContext();
    return dc == null ? null : dc.getDataSeries();
  }

  private double resolveLastPrice(OrderContext ctx, int index) {
    DataSeries series = resolveSeries(ctx);
    if (series == null || index < 0 || index >= series.size()) return Double.NaN;
    return series.getClose(index);
  }

  private double resolveTickSize(OrderContext ctx) {
    return (ctx == null || ctx.getInstrument() == null) ? 0.25 : ctx.getInstrument().getTickSize();
  }

  private int resolveOrderQuantity(OrderContext ctx) {
    if (ctx == null || ctx.getInstrument() == null) return 1;
    int lots = Math.max(1, getSettings().getTradeLots());
    int defaultQty = Math.max(1, ctx.getInstrument().getDefaultQuantity());
    return Math.max(1, lots * defaultQty);
  }

  private boolean showStrategyHud() {
    return getSettings() == null ? config.showStrategyHud : getSettings().getBoolean(STRAT_SHOW_HUD, config.showStrategyHud);
  }

  private int strategyHudOffsetTicks() {
    return getSettings() == null ? config.strategyHudOffsetTicks : getSettings().getInteger(STRAT_HUD_OFFSET_TICKS, config.strategyHudOffsetTicks);
  }

  private boolean useZoneBasedInitialStop() {
    return getSettings() == null ? config.useZoneBasedInitialStop : getSettings().getBoolean(STRAT_USE_ZONE_BASED_INITIAL_STOP, config.useZoneBasedInitialStop);
  }

  private double zoneStopBufferTicks() {
    return getSettings() == null ? config.zoneStopBufferTicks : getSettings().getDouble(STRAT_ZONE_STOP_BUFFER_TICKS, config.zoneStopBufferTicks);
  }

  private void updateRuntimeState(String note) {
    lastStatusLine = buildStatusLine() + (note == null || note.isEmpty() ? "" : " | " + note);
  }

  private String signalLabelFromDecision(LzsStrategyDecision decision) {
    if (decision == null) return "";
    if (decision.decision == LzsEntryDecision.LONG) return "activateLong";
    if (decision.decision == LzsEntryDecision.SHORT) return "activateShort";
    return decision.reason;
  }

  private String signalToLabel(Object signal) {
    if (signal == LzsStudySignals.LZS_LONG) return "signalLong";
    if (signal == LzsStudySignals.LZS_SHORT) return "signalShort";
    return "signal";
  }

  private void resetSignalDiagnostics(String label) {
    lastSignalLabel = label == null ? "" : label;
    lastSignalIndex = -1;
    lastSignalPrice = Double.NaN;
    lastSignalFoundBar = false;
    lastDecisionReason = "";
    lastDecisionCode = 0;
    lastDecisionCtxPass = false;
    lastDecisionCtxScore = Double.NaN;
    lastDecisionPathClear = Double.NaN;
    lastOrderAction = "";
    lastOrderQty = 0;
    lastOrderError = "";
  }

  private void noteDecision(String reason, int code, boolean ctxPass, double ctxScore, double pathClear) {
    lastDecisionReason = reason == null ? "" : reason;
    lastDecisionCode = code;
    lastDecisionCtxPass = ctxPass;
    lastDecisionCtxScore = ctxScore;
    lastDecisionPathClear = pathClear;
  }

  private String shortState(LzsTradeManagementState state) {
    if (state == null) return "INACTIVE";
    switch (state) {
      case MGMT_HOLD: return "HOLD";
      case MGMT_PROTECT: return "PROTECT";
      case MGMT_EXIT_RISK: return "EXIT";
      case MGMT_FORCE_EXIT: return "FORCE";
      default: return "INACTIVE";
    }
  }

  private String fmt(double v) {
    return Double.isNaN(v) ? "NA" : String.format(Locale.US, "%.2f", v);
  }

  private double optionalSeriesValue(DataSeries series, int index, String... methodNames) {
    if (series == null || methodNames == null) return Double.NaN;
    for (String name : methodNames) {
      try {
        Method m = series.getClass().getMethod(name, int.class);
        Object out = m.invoke(series, index);
        if (out instanceof Number) return ((Number) out).doubleValue();
      }
      catch (Throwable ignored) { }
    }
    return Double.NaN;
  }

  private double nz(double v) {
    return Double.isNaN(v) ? 0.0 : v;
  }
}
