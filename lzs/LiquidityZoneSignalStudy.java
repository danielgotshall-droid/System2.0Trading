package study_examples.lzs;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.common.desc.*;
import com.motivewave.platform.sdk.draw.Label;
import com.motivewave.platform.sdk.study.*;

import study_examples.lzs.engine.LzsEngine;
import study_examples.lzs.model.*;
import study_examples.lzs.util.LzsFormatUtils;

/**
 * Phase 3 standalone Liquidity Zone Signal study.
 *
 * Performance constraints preserved:
 * - separate engine eval cadence from HUD refresh cadence
 * - open-mode eval throttle
 * - current-bar execution refresh only when a side is in play
 * - one HUD only, update-on-change
 * - bounded DOM scans for protection/path logic
 */
@StudyHeader(
  namespace="custom.orderflow",
  id="LIQUIDITY_ZONE_SIGNAL_STUDY",
  name="Liquidity Zone Signal Study",
  desc="Standalone lightweight intrabar liquidity-zone detection and signaling.",
  overlay=true,
  signals=true,
  supportsBarUpdates=true,
  requiresVolume=true,
  requiresBidAskHistory=true
)
public class LiquidityZoneSignalStudy extends Study {

  private enum Values {
    LONG_PHASE,
    SHORT_PHASE,
    LONG_SCORE,
    SHORT_SCORE,
    LONG_ZONE_LOW,
    LONG_ZONE_HIGH,
    SHORT_ZONE_LOW,
    SHORT_ZONE_HIGH,
    LONG_FIRED,
    SHORT_FIRED,
    LONG_REV_TICKS,
    SHORT_REV_TICKS,
    LONG_EXEC_REF,
    SHORT_EXEC_REF,
    LONG_REMAINING_PCT,
    SHORT_REMAINING_PCT,
    LONG_PATH_CLEAR,
    SHORT_PATH_CLEAR
  }

  private enum Signals {
    LZS_LONG,
    LZS_SHORT
  }

  private static final String ENABLE_LZS = "ENABLE_LZS";
  private static final String ENABLE_LONG = "ENABLE_LONG";
  private static final String ENABLE_SHORT = "ENABLE_SHORT";
  private static final String EVAL_MIN_INTERVAL_MS = "EVAL_MIN_INTERVAL_MS";
  private static final String DOM_LEVELS_TO_CAPTURE = "DOM_LEVELS_TO_CAPTURE";
  private static final String DOM_WINDOW_MAX_UPDATES = "DOM_WINDOW_MAX_UPDATES";
  private static final String USE_RTH_TICKS = "USE_RTH_TICKS";
  private static final String USE_HIST_BARS = "USE_HIST_BARS";

  private static final String ZONE_MIN_SIZE = "ZONE_MIN_SIZE";
  private static final String ZONE_MIN_ROW_PCT_OF_ANCHOR = "ZONE_MIN_ROW_PCT_OF_ANCHOR";
  private static final String ZONE_MIN_CONTIG_ROWS = "ZONE_MIN_CONTIG_ROWS";
  private static final String ZONE_MAX_GAP_ROWS = "ZONE_MAX_GAP_ROWS";
  private static final String ZONE_MAX_HEIGHT_TICKS = "ZONE_MAX_HEIGHT_TICKS";
  private static final String ZONE_MAX_DISTANCE_TICKS = "ZONE_MAX_DISTANCE_TICKS";

  private static final String ARM_PROX_TICKS = "ARM_PROX_TICKS";
  private static final String EXEC_PROX_TICKS = "EXEC_PROX_TICKS";
  private static final String MIN_BUBBLE_SIZE = "MIN_BUBBLE_SIZE";
  private static final String MIN_BUBBLE_COUNT = "MIN_BUBBLE_COUNT";
  private static final String MIN_AGGR_SHARE = "MIN_AGGR_SHARE";
  private static final String MAX_UPDATES_ARM_TO_EXEC = "MAX_UPDATES_ARM_TO_EXEC";
  private static final String MAX_UPDATES_TO_REVERSE = "MAX_UPDATES_TO_REVERSE";
  private static final String MIN_REVERSAL_TICKS = "MIN_REVERSAL_TICKS";

  private static final String REQUIRE_RELOAD = "REQUIRE_RELOAD";
  private static final String MIN_RELOAD_PCT = "MIN_RELOAD_PCT";
  private static final String MIN_REMAINING_ZONE_PCT = "MIN_REMAINING_ZONE_PCT";
  private static final String REJECT_IF_CONSUMED = "REJECT_IF_CONSUMED";
  private static final String MIN_OPEN_PATH_TICKS = "MIN_OPEN_PATH_TICKS";
  private static final String MAX_OPPOSING_BLOCK_IN_PATH = "MAX_OPPOSING_BLOCK_IN_PATH";
  private static final String ATTRACTION_PENALTY_LOOKAHEAD = "ATTRACTION_PENALTY_LOOKAHEAD";

  private static final String MIN_MS_BETWEEN_SAME_ZONE_SIGNALS = "MIN_MS_BETWEEN_SAME_ZONE_SIGNALS";
  private static final String MIN_BARS_BETWEEN_SAME_SIDE_SIGNALS = "MIN_BARS_BETWEEN_SAME_SIDE_SIGNALS";

  private static final String SHOW_HUD = "SHOW_HUD";
  private static final String SHOW_PHASE_DETAILS = "SHOW_PHASE_DETAILS";
  private static final String SHOW_CANDIDATE_METRICS = "SHOW_CANDIDATE_METRICS";
  private static final String HUD_OFFSET_TICKS = "HUD_OFFSET_TICKS";
  private static final String HUD_REFRESH_INTERVAL_MS = "HUD_REFRESH_INTERVAL_MS";

  private static final String OPEN_MODE_THROTTLE_ENABLED = "OPEN_MODE_THROTTLE_ENABLED";
  private static final String OPEN_MODE_MINUTES = "OPEN_MODE_MINUTES";
  private static final String OPEN_MODE_EVAL_INTERVAL_MS = "OPEN_MODE_EVAL_INTERVAL_MS";
  private static final String EXEC_REFRESH_MIN_INTERVAL_MS = "EXEC_REFRESH_MIN_INTERVAL_MS";

  private final Deque<LzsSnapshot> snapshotWindow = new ArrayDeque<LzsSnapshot>();
  private final LzsEngine engine = new LzsEngine();
  private final LzsSideState longState = new LzsSideState(LzsSide.LONG);
  private final LzsSideState shortState = new LzsSideState(LzsSide.SHORT);

  private Instrument observedInstrument;
  private DOMListener domListener;
  private boolean listenerAttached;
  private long lastSnapshotRecordedAt = Long.MIN_VALUE;
  private long lastEvalAt = Long.MIN_VALUE;
  private long lastHudRefreshAt = Long.MIN_VALUE;
  private long lastExecRefreshAt = Long.MIN_VALUE;

  private Label hudLabel;
  private String hudTextCache;
  private double hudPriceCache = Double.NaN;
  private String hudStateSignatureCache = "";

  @Override
  public void initialize(Defaults defaults) {
    SettingsDescriptor sd = new SettingsDescriptor();

    SettingTab coreTab = sd.addTab("LZS Core");
    SettingGroup core = coreTab.addGroup("General");
    core.addRow(new BooleanDescriptor(ENABLE_LZS, "Enable Liquidity Zone Signal", true));
    core.addRow(new BooleanDescriptor(ENABLE_LONG, "Enable Long", true));
    core.addRow(new BooleanDescriptor(ENABLE_SHORT, "Enable Short", true));
    core.addRow(new IntegerDescriptor(EVAL_MIN_INTERVAL_MS, "Eval Min Interval (ms)", 100, 0, 5000, 10));
    core.addRow(new IntegerDescriptor(DOM_LEVELS_TO_CAPTURE, "DOM Levels To Capture", 20, 1, 200, 1));
    core.addRow(new IntegerDescriptor(DOM_WINDOW_MAX_UPDATES, "DOM Window Max Updates", 50, 2, 500, 1));
    core.addRow(new BooleanDescriptor(USE_RTH_TICKS, "Use RTH Tick Filter", true));
    core.addRow(new BooleanDescriptor(USE_HIST_BARS, "Allow Generated Ticks From OHLC Bars (fallback)", false));
    core.addRow(new BooleanDescriptor(OPEN_MODE_THROTTLE_ENABLED, "Use Open-Mode Eval Throttle", true));
    core.addRow(new IntegerDescriptor(OPEN_MODE_MINUTES, "Open-Mode Window (minutes)", 5, 1, 60, 1));
    core.addRow(new IntegerDescriptor(OPEN_MODE_EVAL_INTERVAL_MS, "Open-Mode Eval Interval (ms)", 150, 0, 5000, 10));
    core.addRow(new IntegerDescriptor(EXEC_REFRESH_MIN_INTERVAL_MS, "Execution Refresh Min Interval (ms)", 75, 0, 5000, 5));

    SettingGroup zone = coreTab.addGroup("Zone Detection");
    zone.addRow(new DoubleDescriptor(ZONE_MIN_SIZE, "Zone Min Row Size", 100.0, 1.0, 100000.0, 1.0));
    zone.addRow(new DoubleDescriptor(ZONE_MIN_ROW_PCT_OF_ANCHOR, "Zone Min Row % Of Anchor", 0.15, 0.0, 1.0, 0.01));
    zone.addRow(new IntegerDescriptor(ZONE_MIN_CONTIG_ROWS, "Zone Min Contiguous Rows", 1, 1, 20, 1));
    zone.addRow(new IntegerDescriptor(ZONE_MAX_GAP_ROWS, "Zone Max Gap Rows", 2, 0, 10, 1));
    zone.addRow(new IntegerDescriptor(ZONE_MAX_HEIGHT_TICKS, "Zone Max Height (ticks)", 20, 1, 200, 1));
    zone.addRow(new IntegerDescriptor(ZONE_MAX_DISTANCE_TICKS, "Zone Max Distance (ticks)", 12, 1, 100, 1));
    zone.addRow(new IntegerDescriptor(ARM_PROX_TICKS, "Arm Proximity To Zone (ticks)", 2, 0, 20, 1));

    SettingGroup exec = coreTab.addGroup("Execution / Reversal");
    exec.addRow(new IntegerDescriptor(EXEC_PROX_TICKS, "Execution Proximity To Zone (ticks)", 2, 0, 20, 1));
    exec.addRow(new IntegerDescriptor(MIN_BUBBLE_SIZE, "Min Bubble Size", 30, 1, 100000, 1));
    exec.addRow(new IntegerDescriptor(MIN_BUBBLE_COUNT, "Min Bubble Count", 1, 1, 20, 1));
    exec.addRow(new DoubleDescriptor(MIN_AGGR_SHARE, "Min Aggression Share", 0.10, 0.0, 1.0, 0.01));
    exec.addRow(new IntegerDescriptor(MAX_UPDATES_ARM_TO_EXEC, "Max Updates From Arm To Exec", 20, 1, 500, 1));
    exec.addRow(new IntegerDescriptor(MAX_UPDATES_TO_REVERSE, "Max Updates To Reverse", 25, 1, 500, 1));
    exec.addRow(new IntegerDescriptor(MIN_REVERSAL_TICKS, "Min Reversal Ticks", 2, 0, 50, 1));

    SettingGroup protection = coreTab.addGroup("Protection / Path");
    protection.addRow(new BooleanDescriptor(REQUIRE_RELOAD, "Require Reload", false));
    protection.addRow(new DoubleDescriptor(MIN_RELOAD_PCT, "Min Reload %", 0.00, 0.0, 5.0, 0.01));
    protection.addRow(new DoubleDescriptor(MIN_REMAINING_ZONE_PCT, "Min Remaining Zone %", 0.10, 0.0, 5.0, 0.01));
    protection.addRow(new BooleanDescriptor(REJECT_IF_CONSUMED, "Reject If Consumed", false));
    protection.addRow(new IntegerDescriptor(MIN_OPEN_PATH_TICKS, "Min Open Path (ticks)", 0, 0, 50, 1));
    protection.addRow(new DoubleDescriptor(MAX_OPPOSING_BLOCK_IN_PATH, "Max Opposing Block In Path", 1000.0, 0.0, 100000.0, 1.0));
    protection.addRow(new IntegerDescriptor(ATTRACTION_PENALTY_LOOKAHEAD, "Attraction Lookahead (ticks)", 0, 0, 50, 1));

    SettingGroup cooldown = coreTab.addGroup("Cooldown");
    cooldown.addRow(new IntegerDescriptor(MIN_MS_BETWEEN_SAME_ZONE_SIGNALS, "Min ms Between Same Zone Signals", 1500, 0, 600000, 50));
    cooldown.addRow(new IntegerDescriptor(MIN_BARS_BETWEEN_SAME_SIDE_SIGNALS, "Min Bars Between Same-Side Signals", 0, 0, 500, 1));

    SettingTab dispTab = sd.addTab("Display");
    SettingGroup hud = dispTab.addGroup("HUD");
    hud.addRow(new BooleanDescriptor(SHOW_HUD, "Show LZS HUD", true));
    hud.addRow(new BooleanDescriptor(SHOW_PHASE_DETAILS, "Show Phase Details", true));
    hud.addRow(new BooleanDescriptor(SHOW_CANDIDATE_METRICS, "Show Candidate Metrics", true));
    hud.addRow(new IntegerDescriptor(HUD_OFFSET_TICKS, "HUD Offset (ticks)", 12, 0, 200, 1));
    hud.addRow(new IntegerDescriptor(HUD_REFRESH_INTERVAL_MS, "HUD Refresh Interval (ms)", 250, 0, 5000, 10));

    setSettingsDescriptor(sd);

    RuntimeDescriptor rd = new RuntimeDescriptor();
    rd.declareSignal(Signals.LZS_LONG, "LZS Long");
    rd.declareSignal(Signals.LZS_SHORT, "LZS Short");
    rd.exportValue(new ValueDescriptor(Values.LONG_PHASE, "LZS Long Phase"));
    rd.exportValue(new ValueDescriptor(Values.SHORT_PHASE, "LZS Short Phase"));
    rd.exportValue(new ValueDescriptor(Values.LONG_ZONE_LOW, "LZS Long Zone Low"));
    rd.exportValue(new ValueDescriptor(Values.LONG_ZONE_HIGH, "LZS Long Zone High"));
    rd.exportValue(new ValueDescriptor(Values.SHORT_ZONE_LOW, "LZS Short Zone Low"));
    rd.exportValue(new ValueDescriptor(Values.SHORT_ZONE_HIGH, "LZS Short Zone High"));
    rd.exportValue(new ValueDescriptor(Values.LONG_EXEC_REF, "LZS Long Exec Ref"));
    rd.exportValue(new ValueDescriptor(Values.SHORT_EXEC_REF, "LZS Short Exec Ref"));
    rd.exportValue(new ValueDescriptor(Values.LONG_REV_TICKS, "LZS Long Reversal Ticks"));
    rd.exportValue(new ValueDescriptor(Values.SHORT_REV_TICKS, "LZS Short Reversal Ticks"));
    rd.exportValue(new ValueDescriptor(Values.LONG_REMAINING_PCT, "LZS Long Remaining %"));
    rd.exportValue(new ValueDescriptor(Values.SHORT_REMAINING_PCT, "LZS Short Remaining %"));
    rd.exportValue(new ValueDescriptor(Values.LONG_PATH_CLEAR, "LZS Long Path Clear"));
    rd.exportValue(new ValueDescriptor(Values.SHORT_PATH_CLEAR, "LZS Short Path Clear"));
    setRuntimeDescriptor(rd);
  }

  @Override
  public void clearState() {
    super.clearState();
    detachDomListener();
    clearHud(false);
    longState.resetLifecycle();
    shortState.resetLifecycle();
    snapshotWindow.clear();
    lastEvalAt = Long.MIN_VALUE;
    lastSnapshotRecordedAt = Long.MIN_VALUE;
    lastHudRefreshAt = Long.MIN_VALUE;
    lastExecRefreshAt = Long.MIN_VALUE;
    hudStateSignatureCache = "";
  }

  @Override
  public void onSettingsUpdated(DataContext ctx) {
    super.onSettingsUpdated(ctx);
    longState.resetLifecycle();
    shortState.resetLifecycle();
    hudTextCache = null;
    hudPriceCache = Double.NaN;
    hudStateSignatureCache = "";
    lastEvalAt = Long.MIN_VALUE;
    lastHudRefreshAt = Long.MIN_VALUE;
    lastExecRefreshAt = Long.MIN_VALUE;
  }

  @Override
  protected void calculate(int index, DataContext ctx) {
    DataSeries s = ctx.getDataSeries();
    if (s == null || index < 0 || index >= s.size()) return;

    if (!getSettings().getBoolean(ENABLE_LZS, true)) {
      detachDomListener();
      clearHud(false);
      s.setComplete(index);
      return;
    }

    initDomListener(ctx);

    LzsSnapshot latest = latestSnapshot();
    if (latest != null) {
      latest.barIndex = index;
      latest.barStartTime = s.getStartTime(index);
      latest.barHigh = s.getHigh(index);
      latest.barLow = s.getLow(index);
      latest.lastPrice = s.getClose(index);
      latest.tickSize = ctx.getInstrument() == null ? 0.25 : safeTickSize(ctx.getInstrument());
    }

    LzsConfig cfg = buildConfig();
    long now = System.currentTimeMillis();
    int effectiveEvalMs = getEffectiveEvalIntervalMs(cfg, latest != null && latest.barStartTime != Long.MIN_VALUE ? latest.barStartTime : now);
    boolean runEval = latest != null && (lastEvalAt == Long.MIN_VALUE || effectiveEvalMs <= 0 || (now - lastEvalAt) >= effectiveEvalMs);

    boolean preRefreshedExec = false;
    if (latest != null && shouldRefreshExecution(now, cfg) && hasActiveExecSide()) {
      populateCurrentBarExecution(latest, index, ctx);
      lastExecRefreshAt = now;
      preRefreshedExec = true;
    }

    String beforeSig = buildStateSignature();
    boolean longEmitted = false;
    boolean shortEmitted = false;
    if (runEval) {
      LzsEngineResult longRes = engine.evaluate(LzsSide.LONG, longState, latest, cfg);
      LzsEngineResult shortRes = engine.evaluate(LzsSide.SHORT, shortState, latest, cfg);
      longEmitted |= longRes != null && longRes.emitted;
      shortEmitted |= shortRes != null && shortRes.emitted;
      lastEvalAt = now;

      if (!preRefreshedExec && latest != null && shouldRefreshExecution(now, cfg) && hasActiveExecSide()) {
        populateCurrentBarExecution(latest, index, ctx);
        lastExecRefreshAt = now;
        longRes = engine.evaluate(LzsSide.LONG, longState, latest, cfg);
        shortRes = engine.evaluate(LzsSide.SHORT, shortState, latest, cfg);
        longEmitted |= longRes != null && longRes.emitted;
        shortEmitted |= shortRes != null && shortRes.emitted;
      }
    }
    String afterSig = buildStateSignature();
    boolean stateChanged = !afterSig.equals(beforeSig);

    storeRuntimeValues(s, index, longEmitted, shortEmitted);
    if (latest != null) {
      if (longEmitted) emitSignal(ctx, index, Signals.LZS_LONG, "LZS Long", latest.lastPrice, longState);
      if (shortEmitted) emitSignal(ctx, index, Signals.LZS_SHORT, "LZS Short", latest.lastPrice, shortState);
    }

    updateHud(index, ctx, cfg, now, stateChanged);
    s.setComplete(index);
  }

  private void initDomListener(DataContext ctx) {
    Instrument instr = ctx.getInstrument();
    if (instr == null) return;
    int captureLevels = Math.max(1, getSettings().getInteger(DOM_LEVELS_TO_CAPTURE, 20));
    if (observedInstrument != instr) {
      detachDomListener();
      observedInstrument = instr;
      snapshotWindow.clear();
    }
    if (!listenerAttached) {
      domListener = new DOMListener() {
        @Override
        public void update(DOM dom) {
          recordSnapshot(dom, captureLevels);
        }
      };
      instr.addListener(domListener);
      listenerAttached = true;
    }
    trimWindow(getSettings().getInteger(DOM_WINDOW_MAX_UPDATES, 50));
  }

  private void detachDomListener() {
    if (observedInstrument != null && listenerAttached && domListener != null) {
      observedInstrument.removeListener(domListener);
    }
    listenerAttached = false;
    domListener = null;
    observedInstrument = null;
    snapshotWindow.clear();
    lastSnapshotRecordedAt = Long.MIN_VALUE;
  }

  private void recordSnapshot(DOM dom, int captureLevels) {
    if (dom == null) return;
    long now = System.currentTimeMillis();
    if (lastSnapshotRecordedAt != Long.MIN_VALUE && now == lastSnapshotRecordedAt) return;
    lastSnapshotRecordedAt = now;

    LzsSnapshot snap = buildSnapshot(dom, captureLevels);
    if (snap == null) return;

    synchronized (snapshotWindow) {
      snapshotWindow.addLast(snap);
      trimWindow(getSettings().getInteger(DOM_WINDOW_MAX_UPDATES, 50));
    }
  }

  private LzsSnapshot buildSnapshot(DOM dom, int captureLevels) {
    if (dom == null) return null;
    LzsSnapshot snap = new LzsSnapshot();
    snap.time = System.currentTimeMillis();
    if (observedInstrument != null) snap.tickSize = safeTickSize(observedInstrument);

    java.util.List<DOMRow> bidRows = dom.getBidRows();
    java.util.List<DOMRow> askRows = dom.getAskRows();
    if (bidRows == null) bidRows = java.util.Collections.emptyList();
    if (askRows == null) askRows = java.util.Collections.emptyList();
    if (!bidRows.isEmpty()) snap.bestBid = bidRows.get(0).getPrice();
    if (!askRows.isEmpty()) snap.bestAsk = askRows.get(0).getPrice();

    int limBid = Math.min(captureLevels, bidRows.size());
    int limAsk = Math.min(captureLevels, askRows.size());

    for (int i = 0; i < limBid; i++) {
      DOMRow r = bidRows.get(i);
      LzsRow lite = new LzsRow(r.getPrice(), r.getSize(), safeOrderCount(r));
      snap.bidRowsNear.add(lite);
      snap.totalBidNear += lite.size;
      snap.largestBidBlock = Math.max(snap.largestBidBlock, lite.size);
    }

    for (int i = 0; i < limAsk; i++) {
      DOMRow r = askRows.get(i);
      LzsRow lite = new LzsRow(r.getPrice(), r.getSize(), safeOrderCount(r));
      snap.askRowsNear.add(lite);
      snap.totalAskNear += lite.size;
      snap.largestAskBlock = Math.max(snap.largestAskBlock, lite.size);
    }
    return snap;
  }

  private void populateCurrentBarExecution(LzsSnapshot snap, int index, DataContext ctx) {
    if (snap == null || observedInstrument == null || ctx == null) return;
    DataSeries s = ctx.getDataSeries();
    if (s == null || index < 0 || index >= s.size()) return;

    long start = s.getStartTime(index);
    long end = Math.min(System.currentTimeMillis(), s.getEndTime(index));
    if (end <= start) end = start + 1;

    final double tick = Math.max(1e-9, safeTickSize(observedInstrument));
    final boolean rth = getSettings().getBoolean(USE_RTH_TICKS, true);
    final boolean useHistBars = getSettings().getBoolean(USE_HIST_BARS, false);
    final Map<Double, LzsExecRow> rows = new HashMap<Double, LzsExecRow>();

    observedInstrument.forEachTick(start, end, rth, useHistBars, tickData -> {
      double px = roundToTick(tickData.getPrice(), tick);
      LzsExecRow row = rows.get(px);
      if (row == null) {
        row = new LzsExecRow();
        row.price = px;
        rows.put(px, row);
      }
      double vol = tickData.getVolumeAsFloat();
      if (tickData.isAskTick()) row.askVol += vol;
      else row.bidVol += vol;
      row.totalVol = row.bidVol + row.askVol;
    });

    snap.execRows.clear();
    java.util.List<Double> prices = new java.util.ArrayList<Double>(rows.keySet());
    java.util.Collections.sort(prices);
    for (Double px : prices) {
      snap.execRows.add(rows.get(px));
    }
  }

  private boolean hasActiveExecSide() {
    return isExecutionRelevant(longState) || isExecutionRelevant(shortState);
  }

  private boolean isExecutionRelevant(LzsSideState state) {
    if (state == null) return false;
    LzsPhase p = state.interaction.phase;
    return p == LzsPhase.ARMED || p == LzsPhase.TOUCHED || p == LzsPhase.EXECUTION_CONFIRMED
        || p == LzsPhase.REVERSAL_CONFIRMED || p == LzsPhase.PROTECTED;
  }

  private boolean shouldRefreshExecution(long now, LzsConfig cfg) {
    int minMs = Math.max(0, cfg.execRefreshMinIntervalMs);
    return lastExecRefreshAt == Long.MIN_VALUE || minMs <= 0 || (now - lastExecRefreshAt) >= minMs;
  }

  private int getEffectiveEvalIntervalMs(LzsConfig cfg, long refTime) {
    int base = Math.max(0, cfg.evalMinIntervalMs);
    if (!cfg.openModeThrottleEnabled) return base;
    if (isWithinOpenWindow(refTime, cfg.openModeMinutes)) {
      return Math.max(base, Math.max(0, cfg.openModeEvalIntervalMs));
    }
    return base;
  }

  private boolean isWithinOpenWindow(long timeMillis, int openMinutes) {
    if (timeMillis <= 0L) return false;
    try {
      ZonedDateTime zdt = Instant.ofEpochMilli(timeMillis).atZone(ZoneId.of("America/New_York"));
      int mins = zdt.getHour() * 60 + zdt.getMinute();
      int open = 9 * 60 + 30;
      return mins >= open && mins < open + Math.max(1, openMinutes);
    }
    catch (Throwable t) {
      return false;
    }
  }

  private void storeRuntimeValues(DataSeries s, int index, boolean longEmitted, boolean shortEmitted) {
    s.setDouble(index, Values.LONG_PHASE, (double) longState.interaction.phase.ordinal());
    s.setDouble(index, Values.SHORT_PHASE, (double) shortState.interaction.phase.ordinal());
    s.setDouble(index, Values.LONG_SCORE, longState.interaction.score);
    s.setDouble(index, Values.SHORT_SCORE, shortState.interaction.score);
    s.setDouble(index, Values.LONG_ZONE_LOW, longState.candidate == null ? Double.NaN : longState.candidate.zoneLow);
    s.setDouble(index, Values.LONG_ZONE_HIGH, longState.candidate == null ? Double.NaN : longState.candidate.zoneHigh);
    s.setDouble(index, Values.SHORT_ZONE_LOW, shortState.candidate == null ? Double.NaN : shortState.candidate.zoneLow);
    s.setDouble(index, Values.SHORT_ZONE_HIGH, shortState.candidate == null ? Double.NaN : shortState.candidate.zoneHigh);
    s.setDouble(index, Values.LONG_EXEC_REF, longState.interaction.reversalRefPrice);
    s.setDouble(index, Values.SHORT_EXEC_REF, shortState.interaction.reversalRefPrice);
    s.setDouble(index, Values.LONG_REV_TICKS, longState.interaction.reversalTicks);
    s.setDouble(index, Values.SHORT_REV_TICKS, shortState.interaction.reversalTicks);
    s.setDouble(index, Values.LONG_REMAINING_PCT, longState.interaction.remainingZonePct);
    s.setDouble(index, Values.SHORT_REMAINING_PCT, shortState.interaction.remainingZonePct);
    s.setDouble(index, Values.LONG_PATH_CLEAR, longState.interaction.pathClearTicks);
    s.setDouble(index, Values.SHORT_PATH_CLEAR, shortState.interaction.pathClearTicks);
    if (longEmitted) s.setBoolean(index, Values.LONG_FIRED, true);
    if (shortEmitted) s.setBoolean(index, Values.SHORT_FIRED, true);
  }

  private void emitSignal(DataContext ctx, int index, Signals signal, String label, double price, LzsSideState state) {
    if (ctx == null || state == null) return;
    Instrument instr = ctx.getInstrument();
    String priceText = instr == null ? LzsFormatUtils.fmt2(price) : instr.format(price);
    String msg = label + " | " + priceText + " | Rev " + LzsFormatUtils.fmt1(state.interaction.reversalTicks)
        + "t | Path " + LzsFormatUtils.fmt1(state.interaction.pathClearTicks) + "t";
    ctx.signal(index, signal, msg, price);
  }

  private static double roundToTick(double price, double tick) {
    if (tick <= 0) return price;
    return Math.round(price / tick) * tick;
  }

  private static double safeTickSize(Instrument instr) {
    try {
      double t = instr == null ? 0.25 : instr.getTickSize();
      return t > 0 ? t : 0.25;
    }
    catch (Throwable ignore) {
      return 0.25;
    }
  }

  private static int safeOrderCount(DOMRow r) {
    try {
      return r == null ? 0 : r.getOrderCount();
    }
    catch (Throwable t) {
      return 0;
    }
  }

  private void trimWindow(int maxUpdates) {
    synchronized (snapshotWindow) {
      while (snapshotWindow.size() > Math.max(1, maxUpdates)) {
        snapshotWindow.removeFirst();
      }
    }
  }

  private LzsSnapshot latestSnapshot() {
    synchronized (snapshotWindow) {
      return snapshotWindow.isEmpty() ? null : snapshotWindow.getLast();
    }
  }

  private LzsConfig buildConfig() {
    LzsConfig cfg = LzsConfig.defaults();
    cfg.enableLong = getSettings().getBoolean(ENABLE_LONG, true);
    cfg.enableShort = getSettings().getBoolean(ENABLE_SHORT, true);
    cfg.evalMinIntervalMs = getSettings().getInteger(EVAL_MIN_INTERVAL_MS, 100);
    cfg.domLevelsToCapture = getSettings().getInteger(DOM_LEVELS_TO_CAPTURE, 20);
    cfg.domWindowMaxUpdates = getSettings().getInteger(DOM_WINDOW_MAX_UPDATES, 50);
    cfg.useRthTicks = getSettings().getBoolean(USE_RTH_TICKS, true);
    cfg.useHistBars = getSettings().getBoolean(USE_HIST_BARS, false);
    cfg.zoneMinSize = getSettings().getDouble(ZONE_MIN_SIZE, 100.0);
    cfg.zoneMinRowPctOfAnchor = getSettings().getDouble(ZONE_MIN_ROW_PCT_OF_ANCHOR, 0.15);
    cfg.zoneMinContiguousRows = getSettings().getInteger(ZONE_MIN_CONTIG_ROWS, 1);
    cfg.zoneMaxGapRows = getSettings().getInteger(ZONE_MAX_GAP_ROWS, 2);
    cfg.zoneMaxHeightTicks = getSettings().getInteger(ZONE_MAX_HEIGHT_TICKS, 20);
    cfg.zoneMaxDistanceTicks = getSettings().getInteger(ZONE_MAX_DISTANCE_TICKS, 12);
    cfg.armProximityTicks = getSettings().getInteger(ARM_PROX_TICKS, 2);
    cfg.executionProximityTicks = getSettings().getInteger(EXEC_PROX_TICKS, 2);
    cfg.minBubbleSize = getSettings().getInteger(MIN_BUBBLE_SIZE, 30);
    cfg.minBubbleCount = getSettings().getInteger(MIN_BUBBLE_COUNT, 1);
    cfg.minAggressionShare = getSettings().getDouble(MIN_AGGR_SHARE, 0.10);
    cfg.maxUpdatesFromArmToExec = getSettings().getInteger(MAX_UPDATES_ARM_TO_EXEC, 20);
    cfg.maxUpdatesToReverse = getSettings().getInteger(MAX_UPDATES_TO_REVERSE, 25);
    cfg.minReversalTicks = getSettings().getInteger(MIN_REVERSAL_TICKS, 2);
    cfg.requireReload = getSettings().getBoolean(REQUIRE_RELOAD, false);
    cfg.minReloadPct = getSettings().getDouble(MIN_RELOAD_PCT, 0.0);
    cfg.minRemainingZonePct = getSettings().getDouble(MIN_REMAINING_ZONE_PCT, 0.10);
    cfg.rejectIfConsumed = getSettings().getBoolean(REJECT_IF_CONSUMED, false);
    cfg.minOpenPathTicks = getSettings().getInteger(MIN_OPEN_PATH_TICKS, 0);
    cfg.maxOpposingBlockInPath = getSettings().getDouble(MAX_OPPOSING_BLOCK_IN_PATH, 1000.0);
    cfg.attractionPenaltyLookaheadTicks = getSettings().getInteger(ATTRACTION_PENALTY_LOOKAHEAD, 0);
    cfg.minMsBetweenSameZoneSignals = getSettings().getInteger(MIN_MS_BETWEEN_SAME_ZONE_SIGNALS, 1500);
    cfg.minBarsBetweenSameSideSignals = getSettings().getInteger(MIN_BARS_BETWEEN_SAME_SIDE_SIGNALS, 0);
    cfg.showHud = getSettings().getBoolean(SHOW_HUD, true);
    cfg.showPhaseDetails = getSettings().getBoolean(SHOW_PHASE_DETAILS, true);
    cfg.showCandidateMetrics = getSettings().getBoolean(SHOW_CANDIDATE_METRICS, true);
    cfg.hudOffsetTicks = getSettings().getInteger(HUD_OFFSET_TICKS, 12);
    cfg.hudRefreshIntervalMs = getSettings().getInteger(HUD_REFRESH_INTERVAL_MS, 250);
    cfg.openModeThrottleEnabled = getSettings().getBoolean(OPEN_MODE_THROTTLE_ENABLED, true);
    cfg.openModeMinutes = getSettings().getInteger(OPEN_MODE_MINUTES, 5);
    cfg.openModeEvalIntervalMs = getSettings().getInteger(OPEN_MODE_EVAL_INTERVAL_MS, 150);
    cfg.execRefreshMinIntervalMs = getSettings().getInteger(EXEC_REFRESH_MIN_INTERVAL_MS, 75);
    return cfg;
  }

  private void updateHud(int index, DataContext ctx, LzsConfig cfg, long now, boolean stateChanged) {
    DataSeries s = ctx.getDataSeries();
    if (!cfg.showHud) {
      clearHud(false);
      return;
    }

    int refreshMs = Math.max(0, cfg.hudRefreshIntervalMs);
    if (!stateChanged && lastHudRefreshAt != Long.MIN_VALUE && refreshMs > 0 && (now - lastHudRefreshAt) < refreshMs) {
      return;
    }

    double tick = ctx.getInstrument() == null ? 0.25 : safeTickSize(ctx.getInstrument());
    double anchorPrice = s.getHigh(index) + (cfg.hudOffsetTicks * tick);
    String text = buildHudText(cfg);
    String stateSig = buildStateSignature();

    boolean sameText = text.equals(hudTextCache);
    boolean sameStateSig = stateSig.equals(hudStateSignatureCache);
    boolean samePrice = !Double.isNaN(hudPriceCache)
        && Math.abs(hudPriceCache - anchorPrice) < Math.max(1e-9, 0.25 * Math.max(tick, 1e-9));

    if (sameText && samePrice && sameStateSig && hudLabel != null) {
      return;
    }

    if (hudLabel != null) removeFigure(hudLabel);
    hudLabel = new Label(new Coordinate(s.getStartTime(index), anchorPrice), text);
    hudLabel.setShowLine(false);
    hudLabel.setPosition(Enums.Position.TOP_RIGHT);
    hudLabel.setOffsetPixels(20);
    addFigure(hudLabel);
    hudTextCache = text;
    hudPriceCache = anchorPrice;
    hudStateSignatureCache = stateSig;
    lastHudRefreshAt = now;
    notifyRedraw();
  }

  private String buildHudText(LzsConfig cfg) {
    StringBuilder sb = new StringBuilder();
    sb.append("LZS HUD\n");
    sb.append(LzsFormatUtils.buildHudSide(longState, cfg));
    sb.append("\n");
    sb.append(LzsFormatUtils.buildHudSide(shortState, cfg));
    return sb.toString();
  }

  private String buildStateSignature() {
    return buildSideSignature(longState) + "||" + buildSideSignature(shortState);
  }

  private String buildSideSignature(LzsSideState state) {
    if (state == null) return "NA";
    String candSig = state.candidate == null ? "-" : state.candidate.signature();
    String dbg = state.interaction.debug == null ? "" : state.interaction.debug;
    return state.side + "|" + state.interaction.phase + "|" + candSig + "|" + dbg + "|"
        + LzsFormatUtils.fmt1(state.interaction.execSameSideVol) + "|"
        + state.interaction.bubbleCount + "|" + LzsFormatUtils.fmt2(state.interaction.aggressionShare) + "|"
        + LzsFormatUtils.fmt1(state.interaction.reversalTicks) + "|"
        + LzsFormatUtils.fmt2(state.interaction.remainingZonePct) + "|"
        + LzsFormatUtils.fmt1(state.interaction.pathClearTicks);
  }

  private void clearHud(boolean redraw) {
    if (hudLabel != null) {
      removeFigure(hudLabel);
      hudLabel = null;
      hudTextCache = null;
      hudPriceCache = Double.NaN;
      hudStateSignatureCache = "";
      if (redraw) notifyRedraw();
    }
  }
}
