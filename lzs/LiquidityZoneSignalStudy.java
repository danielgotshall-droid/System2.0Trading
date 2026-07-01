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

import study_examples.lzs.context.LzsContextConfig;
import study_examples.lzs.context.LzsContextEngine;
import study_examples.lzs.context.LzsContextResult;
import study_examples.lzs.context.LzsContextSnapshot;
import study_examples.lzs.context.LzsContextMergeEngine;
import study_examples.lzs.context.LzsContextMode;
import study_examples.lzs.context.LzsHudDisplayMode;
import study_examples.lzs.context.LzsMergedContextResult;
import study_examples.lzs.context.LzsReferenceResolver;
import study_examples.lzs.context.DayTypeContextProvider;
import study_examples.lzs.LzsStudySignals;
import study_examples.lzs.LzsStudyValues;
import study_examples.lzs.engine.LzsEngine;
import study_examples.lzs.model.*;
import study_examples.lzs.session.InitialBalanceTracker;
import study_examples.lzs.session.OpeningRangeTracker;
import study_examples.lzs.session.RthSessionTracker;
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
  private static final String OPPOSING_ZONE_CONFLICT_MODE = "OPPOSING_ZONE_CONFLICT_MODE";
  private static final String OPPOSING_ZONE_CONFLICT_MAX_DISTANCE_TICKS = "OPPOSING_ZONE_CONFLICT_MAX_DISTANCE_TICKS";

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

  private static final String ENABLE_CONTEXT = "ENABLE_CONTEXT";
  private static final String ENABLE_STRUCTURAL_CONTEXT = "ENABLE_STRUCTURAL_CONTEXT";
  private static final String ENABLE_VWAP_CONTEXT = "ENABLE_VWAP_CONTEXT";
  private static final String ENABLE_IB_CONTEXT = "ENABLE_IB_CONTEXT";
  private static final String STRUCTURAL_PROX_TICKS = "STRUCTURAL_PROX_TICKS";
  private static final String VWAP_PROX_TICKS = "VWAP_PROX_TICKS";
  private static final String IB_PROX_TICKS = "IB_PROX_TICKS";
  private static final String IB_MINUTES = "IB_MINUTES";
  private static final String SHOW_CONTEXT_ON_HUD = "SHOW_CONTEXT_ON_HUD";
  private static final String SHOW_CONTEXT_REASONS = "SHOW_CONTEXT_REASONS";
  private static final String ENABLE_OVERNIGHT_CONTEXT = "ENABLE_OVERNIGHT_CONTEXT";
  private static final String ENABLE_OR_CONTEXT = "ENABLE_OR_CONTEXT";
  private static final String ENABLE_VALUE_AREA_CONTEXT = "ENABLE_VALUE_AREA_CONTEXT";
  private static final String OVERNIGHT_PROX_TICKS = "OVERNIGHT_PROX_TICKS";
  private static final String OR_PROX_TICKS = "OR_PROX_TICKS";
  private static final String VALUE_AREA_PROX_TICKS = "VALUE_AREA_PROX_TICKS";
  private static final String OR_MINUTES = "OR_MINUTES";
  private static final String USE_MANUAL_LEVEL_FALLBACK = "USE_MANUAL_LEVEL_FALLBACK";
  private static final String PREFER_MANUAL_LEVELS = "PREFER_MANUAL_LEVELS";
  private static final String SHOW_REFERENCE_SOURCES_ON_HUD = "SHOW_REFERENCE_SOURCES_ON_HUD";
  private static final String SHOW_DEVELOPING_REFS_ON_HUD = "SHOW_DEVELOPING_REFS_ON_HUD";
  private static final String MANUAL_SESSION_OPEN_ENABLED = "MANUAL_SESSION_OPEN_ENABLED";
  private static final String MANUAL_SESSION_OPEN = "MANUAL_SESSION_OPEN";
  private static final String MANUAL_PRIOR_DAY_LEVELS_ENABLED = "MANUAL_PRIOR_DAY_LEVELS_ENABLED";
  private static final String MANUAL_PRIOR_DAY_HIGH = "MANUAL_PRIOR_DAY_HIGH";
  private static final String MANUAL_PRIOR_DAY_LOW = "MANUAL_PRIOR_DAY_LOW";
  private static final String MANUAL_PRIOR_DAY_CLOSE = "MANUAL_PRIOR_DAY_CLOSE";
  private static final String MANUAL_OVERNIGHT_LEVELS_ENABLED = "MANUAL_OVERNIGHT_LEVELS_ENABLED";
  private static final String MANUAL_OVERNIGHT_HIGH = "MANUAL_OVERNIGHT_HIGH";
  private static final String MANUAL_OVERNIGHT_LOW = "MANUAL_OVERNIGHT_LOW";
  private static final String MANUAL_OPENING_RANGE_LEVELS_ENABLED = "MANUAL_OPENING_RANGE_LEVELS_ENABLED";
  private static final String MANUAL_OPENING_RANGE_HIGH = "MANUAL_OPENING_RANGE_HIGH";
  private static final String MANUAL_OPENING_RANGE_LOW = "MANUAL_OPENING_RANGE_LOW";
  private static final String MANUAL_INITIAL_BALANCE_LEVELS_ENABLED = "MANUAL_INITIAL_BALANCE_LEVELS_ENABLED";
  private static final String MANUAL_INITIAL_BALANCE_HIGH = "MANUAL_INITIAL_BALANCE_HIGH";
  private static final String MANUAL_INITIAL_BALANCE_LOW = "MANUAL_INITIAL_BALANCE_LOW";
  private static final String MANUAL_VALUE_AREA_LEVELS_ENABLED = "MANUAL_VALUE_AREA_LEVELS_ENABLED";
  private static final String MANUAL_PRIOR_VALUE_AREA_HIGH = "MANUAL_PRIOR_VALUE_AREA_HIGH";
  private static final String MANUAL_PRIOR_VALUE_AREA_LOW = "MANUAL_PRIOR_VALUE_AREA_LOW";
  private static final String MANUAL_PRIOR_POC = "MANUAL_PRIOR_POC";

  private static final String ENABLE_DAY_TYPE_CONTEXT = "ENABLE_DAY_TYPE_CONTEXT";
  private static final String DAY_TYPE_REFRESH_INTERVAL_MS = "DAY_TYPE_REFRESH_INTERVAL_MS";
  private static final String DAY_TYPE_RECENT_LOOKBACK_SESSIONS = "DAY_TYPE_RECENT_LOOKBACK_SESSIONS";
  private static final String DAY_TYPE_SCORE_WEIGHT = "DAY_TYPE_SCORE_WEIGHT";
  private static final String DAY_TYPE_MIN_CONFIDENCE = "DAY_TYPE_MIN_CONFIDENCE";
  private static final String SHOW_DAY_TYPE_ON_HUD = "SHOW_DAY_TYPE_ON_HUD";
  private static final String SHOW_DAY_TYPE_DEBUG = "SHOW_DAY_TYPE_DEBUG";

  private static final String CONTEXT_MODE = "CONTEXT_MODE";
  private static final String HUD_DISPLAY_MODE = "HUD_DISPLAY_MODE";
  private static final String MIN_MERGED_CONTEXT_SCORE_FOR_FILTER = "MIN_MERGED_CONTEXT_SCORE_FOR_FILTER";
  private static final String MIN_STRUCTURAL_SCORE_FOR_FILTER = "MIN_STRUCTURAL_SCORE_FOR_FILTER";
  private static final String MIN_DAYTYPE_SCORE_FOR_FILTER = "MIN_DAYTYPE_SCORE_FOR_FILTER";
  private static final String REQUIRE_SUPPORTS_SIDE_WHEN_FILTER = "REQUIRE_SUPPORTS_SIDE_WHEN_FILTER";
  private static final String BLOCK_FREE_FLOATING_WHEN_FILTER = "BLOCK_FREE_FLOATING_WHEN_FILTER";
  private static final String SHOW_FILTER_STATUS_ON_HUD = "SHOW_FILTER_STATUS_ON_HUD";


  private static final String ENABLE_DOM_STALE_DETECTION = "ENABLE_DOM_STALE_DETECTION";
  private static final String DOM_STALE_THRESHOLD_MS = "DOM_STALE_THRESHOLD_MS";
  private static final String DEPTH_SIGNATURE_STALE_THRESHOLD_MS = "DEPTH_SIGNATURE_STALE_THRESHOLD_MS";
  private static final String AUTO_RESET_DOM_ON_STALE = "AUTO_RESET_DOM_ON_STALE";
  private static final String MAX_DOM_AUTO_RESETS_PER_SESSION = "MAX_DOM_AUTO_RESETS_PER_SESSION";
  private static final String SHOW_DOM_HEALTH_ON_HUD = "SHOW_DOM_HEALTH_ON_HUD";

  private static final String USE_MANUAL_DAY_TYPE_AID = "USE_MANUAL_DAY_TYPE_AID";
  private static final String PREFER_MANUAL_DAY_TYPE_AID = "PREFER_MANUAL_DAY_TYPE_AID";
  private static final String MANUAL_DAY_TYPE_RECENT_MEDIAN_IB_RANGE = "MANUAL_DAY_TYPE_RECENT_MEDIAN_IB_RANGE";
  private static final String MANUAL_DAY_TYPE_RECENT_MEDIAN_IB_VOLUME = "MANUAL_DAY_TYPE_RECENT_MEDIAN_IB_VOLUME";
  private static final String MANUAL_DAY_TYPE_ATR_LIKE_RANGE = "MANUAL_DAY_TYPE_ATR_LIKE_RANGE";
  private static final String MANUAL_DAY_TYPE_PRIOR_SESSION_CLOSE = "MANUAL_DAY_TYPE_PRIOR_SESSION_CLOSE";

  private final Deque<LzsSnapshot> snapshotWindow = new ArrayDeque<LzsSnapshot>();
  private final LzsEngine engine = new LzsEngine();
  private final LzsContextEngine contextEngine = new LzsContextEngine();
  private final LzsContextMergeEngine contextMergeEngine = new LzsContextMergeEngine();
  private final LzsReferenceResolver referenceResolver = new LzsReferenceResolver();
  private final RthSessionTracker sessionTracker = new RthSessionTracker();
  private final InitialBalanceTracker ibTracker = new InitialBalanceTracker();
  private final OpeningRangeTracker orTracker = new OpeningRangeTracker();
  private final DayTypeContextProvider dayTypeProvider = new DayTypeContextProvider();
  private final LzsSideState longState = new LzsSideState(LzsSide.LONG);
  private final LzsSideState shortState = new LzsSideState(LzsSide.SHORT);

  private LzsContextResult longContext = new LzsContextResult();
  private LzsContextResult shortContext = new LzsContextResult();
  private LzsMergedContextResult longMergedContext = new LzsMergedContextResult();
  private LzsMergedContextResult shortMergedContext = new LzsMergedContextResult();

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

  private long lastDomUpdateAt = Long.MIN_VALUE;
  private long lastBestBidAskUpdateAt = Long.MIN_VALUE;
  private long lastDepthSignatureChangeAt = Long.MIN_VALUE;
  private long lastChartActivityAt = Long.MIN_VALUE;
  private long lastDomAutoResetAt = Long.MIN_VALUE;
  private long domResetSessionStartTime = Long.MIN_VALUE;
  private int domAutoResetCount = 0;
  private int lastDepthSignature = 0;
  private int lastObservedBarIndex = -1;
  private double lastObservedClose = Double.NaN;
  private double lastObservedBestBid = Double.NaN;
  private double lastObservedBestAsk = Double.NaN;
  private String domHealthStatus = "WAIT";

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

    SettingGroup domHealth = coreTab.addGroup("DOM Health");
    domHealth.addRow(new BooleanDescriptor(ENABLE_DOM_STALE_DETECTION, "Enable DOM Stale Detection", true));
    domHealth.addRow(new IntegerDescriptor(DOM_STALE_THRESHOLD_MS, "DOM Stale Threshold (ms)", 3000, 250, 60000, 250));
    domHealth.addRow(new IntegerDescriptor(DEPTH_SIGNATURE_STALE_THRESHOLD_MS, "Depth Signature Stale Threshold (ms)", 5000, 250, 120000, 250));
    domHealth.addRow(new BooleanDescriptor(AUTO_RESET_DOM_ON_STALE, "Auto Reset DOM Adapter On Stale", true));
    domHealth.addRow(new IntegerDescriptor(MAX_DOM_AUTO_RESETS_PER_SESSION, "Max Auto Resets Per Session", 3, 0, 20, 1));
    domHealth.addRow(new BooleanDescriptor(SHOW_DOM_HEALTH_ON_HUD, "Show DOM Health On HUD", false));

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
    protection.addRow(new IntegerDescriptor(OPPOSING_ZONE_CONFLICT_MODE, "Opposing Zone Conflict Mode (0=Off,1=Annotate,2=Block)", 0, 0, 2, 1));
    protection.addRow(new IntegerDescriptor(OPPOSING_ZONE_CONFLICT_MAX_DISTANCE_TICKS, "Opposing Zone Conflict Max Distance (ticks)", 12, 0, 100, 1));

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

    SettingTab ctxTab = sd.addTab("Phase 4 Context");
    SettingGroup ctxGeneral = ctxTab.addGroup("General");
    ctxGeneral.addRow(new BooleanDescriptor(ENABLE_CONTEXT, "Enable Context Layer", true));
    ctxGeneral.addRow(new BooleanDescriptor(ENABLE_STRUCTURAL_CONTEXT, "Enable Structural References", true));
    ctxGeneral.addRow(new BooleanDescriptor(ENABLE_VWAP_CONTEXT, "Enable Session VWAP Context", true));
    ctxGeneral.addRow(new BooleanDescriptor(ENABLE_OVERNIGHT_CONTEXT, "Enable Overnight Context", true));
    ctxGeneral.addRow(new BooleanDescriptor(ENABLE_IB_CONTEXT, "Enable Initial Balance Context", true));
    ctxGeneral.addRow(new BooleanDescriptor(ENABLE_OR_CONTEXT, "Enable Opening Range Context", true));
    ctxGeneral.addRow(new BooleanDescriptor(ENABLE_VALUE_AREA_CONTEXT, "Enable Prior Value Area Context", true));
    ctxGeneral.addRow(new BooleanDescriptor(ENABLE_DAY_TYPE_CONTEXT, "Enable Day-Type Context", true));
    ctxGeneral.addRow(new IntegerDescriptor(OR_MINUTES, "Opening Range Minutes", 5, 1, 60, 1));
    ctxGeneral.addRow(new IntegerDescriptor(IB_MINUTES, "Initial Balance Minutes", 60, 5, 180, 5));

    SettingGroup ctxDistance = ctxTab.addGroup("Proximity");
    ctxDistance.addRow(new IntegerDescriptor(STRUCTURAL_PROX_TICKS, "Structural Ref Proximity (ticks)", 8, 0, 50, 1));
    ctxDistance.addRow(new IntegerDescriptor(OVERNIGHT_PROX_TICKS, "Overnight Ref Proximity (ticks)", 8, 0, 50, 1));
    ctxDistance.addRow(new IntegerDescriptor(VWAP_PROX_TICKS, "VWAP Proximity (ticks)", 8, 0, 50, 1));
    ctxDistance.addRow(new IntegerDescriptor(OR_PROX_TICKS, "Opening Range Edge Proximity (ticks)", 6, 0, 50, 1));
    ctxDistance.addRow(new IntegerDescriptor(IB_PROX_TICKS, "IB Edge Proximity (ticks)", 6, 0, 50, 1));
    ctxDistance.addRow(new IntegerDescriptor(VALUE_AREA_PROX_TICKS, "Value Area / POC Proximity (ticks)", 6, 0, 50, 1));

    SettingGroup ctxDayType = ctxTab.addGroup("Day Type");
    ctxDayType.addRow(new IntegerDescriptor(DAY_TYPE_REFRESH_INTERVAL_MS, "Day-Type Refresh Interval (ms)", 5000, 250, 60000, 250));
    ctxDayType.addRow(new IntegerDescriptor(DAY_TYPE_RECENT_LOOKBACK_SESSIONS, "Day-Type Recent Lookback Sessions", 20, 5, 60, 1));
    ctxDayType.addRow(new DoubleDescriptor(DAY_TYPE_SCORE_WEIGHT, "Day-Type Score Weight", 1.0, 0.0, 5.0, 0.1));
    ctxDayType.addRow(new DoubleDescriptor(DAY_TYPE_MIN_CONFIDENCE, "Day-Type Min Confidence", 7.5, 0.0, 100.0, 0.5));

    SettingGroup ctxDayTypeManual = ctxTab.addGroup("Day Type Manual Aid");
    ctxDayTypeManual.addRow(new BooleanDescriptor(USE_MANUAL_DAY_TYPE_AID, "Use Manual Day-Type Aid", false));
    ctxDayTypeManual.addRow(new BooleanDescriptor(PREFER_MANUAL_DAY_TYPE_AID, "Prefer Manual Day-Type Aid", false));
    ctxDayTypeManual.addRow(new DoubleDescriptor(MANUAL_DAY_TYPE_RECENT_MEDIAN_IB_RANGE, "Manual Recent Median IB Range", 0.0, -1000000.0, 1000000.0, 0.25));
    ctxDayTypeManual.addRow(new DoubleDescriptor(MANUAL_DAY_TYPE_RECENT_MEDIAN_IB_VOLUME, "Manual Recent Median IB Volume", 0.0, 0.0, 1000000000.0, 1.0));
    ctxDayTypeManual.addRow(new DoubleDescriptor(MANUAL_DAY_TYPE_ATR_LIKE_RANGE, "Manual ATR-Like Range", 0.0, 0.0, 1000000.0, 0.25));
    ctxDayTypeManual.addRow(new DoubleDescriptor(MANUAL_DAY_TYPE_PRIOR_SESSION_CLOSE, "Manual Prior Session Close", 0.0, -1000000.0, 1000000.0, 0.25));

    SettingGroup ctxSource = ctxTab.addGroup("Source Policy");
    ctxSource.addRow(new BooleanDescriptor(USE_MANUAL_LEVEL_FALLBACK, "Use Manual Fallback When Auto Missing", true));
    ctxSource.addRow(new BooleanDescriptor(PREFER_MANUAL_LEVELS, "Prefer Manual Levels Over Auto", false));

    SettingGroup ctxManual1 = ctxTab.addGroup("Manual Daily / Overnight Levels");
    ctxManual1.addRow(new BooleanDescriptor(MANUAL_SESSION_OPEN_ENABLED, "Manual Session Open Enabled", false));
    ctxManual1.addRow(new DoubleDescriptor(MANUAL_SESSION_OPEN, "Manual Session Open", 0.0, -1000000.0, 1000000.0, 0.25));
    ctxManual1.addRow(new BooleanDescriptor(MANUAL_PRIOR_DAY_LEVELS_ENABLED, "Manual Prior Day Levels Enabled", false));
    ctxManual1.addRow(new DoubleDescriptor(MANUAL_PRIOR_DAY_HIGH, "Manual Prior Day High", 0.0, -1000000.0, 1000000.0, 0.25));
    ctxManual1.addRow(new DoubleDescriptor(MANUAL_PRIOR_DAY_LOW, "Manual Prior Day Low", 0.0, -1000000.0, 1000000.0, 0.25));
    ctxManual1.addRow(new DoubleDescriptor(MANUAL_PRIOR_DAY_CLOSE, "Manual Prior Day Close", 0.0, -1000000.0, 1000000.0, 0.25));
    ctxManual1.addRow(new BooleanDescriptor(MANUAL_OVERNIGHT_LEVELS_ENABLED, "Manual Overnight Levels Enabled", false));
    ctxManual1.addRow(new DoubleDescriptor(MANUAL_OVERNIGHT_HIGH, "Manual Overnight High", 0.0, -1000000.0, 1000000.0, 0.25));
    ctxManual1.addRow(new DoubleDescriptor(MANUAL_OVERNIGHT_LOW, "Manual Overnight Low", 0.0, -1000000.0, 1000000.0, 0.25));

    SettingGroup ctxManual2 = ctxTab.addGroup("Manual Opening Range / Initial Balance");
    ctxManual2.addRow(new BooleanDescriptor(MANUAL_OPENING_RANGE_LEVELS_ENABLED, "Manual Opening Range Levels Enabled", false));
    ctxManual2.addRow(new DoubleDescriptor(MANUAL_OPENING_RANGE_HIGH, "Manual Opening Range High", 0.0, -1000000.0, 1000000.0, 0.25));
    ctxManual2.addRow(new DoubleDescriptor(MANUAL_OPENING_RANGE_LOW, "Manual Opening Range Low", 0.0, -1000000.0, 1000000.0, 0.25));
    ctxManual2.addRow(new BooleanDescriptor(MANUAL_INITIAL_BALANCE_LEVELS_ENABLED, "Manual Initial Balance Levels Enabled", false));
    ctxManual2.addRow(new DoubleDescriptor(MANUAL_INITIAL_BALANCE_HIGH, "Manual Initial Balance High", 0.0, -1000000.0, 1000000.0, 0.25));
    ctxManual2.addRow(new DoubleDescriptor(MANUAL_INITIAL_BALANCE_LOW, "Manual Initial Balance Low", 0.0, -1000000.0, 1000000.0, 0.25));

    SettingGroup ctxManual3 = ctxTab.addGroup("Manual Prior Value Area");
    ctxManual3.addRow(new BooleanDescriptor(MANUAL_VALUE_AREA_LEVELS_ENABLED, "Manual Prior VA / POC Enabled", false));
    ctxManual3.addRow(new DoubleDescriptor(MANUAL_PRIOR_VALUE_AREA_HIGH, "Manual Prior VAH", 0.0, -1000000.0, 1000000.0, 0.25));
    ctxManual3.addRow(new DoubleDescriptor(MANUAL_PRIOR_VALUE_AREA_LOW, "Manual Prior VAL", 0.0, -1000000.0, 1000000.0, 0.25));
    ctxManual3.addRow(new DoubleDescriptor(MANUAL_PRIOR_POC, "Manual Prior POC", 0.0, -1000000.0, 1000000.0, 0.25));

    SettingGroup ctxDisplay = ctxTab.addGroup("Display");
    ctxDisplay.addRow(new BooleanDescriptor(SHOW_CONTEXT_ON_HUD, "Show Context On HUD", true));
    ctxDisplay.addRow(new BooleanDescriptor(SHOW_CONTEXT_REASONS, "Show Context Reasons", false));
    ctxDisplay.addRow(new BooleanDescriptor(SHOW_REFERENCE_SOURCES_ON_HUD, "Show Reference Sources On HUD", true));
    ctxDisplay.addRow(new BooleanDescriptor(SHOW_DEVELOPING_REFS_ON_HUD, "Show Developing Refs On HUD", true));
    ctxDisplay.addRow(new BooleanDescriptor(SHOW_DAY_TYPE_ON_HUD, "Show Day Type On HUD", true));
    ctxDisplay.addRow(new BooleanDescriptor(SHOW_DAY_TYPE_DEBUG, "Show Day Type Debug", false));

    SettingGroup ctx4e = ctxTab.addGroup("4E Control / Output");
    ctx4e.addRow(new IntegerDescriptor(CONTEXT_MODE, "Context Mode (0=Annot,1=Score,2=Bias,3=Filter)", 0, 0, 3, 1));
    ctx4e.addRow(new IntegerDescriptor(HUD_DISPLAY_MODE, "HUD Display Mode (0=Compact,1=Standard,2=Debug)", 1, 0, 2, 1));
    ctx4e.addRow(new DoubleDescriptor(MIN_MERGED_CONTEXT_SCORE_FOR_FILTER, "Min Merged Score For Filter", 1.0, 0.0, 10.0, 0.1));
    ctx4e.addRow(new DoubleDescriptor(MIN_STRUCTURAL_SCORE_FOR_FILTER, "Min Structural Score For Filter", 0.0, 0.0, 10.0, 0.1));
    ctx4e.addRow(new DoubleDescriptor(MIN_DAYTYPE_SCORE_FOR_FILTER, "Min Day-Type Score For Filter", 0.0, 0.0, 10.0, 0.1));
    ctx4e.addRow(new BooleanDescriptor(REQUIRE_SUPPORTS_SIDE_WHEN_FILTER, "Require Supports-Side In Filter Mode", true));
    ctx4e.addRow(new BooleanDescriptor(BLOCK_FREE_FLOATING_WHEN_FILTER, "Block Free-Floating In Filter Mode", false));
    ctx4e.addRow(new BooleanDescriptor(SHOW_FILTER_STATUS_ON_HUD, "Show Filter Status On HUD", true));

    setSettingsDescriptor(sd);

    RuntimeDescriptor rd = new RuntimeDescriptor();
    rd.declareSignal(LzsStudySignals.LZS_LONG, "LZS Long");
    rd.declareSignal(LzsStudySignals.LZS_SHORT, "LZS Short");
    rd.exportValue(new ValueDescriptor(LzsStudyValues.LONG_PHASE, "LZS Long Phase"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.SHORT_PHASE, "LZS Short Phase"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.LONG_ZONE_LOW, "LZS Long Zone Low"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.LONG_ZONE_HIGH, "LZS Long Zone High"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.SHORT_ZONE_LOW, "LZS Short Zone Low"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.SHORT_ZONE_HIGH, "LZS Short Zone High"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.LONG_SIGNAL_ZONE_LOW, "LZS Long Signal Zone Low"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.LONG_SIGNAL_ZONE_HIGH, "LZS Long Signal Zone High"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.SHORT_SIGNAL_ZONE_LOW, "LZS Short Signal Zone Low"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.SHORT_SIGNAL_ZONE_HIGH, "LZS Short Signal Zone High"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.LONG_EXEC_REF, "LZS Long Exec Ref"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.SHORT_EXEC_REF, "LZS Short Exec Ref"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.LONG_SIGNAL_EXEC_REF, "LZS Long Signal Exec Ref"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.SHORT_SIGNAL_EXEC_REF, "LZS Short Signal Exec Ref"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.LONG_REV_TICKS, "LZS Long Reversal Ticks"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.SHORT_REV_TICKS, "LZS Short Reversal Ticks"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.LONG_REMAINING_PCT, "LZS Long Remaining %"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.SHORT_REMAINING_PCT, "LZS Short Remaining %"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.LONG_PATH_CLEAR, "LZS Long Path Clear"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.SHORT_PATH_CLEAR, "LZS Short Path Clear"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.SESSION_OPEN, "LZS Session Open"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.PRIOR_DAY_HIGH, "LZS Prior Day High"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.PRIOR_DAY_LOW, "LZS Prior Day Low"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.PRIOR_DAY_CLOSE, "LZS Prior Day Close"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.OVERNIGHT_HIGH, "LZS Overnight High"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.OVERNIGHT_LOW, "LZS Overnight Low"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.SESSION_VWAP, "LZS Session VWAP"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.OR_HIGH, "LZS Opening Range High"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.OR_LOW, "LZS Opening Range Low"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.OR_COMPLETE, "LZS Opening Range Complete"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.IB_HIGH, "LZS Initial Balance High"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.IB_LOW, "LZS Initial Balance Low"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.IB_COMPLETE, "LZS Initial Balance Complete"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.PRIOR_VALUE_AREA_HIGH, "LZS Prior Value Area High"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.PRIOR_VALUE_AREA_LOW, "LZS Prior Value Area Low"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.PRIOR_POC, "LZS Prior POC"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.LONG_CONTEXT_SCORE, "LZS Long Context Score"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.SHORT_CONTEXT_SCORE, "LZS Short Context Score"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.LONG_CONTEXT_PASS, "LZS Long Context Pass"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.SHORT_CONTEXT_PASS, "LZS Short Context Pass"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.LONG_CONTEXT_INTENT, "LZS Long Context Intent"));
    rd.exportValue(new ValueDescriptor(LzsStudyValues.SHORT_CONTEXT_INTENT, "LZS Short Context Intent"));
    setRuntimeDescriptor(rd);
  }

  @Override
  public void clearState() {
    super.clearState();
    detachDomListener();
    clearHud(false);
    longState.resetLifecycle();
    shortState.resetLifecycle();
    longContext = new LzsContextResult();
    shortContext = new LzsContextResult();
    longMergedContext = new LzsMergedContextResult();
    shortMergedContext = new LzsMergedContextResult();
    sessionTracker.reset();
    ibTracker.reset();
    orTracker.reset();
    dayTypeProvider.reset();
    snapshotWindow.clear();
    lastEvalAt = Long.MIN_VALUE;
    lastSnapshotRecordedAt = Long.MIN_VALUE;
    lastHudRefreshAt = Long.MIN_VALUE;
    lastExecRefreshAt = Long.MIN_VALUE;
    resetDomHealthState();
    hudStateSignatureCache = "";
  }

  @Override
  public void onSettingsUpdated(DataContext ctx) {
    super.onSettingsUpdated(ctx);
    longState.resetLifecycle();
    shortState.resetLifecycle();
    longContext = new LzsContextResult();
    shortContext = new LzsContextResult();
    longMergedContext = new LzsMergedContextResult();
    shortMergedContext = new LzsMergedContextResult();
    sessionTracker.reset();
    ibTracker.reset();
    orTracker.reset();
    dayTypeProvider.reset();
    hudTextCache = null;
    hudPriceCache = Double.NaN;
    hudStateSignatureCache = "";
    lastEvalAt = Long.MIN_VALUE;
    lastHudRefreshAt = Long.MIN_VALUE;
    lastExecRefreshAt = Long.MIN_VALUE;
    resetDomHealthState();
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

    long now = System.currentTimeMillis();
    noteChartActivity(index, s, now);

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
    LzsContextConfig ctxCfg = buildContextConfig();
    sessionTracker.update(index, ctx);
    orTracker.update(index, ctx, sessionTracker, ctxCfg.openingRangeMinutes);
    ibTracker.update(index, ctx, sessionTracker, ctxCfg.ibMinutes);
    LzsContextSnapshot ctxSnap = buildContextSnapshot(index, ctx, ctxCfg);

    maybeHandleDomStale(now, ctx, ctxSnap.sessionStartTime);
    latest = latestSnapshot();
    if (latest != null) {
      latest.barIndex = index;
      latest.barStartTime = s.getStartTime(index);
      latest.barHigh = s.getHigh(index);
      latest.barLow = s.getLow(index);
      latest.lastPrice = s.getClose(index);
      latest.tickSize = ctx.getInstrument() == null ? 0.25 : safeTickSize(ctx.getInstrument());
    }

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
    longContext = contextEngine.evaluate(LzsSide.LONG, longState.candidate, ctxSnap, ctxCfg);
    shortContext = contextEngine.evaluate(LzsSide.SHORT, shortState.candidate, ctxSnap, ctxCfg);
    longMergedContext = contextMergeEngine.merge(LzsSide.LONG, longState.candidate, longContext, ctxSnap, ctxCfg);
    shortMergedContext = contextMergeEngine.merge(LzsSide.SHORT, shortState.candidate, shortContext, ctxSnap, ctxCfg);

    applyOpposingZoneConflict(LzsSide.LONG, longState, shortState, latest, cfg);
    applyOpposingZoneConflict(LzsSide.SHORT, shortState, longState, latest, cfg);

    String afterSig = buildStateSignature();
    boolean stateChanged = !afterSig.equals(beforeSig);

    boolean longSignalOut = longEmitted && shouldEmitWithContext(LzsSide.LONG, longMergedContext, ctxCfg)
        && !shouldBlockOnOpposingZone(longState, cfg);
    boolean shortSignalOut = shortEmitted && shouldEmitWithContext(LzsSide.SHORT, shortMergedContext, ctxCfg)
        && !shouldBlockOnOpposingZone(shortState, cfg);

    storeRuntimeValues(s, index, ctxSnap, longSignalOut, shortSignalOut);
    if (latest != null) {
      if (longSignalOut) emitSignal(ctx, index, LzsStudySignals.LZS_LONG, "LZS Long", latest.lastPrice, longState, longContext);
      if (shortSignalOut) emitSignal(ctx, index, LzsStudySignals.LZS_SHORT, "LZS Short", latest.lastPrice, shortState, shortContext);
    }

    updateHud(index, ctx, cfg, ctxCfg, ctxSnap, now, stateChanged);
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
    lastDomUpdateAt = Long.MIN_VALUE;
    lastBestBidAskUpdateAt = Long.MIN_VALUE;
    lastDepthSignatureChangeAt = Long.MIN_VALUE;
    lastObservedBestBid = Double.NaN;
    lastObservedBestAsk = Double.NaN;
    lastDepthSignature = 0;
  }

  private void recordSnapshot(DOM dom, int captureLevels) {
    if (dom == null) return;
    long now = System.currentTimeMillis();
    if (lastSnapshotRecordedAt != Long.MIN_VALUE && now == lastSnapshotRecordedAt) return;
    lastSnapshotRecordedAt = now;

    LzsSnapshot snap = buildSnapshot(dom, captureLevels);
    if (snap == null) return;

    lastDomUpdateAt = now;
    if (Double.compare(snap.bestBid, lastObservedBestBid) != 0 || Double.compare(snap.bestAsk, lastObservedBestAsk) != 0) {
      lastObservedBestBid = snap.bestBid;
      lastObservedBestAsk = snap.bestAsk;
      lastBestBidAskUpdateAt = now;
    }
    int sig = computeDepthSignature(snap);
    if (sig != lastDepthSignature) {
      lastDepthSignature = sig;
      lastDepthSignatureChangeAt = now;
    }
    domHealthStatus = "OK";

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
      LzsExecRow row = rows.get(Double.valueOf(px));
      if (row == null) {
        row = new LzsExecRow();
        row.price = px;
        rows.put(Double.valueOf(px), row);
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

  private void applyOpposingZoneConflict(LzsSide side, LzsSideState state, LzsSideState opposing, LzsSnapshot snap, LzsConfig cfg) {
    if (state == null || cfg == null) return;
    state.interaction.opposingZoneConflict = false;
    state.interaction.opposingZoneConflictDistanceTicks = Double.NaN;
    state.interaction.opposingZoneConflictTag = "";
    if (cfg.opposingZoneConflictMode <= 0 || snap == null || opposing == null || opposing.candidate == null || !opposing.candidate.valid) return;

    double tick = Math.max(1e-9, snap.tickSize);
    LzsZoneCandidate opp = opposing.candidate;
    double distTicks;
    if (side == LzsSide.LONG) {
      if (opp.zoneHigh < snap.lastPrice - 1e-9) return;
      distTicks = opp.zoneLow <= snap.lastPrice + 1e-9 ? 0.0 : (opp.zoneLow - snap.lastPrice) / tick;
    }
    else {
      if (opp.zoneLow > snap.lastPrice + 1e-9) return;
      distTicks = opp.zoneHigh >= snap.lastPrice - 1e-9 ? 0.0 : (snap.lastPrice - opp.zoneHigh) / tick;
    }
    if (distTicks > Math.max(0, cfg.opposingZoneConflictMaxDistanceTicks)) return;

    state.interaction.opposingZoneConflict = true;
    state.interaction.opposingZoneConflictDistanceTicks = Math.max(0.0, distTicks);
    state.interaction.opposingZoneConflictTag = String.format(java.util.Locale.US, "opp%s@%.1ft", opposing.side == LzsSide.LONG ? "Long" : "Short", Math.max(0.0, distTicks));
    String add = String.format(java.util.Locale.US, "LZS opp zone | %s %.2f-%.2f | D %.1ft",
        opposing.side == LzsSide.LONG ? "BID" : "ASK",
        opp.zoneLow, opp.zoneHigh, Math.max(0.0, distTicks));
    if (state.interaction.debug == null || state.interaction.debug.length() == 0) state.interaction.debug = add;
    else if (!state.interaction.debug.contains("LZS opp zone")) state.interaction.debug = state.interaction.debug + "\n" + add;
    if (state.interaction.reason == null || state.interaction.reason.length() == 0) state.interaction.reason = state.interaction.opposingZoneConflictTag;
    else if (!state.interaction.reason.contains("opp")) state.interaction.reason = state.interaction.reason + "/" + state.interaction.opposingZoneConflictTag;
  }

  private boolean shouldBlockOnOpposingZone(LzsSideState state, LzsConfig cfg) {
    return cfg != null && cfg.opposingZoneConflictMode >= 2 && state != null && state.interaction.opposingZoneConflict;
  }

  private void storeRuntimeValues(DataSeries s, int index, LzsContextSnapshot ctxSnap, boolean longEmitted, boolean shortEmitted) {
    s.setDouble(index, LzsStudyValues.LONG_PHASE, (double) longState.interaction.phase.ordinal());
    s.setDouble(index, LzsStudyValues.SHORT_PHASE, (double) shortState.interaction.phase.ordinal());
    s.setDouble(index, LzsStudyValues.LONG_SCORE, longState.interaction.score);
    s.setDouble(index, LzsStudyValues.SHORT_SCORE, shortState.interaction.score);
    s.setDouble(index, LzsStudyValues.LONG_ZONE_LOW, longState.candidate == null ? Double.NaN : longState.candidate.zoneLow);
    s.setDouble(index, LzsStudyValues.LONG_ZONE_HIGH, longState.candidate == null ? Double.NaN : longState.candidate.zoneHigh);
    s.setDouble(index, LzsStudyValues.SHORT_ZONE_LOW, shortState.candidate == null ? Double.NaN : shortState.candidate.zoneLow);
    s.setDouble(index, LzsStudyValues.SHORT_ZONE_HIGH, shortState.candidate == null ? Double.NaN : shortState.candidate.zoneHigh);
    s.setDouble(index, LzsStudyValues.LONG_EXEC_REF, longState.interaction.reversalRefPrice);
    s.setDouble(index, LzsStudyValues.SHORT_EXEC_REF, shortState.interaction.reversalRefPrice);
    s.setDouble(index, LzsStudyValues.LONG_SIGNAL_ZONE_LOW, longEmitted ? longState.signalZoneLow : Double.NaN);
    s.setDouble(index, LzsStudyValues.LONG_SIGNAL_ZONE_HIGH, longEmitted ? longState.signalZoneHigh : Double.NaN);
    s.setDouble(index, LzsStudyValues.SHORT_SIGNAL_ZONE_LOW, shortEmitted ? shortState.signalZoneLow : Double.NaN);
    s.setDouble(index, LzsStudyValues.SHORT_SIGNAL_ZONE_HIGH, shortEmitted ? shortState.signalZoneHigh : Double.NaN);
    s.setDouble(index, LzsStudyValues.LONG_SIGNAL_EXEC_REF, longEmitted ? longState.signalExecRef : Double.NaN);
    s.setDouble(index, LzsStudyValues.SHORT_SIGNAL_EXEC_REF, shortEmitted ? shortState.signalExecRef : Double.NaN);
    s.setDouble(index, LzsStudyValues.LONG_REV_TICKS, longState.interaction.reversalTicks);
    s.setDouble(index, LzsStudyValues.SHORT_REV_TICKS, shortState.interaction.reversalTicks);
    s.setDouble(index, LzsStudyValues.LONG_REMAINING_PCT, longState.interaction.remainingZonePct);
    s.setDouble(index, LzsStudyValues.SHORT_REMAINING_PCT, shortState.interaction.remainingZonePct);
    s.setDouble(index, LzsStudyValues.LONG_PATH_CLEAR, longState.interaction.pathClearTicks);
    s.setDouble(index, LzsStudyValues.SHORT_PATH_CLEAR, shortState.interaction.pathClearTicks);
    if (ctxSnap != null) {
      s.setDouble(index, LzsStudyValues.SESSION_OPEN, ctxSnap.sessionOpen.value);
      s.setDouble(index, LzsStudyValues.PRIOR_DAY_HIGH, ctxSnap.priorDayHigh.value);
      s.setDouble(index, LzsStudyValues.PRIOR_DAY_LOW, ctxSnap.priorDayLow.value);
      s.setDouble(index, LzsStudyValues.PRIOR_DAY_CLOSE, ctxSnap.priorDayClose.value);
      s.setDouble(index, LzsStudyValues.OVERNIGHT_HIGH, ctxSnap.overnightHigh.value);
      s.setDouble(index, LzsStudyValues.OVERNIGHT_LOW, ctxSnap.overnightLow.value);
      s.setDouble(index, LzsStudyValues.SESSION_VWAP, ctxSnap.sessionVwap.value);
      s.setDouble(index, LzsStudyValues.OR_HIGH, ctxSnap.openingRangeHigh.value);
      s.setDouble(index, LzsStudyValues.OR_LOW, ctxSnap.openingRangeLow.value);
      s.setBoolean(index, LzsStudyValues.OR_COMPLETE, ctxSnap.openingRangeComplete);
      s.setDouble(index, LzsStudyValues.IB_HIGH, ctxSnap.ibHigh.value);
      s.setDouble(index, LzsStudyValues.IB_LOW, ctxSnap.ibLow.value);
      s.setBoolean(index, LzsStudyValues.IB_COMPLETE, ctxSnap.ibComplete);
      s.setDouble(index, LzsStudyValues.PRIOR_VALUE_AREA_HIGH, ctxSnap.priorValueAreaHigh.value);
      s.setDouble(index, LzsStudyValues.PRIOR_VALUE_AREA_LOW, ctxSnap.priorValueAreaLow.value);
      s.setDouble(index, LzsStudyValues.PRIOR_POC, ctxSnap.priorPoc.value);
    }
    s.setDouble(index, LzsStudyValues.LONG_CONTEXT_SCORE, longMergedContext == null ? (longContext == null ? Double.NaN : longContext.totalScore) : longMergedContext.sideScore);
    s.setDouble(index, LzsStudyValues.SHORT_CONTEXT_SCORE, shortMergedContext == null ? (shortContext == null ? Double.NaN : shortContext.totalScore) : shortMergedContext.sideScore);
    s.setBoolean(index, LzsStudyValues.LONG_CONTEXT_PASS, longMergedContext != null && longMergedContext.passesFilter);
    s.setBoolean(index, LzsStudyValues.SHORT_CONTEXT_PASS, shortMergedContext != null && shortMergedContext.passesFilter);
    s.setDouble(index, LzsStudyValues.LONG_CONTEXT_INTENT, longMergedContext == null || longMergedContext.intent == null ? Double.NaN : (double) longMergedContext.intent.ordinal());
    s.setDouble(index, LzsStudyValues.SHORT_CONTEXT_INTENT, shortMergedContext == null || shortMergedContext.intent == null ? Double.NaN : (double) shortMergedContext.intent.ordinal());
    if (longEmitted) s.setBoolean(index, LzsStudyValues.LONG_FIRED, true);
    if (shortEmitted) s.setBoolean(index, LzsStudyValues.SHORT_FIRED, true);
  }

  private boolean shouldEmitWithContext(LzsSide side, LzsMergedContextResult merged, LzsContextConfig cfg) {
    if (cfg == null || cfg.contextMode != LzsContextMode.OPTIONAL_FILTER) return true;
    if (merged == null) return false;
    return merged.passesFilter;
  }

  private void emitSignal(DataContext ctx, int index, LzsStudySignals signal, String label, double price, LzsSideState state, LzsContextResult context) {
    if (ctx == null || state == null) return;
    Instrument instr = ctx.getInstrument();
    String priceText = instr == null ? LzsFormatUtils.fmt2(price) : instr.format(price);
    String msg = label + " | " + priceText + " | Rev " + LzsFormatUtils.fmt1(state.interaction.reversalTicks)
        + "t | Path " + LzsFormatUtils.fmt1(state.interaction.pathClearTicks) + "t";
    if (context != null && context.summary != null && context.summary.length() > 0 && !context.freeFloating) {
      msg += " | " + context.summary;
    }
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

  private void noteChartActivity(int index, DataSeries s, long now) {
    if (s == null) return;
    double close = s.getClose(index);
    if (index != lastObservedBarIndex || Double.compare(close, lastObservedClose) != 0) {
      lastObservedBarIndex = index;
      lastObservedClose = close;
      lastChartActivityAt = now;
    }
  }

  private void maybeHandleDomStale(long now, DataContext ctx, long sessionStartTime) {
    if (!getSettings().getBoolean(ENABLE_DOM_STALE_DETECTION, true)) {
      domHealthStatus = "OFF";
      return;
    }
    rollDomResetSession(sessionStartTime);
    long domStaleMs = Math.max(250L, getSettings().getInteger(DOM_STALE_THRESHOLD_MS, 3000));
    long depthStaleMs = Math.max(domStaleMs, getSettings().getInteger(DEPTH_SIGNATURE_STALE_THRESHOLD_MS, 5000));

    boolean chartActive = lastChartActivityAt != Long.MIN_VALUE && (now - lastChartActivityAt) <= Math.max(1500L, domStaleMs * 2L);
    boolean domOld = lastDomUpdateAt == Long.MIN_VALUE || (now - lastDomUpdateAt) >= domStaleMs;
    boolean bboOld = lastBestBidAskUpdateAt == Long.MIN_VALUE || (now - lastBestBidAskUpdateAt) >= domStaleMs;
    boolean depthOld = lastDepthSignatureChangeAt == Long.MIN_VALUE || (now - lastDepthSignatureChangeAt) >= depthStaleMs;
    boolean hardStale = listenerAttached && chartActive && domOld && (bboOld || depthOld);

    if (!hardStale) {
      if (listenerAttached) domHealthStatus = "OK";
      else domHealthStatus = "WAIT";
      return;
    }

    domHealthStatus = "STALE";
    if (!getSettings().getBoolean(AUTO_RESET_DOM_ON_STALE, true)) return;

    int maxResets = Math.max(0, getSettings().getInteger(MAX_DOM_AUTO_RESETS_PER_SESSION, 3));
    if (domAutoResetCount >= maxResets) {
      domHealthStatus = "STALE_MAX";
      return;
    }

    long cooldownMs = Math.max(2000L, domStaleMs / 2L);
    if (lastDomAutoResetAt != Long.MIN_VALUE && (now - lastDomAutoResetAt) < cooldownMs) {
      domHealthStatus = "RECOVER";
      return;
    }

    autoResetDomAdapter(ctx, now);
  }

  private void autoResetDomAdapter(DataContext ctx, long now) {
    longState.resetLifecycle();
    shortState.resetLifecycle();
    longContext = new LzsContextResult();
    shortContext = new LzsContextResult();
    longMergedContext = new LzsMergedContextResult();
    shortMergedContext = new LzsMergedContextResult();
    lastEvalAt = Long.MIN_VALUE;
    lastExecRefreshAt = Long.MIN_VALUE;
    snapshotWindow.clear();
    detachDomListener();
    if (ctx != null) initDomListener(ctx);
    domAutoResetCount++;
    lastDomAutoResetAt = now;
    domHealthStatus = "RESET" + domAutoResetCount;
  }

  private void rollDomResetSession(long sessionStartTime) {
    if (sessionStartTime == Long.MIN_VALUE) return;
    if (domResetSessionStartTime != sessionStartTime) {
      domResetSessionStartTime = sessionStartTime;
      domAutoResetCount = 0;
    }
  }

  private int computeDepthSignature(LzsSnapshot snap) {
    if (snap == null) return 0;
    int hash = 17;
    int bidLim = Math.min(6, snap.bidRowsNear.size());
    int askLim = Math.min(6, snap.askRowsNear.size());
    for (int i = 0; i < bidLim; i++) {
      LzsRow r = snap.bidRowsNear.get(i);
      hash = 31 * hash + Double.hashCode(r.price);
      hash = 31 * hash + Double.hashCode(r.size);
    }
    for (int i = 0; i < askLim; i++) {
      LzsRow r = snap.askRowsNear.get(i);
      hash = 31 * hash + Double.hashCode(r.price);
      hash = 31 * hash + Double.hashCode(r.size);
    }
    return hash;
  }

  private void resetDomHealthState() {
    lastDomUpdateAt = Long.MIN_VALUE;
    lastBestBidAskUpdateAt = Long.MIN_VALUE;
    lastDepthSignatureChangeAt = Long.MIN_VALUE;
    lastChartActivityAt = Long.MIN_VALUE;
    lastDomAutoResetAt = Long.MIN_VALUE;
    domResetSessionStartTime = Long.MIN_VALUE;
    domAutoResetCount = 0;
    lastDepthSignature = 0;
    lastObservedBarIndex = -1;
    lastObservedClose = Double.NaN;
    lastObservedBestBid = Double.NaN;
    lastObservedBestAsk = Double.NaN;
    domHealthStatus = "WAIT";
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
    cfg.opposingZoneConflictMode = getSettings().getInteger(OPPOSING_ZONE_CONFLICT_MODE, 0);
    cfg.opposingZoneConflictMaxDistanceTicks = getSettings().getInteger(OPPOSING_ZONE_CONFLICT_MAX_DISTANCE_TICKS, cfg.zoneMaxDistanceTicks);
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

  private LzsContextConfig buildContextConfig() {
    LzsContextConfig cfg = LzsContextConfig.defaults();
    cfg.enableContext = getSettings().getBoolean(ENABLE_CONTEXT, true);
    cfg.enableStructuralRefs = getSettings().getBoolean(ENABLE_STRUCTURAL_CONTEXT, true);
    cfg.enableOvernightRefs = getSettings().getBoolean(ENABLE_OVERNIGHT_CONTEXT, true);
    cfg.enableVwapRef = getSettings().getBoolean(ENABLE_VWAP_CONTEXT, true);
    cfg.enableIbRefs = getSettings().getBoolean(ENABLE_IB_CONTEXT, true);
    cfg.enableOrRefs = getSettings().getBoolean(ENABLE_OR_CONTEXT, true);
    cfg.enableValueAreaRefs = getSettings().getBoolean(ENABLE_VALUE_AREA_CONTEXT, true);
    cfg.structuralProximityTicks = getSettings().getInteger(STRUCTURAL_PROX_TICKS, 8);
    cfg.overnightProximityTicks = getSettings().getInteger(OVERNIGHT_PROX_TICKS, 8);
    cfg.vwapProximityTicks = getSettings().getInteger(VWAP_PROX_TICKS, 8);
    cfg.openingRangeProximityTicks = getSettings().getInteger(OR_PROX_TICKS, 6);
    cfg.ibProximityTicks = getSettings().getInteger(IB_PROX_TICKS, 6);
    cfg.valueAreaProximityTicks = getSettings().getInteger(VALUE_AREA_PROX_TICKS, 6);
    cfg.openingRangeMinutes = getSettings().getInteger(OR_MINUTES, 5);
    cfg.ibMinutes = getSettings().getInteger(IB_MINUTES, 60);
    cfg.useManualLevelFallback = getSettings().getBoolean(USE_MANUAL_LEVEL_FALLBACK, true);
    cfg.preferManualLevels = getSettings().getBoolean(PREFER_MANUAL_LEVELS, false);
    cfg.manualSessionOpenEnabled = getSettings().getBoolean(MANUAL_SESSION_OPEN_ENABLED, false);
    cfg.manualSessionOpen = manualValue(MANUAL_SESSION_OPEN, cfg.manualSessionOpenEnabled);
    cfg.manualPriorDayLevelsEnabled = getSettings().getBoolean(MANUAL_PRIOR_DAY_LEVELS_ENABLED, false);
    cfg.manualPriorDayHigh = manualValue(MANUAL_PRIOR_DAY_HIGH, cfg.manualPriorDayLevelsEnabled);
    cfg.manualPriorDayLow = manualValue(MANUAL_PRIOR_DAY_LOW, cfg.manualPriorDayLevelsEnabled);
    cfg.manualPriorDayClose = manualValue(MANUAL_PRIOR_DAY_CLOSE, cfg.manualPriorDayLevelsEnabled);
    cfg.manualOvernightLevelsEnabled = getSettings().getBoolean(MANUAL_OVERNIGHT_LEVELS_ENABLED, false);
    cfg.manualOvernightHigh = manualValue(MANUAL_OVERNIGHT_HIGH, cfg.manualOvernightLevelsEnabled);
    cfg.manualOvernightLow = manualValue(MANUAL_OVERNIGHT_LOW, cfg.manualOvernightLevelsEnabled);
    cfg.manualOpeningRangeLevelsEnabled = getSettings().getBoolean(MANUAL_OPENING_RANGE_LEVELS_ENABLED, false);
    cfg.manualOpeningRangeHigh = manualValue(MANUAL_OPENING_RANGE_HIGH, cfg.manualOpeningRangeLevelsEnabled);
    cfg.manualOpeningRangeLow = manualValue(MANUAL_OPENING_RANGE_LOW, cfg.manualOpeningRangeLevelsEnabled);
    cfg.manualInitialBalanceLevelsEnabled = getSettings().getBoolean(MANUAL_INITIAL_BALANCE_LEVELS_ENABLED, false);
    cfg.manualInitialBalanceHigh = manualValue(MANUAL_INITIAL_BALANCE_HIGH, cfg.manualInitialBalanceLevelsEnabled);
    cfg.manualInitialBalanceLow = manualValue(MANUAL_INITIAL_BALANCE_LOW, cfg.manualInitialBalanceLevelsEnabled);
    cfg.manualValueAreaLevelsEnabled = getSettings().getBoolean(MANUAL_VALUE_AREA_LEVELS_ENABLED, false);
    cfg.manualPriorValueAreaHigh = manualValue(MANUAL_PRIOR_VALUE_AREA_HIGH, cfg.manualValueAreaLevelsEnabled);
    cfg.manualPriorValueAreaLow = manualValue(MANUAL_PRIOR_VALUE_AREA_LOW, cfg.manualValueAreaLevelsEnabled);
    cfg.manualPriorPoc = manualValue(MANUAL_PRIOR_POC, cfg.manualValueAreaLevelsEnabled);
    cfg.showContextOnHud = getSettings().getBoolean(SHOW_CONTEXT_ON_HUD, true);
    cfg.showContextReasons = getSettings().getBoolean(SHOW_CONTEXT_REASONS, false);
    cfg.showReferenceSourcesOnHud = getSettings().getBoolean(SHOW_REFERENCE_SOURCES_ON_HUD, true);
    cfg.showDevelopingReferencesOnHud = getSettings().getBoolean(SHOW_DEVELOPING_REFS_ON_HUD, true);
    cfg.enableDayTypeContext = getSettings().getBoolean(ENABLE_DAY_TYPE_CONTEXT, true);
    cfg.dayTypeRefreshIntervalMs = getSettings().getInteger(DAY_TYPE_REFRESH_INTERVAL_MS, 5000);
    cfg.dayTypeRecentLookbackSessions = getSettings().getInteger(DAY_TYPE_RECENT_LOOKBACK_SESSIONS, 20);
    cfg.dayTypeScoreWeight = getSettings().getDouble(DAY_TYPE_SCORE_WEIGHT, 1.0);
    cfg.dayTypeMinConfidence = getSettings().getDouble(DAY_TYPE_MIN_CONFIDENCE, 7.5);
    cfg.useManualDayTypeAid = getSettings().getBoolean(USE_MANUAL_DAY_TYPE_AID, false);
    cfg.preferManualDayTypeAid = getSettings().getBoolean(PREFER_MANUAL_DAY_TYPE_AID, false);
    cfg.manualRecentMedianIbRange = safeManual(getSettings().getDouble(MANUAL_DAY_TYPE_RECENT_MEDIAN_IB_RANGE, 0.0));
    cfg.manualRecentMedianIbVolume = safeManual(getSettings().getDouble(MANUAL_DAY_TYPE_RECENT_MEDIAN_IB_VOLUME, 0.0));
    cfg.manualAtrLikeRange = safeManual(getSettings().getDouble(MANUAL_DAY_TYPE_ATR_LIKE_RANGE, 0.0));
    cfg.manualPriorSessionClose = safeManual(getSettings().getDouble(MANUAL_DAY_TYPE_PRIOR_SESSION_CLOSE, 0.0));
    cfg.showDayTypeOnHud = getSettings().getBoolean(SHOW_DAY_TYPE_ON_HUD, true);
    cfg.showDayTypeDebug = getSettings().getBoolean(SHOW_DAY_TYPE_DEBUG, false);
    cfg.contextMode = LzsContextMode.fromCode(getSettings().getInteger(CONTEXT_MODE, 0));
    cfg.hudDisplayMode = LzsHudDisplayMode.fromCode(getSettings().getInteger(HUD_DISPLAY_MODE, 1));
    cfg.minMergedContextScoreForFilter = getSettings().getDouble(MIN_MERGED_CONTEXT_SCORE_FOR_FILTER, 1.0);
    cfg.minStructuralScoreForFilter = getSettings().getDouble(MIN_STRUCTURAL_SCORE_FOR_FILTER, 0.0);
    cfg.minDayTypeScoreForFilter = getSettings().getDouble(MIN_DAYTYPE_SCORE_FOR_FILTER, 0.0);
    cfg.requireSupportsSideWhenFilterEnabled = getSettings().getBoolean(REQUIRE_SUPPORTS_SIDE_WHEN_FILTER, true);
    cfg.blockFreeFloatingWhenFilterEnabled = getSettings().getBoolean(BLOCK_FREE_FLOATING_WHEN_FILTER, false);
    cfg.showFilterStatusOnHud = getSettings().getBoolean(SHOW_FILTER_STATUS_ON_HUD, true);
    return cfg;
  }

  private LzsContextSnapshot buildContextSnapshot(int index, DataContext ctx, LzsContextConfig cfg) {
    LzsContextSnapshot out = new LzsContextSnapshot();
    if (ctx == null) return out;
    DataSeries s = ctx.getDataSeries();
    Instrument instr = ctx.getInstrument();
    if (s == null || instr == null || index < 0 || index >= s.size()) return out;

    out.barTime = s.getStartTime(index);
    out.tickSize = safeTickSize(instr);
    out.lastPrice = s.getClose(index);
    out.sessionHigh = sessionTracker.getSessionHigh();
    out.sessionLow = sessionTracker.getSessionLow();
    if (!Double.isNaN(out.sessionHigh) && !Double.isNaN(out.sessionLow) && out.sessionHigh > out.sessionLow && !Double.isNaN(out.lastPrice)) {
      out.sessionRangePct = Math.max(0.0, Math.min(1.0, (out.lastPrice - out.sessionLow) / (out.sessionHigh - out.sessionLow)));
    }
    out.sessionStartTime = sessionTracker.getCurrentSessionStartTime();
    out.inRthSession = out.sessionStartTime != Long.MIN_VALUE && out.barTime >= out.sessionStartTime;
    out.ibComplete = ibTracker.isIbComplete();
    out.openingRangeComplete = orTracker.isComplete();
    out.dayType.reset();
    copyDayType(out, dayTypeProvider.evaluate(index, ctx, out.sessionStartTime, cfg));

    out.sessionOpen.with(referenceResolver.resolveStatic("OPEN", sessionTracker.getSessionOpen(), !Double.isNaN(sessionTracker.getSessionOpen()), cfg.manualSessionOpenEnabled, cfg.manualSessionOpen, cfg));
    out.priorDayHigh.with(referenceResolver.resolveStatic("PDH", sessionTracker.getPriorSessionHigh(), !Double.isNaN(sessionTracker.getPriorSessionHigh()), cfg.manualPriorDayLevelsEnabled, cfg.manualPriorDayHigh, cfg));
    out.priorDayLow.with(referenceResolver.resolveStatic("PDL", sessionTracker.getPriorSessionLow(), !Double.isNaN(sessionTracker.getPriorSessionLow()), cfg.manualPriorDayLevelsEnabled, cfg.manualPriorDayLow, cfg));
    out.priorDayClose.with(referenceResolver.resolveStatic("PDC", sessionTracker.getPriorSessionClose(), !Double.isNaN(sessionTracker.getPriorSessionClose()), cfg.manualPriorDayLevelsEnabled, cfg.manualPriorDayClose, cfg));
    out.overnightHigh.with(referenceResolver.resolveStatic("ONH", sessionTracker.getOvernightHigh(), !Double.isNaN(sessionTracker.getOvernightHigh()), cfg.manualOvernightLevelsEnabled, cfg.manualOvernightHigh, cfg));
    out.overnightLow.with(referenceResolver.resolveStatic("ONL", sessionTracker.getOvernightLow(), !Double.isNaN(sessionTracker.getOvernightLow()), cfg.manualOvernightLevelsEnabled, cfg.manualOvernightLow, cfg));
    out.sessionVwap.with(referenceResolver.resolveStatic("VWAP", sessionTracker.getSessionVwap(), !Double.isNaN(sessionTracker.getSessionVwap()), false, Double.NaN, cfg));
    out.openingRangeHigh.with(referenceResolver.resolveWindowed("ORH", orTracker.getOrHigh(), orTracker.hasOrValues(), orTracker.isComplete(), cfg.manualOpeningRangeLevelsEnabled, cfg.manualOpeningRangeHigh, cfg));
    out.openingRangeLow.with(referenceResolver.resolveWindowed("ORL", orTracker.getOrLow(), orTracker.hasOrValues(), orTracker.isComplete(), cfg.manualOpeningRangeLevelsEnabled, cfg.manualOpeningRangeLow, cfg));
    out.ibHigh.with(referenceResolver.resolveWindowed("IBH", ibTracker.getIbHigh(), ibTracker.hasIbValues(), ibTracker.isIbComplete(), cfg.manualInitialBalanceLevelsEnabled, cfg.manualInitialBalanceHigh, cfg));
    out.ibLow.with(referenceResolver.resolveWindowed("IBL", ibTracker.getIbLow(), ibTracker.hasIbValues(), ibTracker.isIbComplete(), cfg.manualInitialBalanceLevelsEnabled, cfg.manualInitialBalanceLow, cfg));
    out.priorValueAreaHigh.with(referenceResolver.resolveStatic("VAH", Double.NaN, false, cfg.manualValueAreaLevelsEnabled, cfg.manualPriorValueAreaHigh, cfg));
    out.priorValueAreaLow.with(referenceResolver.resolveStatic("VAL", Double.NaN, false, cfg.manualValueAreaLevelsEnabled, cfg.manualPriorValueAreaLow, cfg));
    out.priorPoc.with(referenceResolver.resolveStatic("POC", Double.NaN, false, cfg.manualValueAreaLevelsEnabled, cfg.manualPriorPoc, cfg));
    return out;
  }


  private void copyDayType(LzsContextSnapshot out, study_examples.lzs.context.LzsDayTypeContext src) {
    if (out == null || src == null) return;
    out.dayType.state = src.state;
    out.dayType.archetypeState = src.archetypeState;
    out.dayType.liveState = src.liveState;
    out.dayType.ibComplete = src.ibComplete;
    out.dayType.ready = src.ready;
    out.dayType.applicable = src.applicable;
    out.dayType.confidence = src.confidence;
    out.dayType.trendUpScore = src.trendUpScore;
    out.dayType.trendDownScore = src.trendDownScore;
    out.dayType.rangeScore = src.rangeScore;
    out.dayType.liquidationScore = src.liquidationScore;
    out.dayType.supportsLongContinuation = src.supportsLongContinuation;
    out.dayType.supportsShortContinuation = src.supportsShortContinuation;
    out.dayType.supportsFade = src.supportsFade;
    out.dayType.manualAidUsed = src.manualAidUsed;
    out.dayType.historicalNormReady = src.historicalNormReady;
    out.dayType.lookbackSessionsUsed = src.lookbackSessionsUsed;
    out.dayType.summary = src.summary;
    out.dayType.reasons = src.reasons;
    out.dayType.debugText = src.debugText;
  }

  private double manualValue(String key, boolean enabled) {
    if (!enabled) return Double.NaN;
    double v = getSettings().getDouble(key, 0.0);
    return Math.abs(v) < 1e-9 ? Double.NaN : v;
  }

  private void updateHud(int index, DataContext ctx, LzsConfig cfg, LzsContextConfig ctxCfg, LzsContextSnapshot ctxSnap, long now, boolean stateChanged) {
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
    String text = buildHudText(cfg, ctxCfg, ctxSnap);
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

  private String buildHudText(LzsConfig cfg, LzsContextConfig ctxCfg, LzsContextSnapshot ctxSnap) {
    StringBuilder sb = new StringBuilder();
    sb.append("LZS HUD\n");
    if (getSettings().getBoolean(SHOW_DOM_HEALTH_ON_HUD, false)) {
      sb.append(buildDomHealthLine(System.currentTimeMillis()));
      sb.append("\n");
    }
    if (ctxCfg != null && ctxCfg.showContextOnHud && ctxSnap != null) {
      sb.append(LzsFormatUtils.buildGlobalContextLine(ctxSnap, ctxCfg));
      sb.append("\n");
    }
    sb.append(LzsFormatUtils.buildHudSide(longState, cfg, longContext, longMergedContext, ctxCfg));
    sb.append("\n");
    sb.append(LzsFormatUtils.buildHudSide(shortState, cfg, shortContext, shortMergedContext, ctxCfg));
    return sb.toString();
  }

  private String buildDomHealthLine(long now) {
    double domAge = ageSeconds(now, lastDomUpdateAt);
    double quoteAge = ageSeconds(now, lastBestBidAskUpdateAt);
    double depthAge = ageSeconds(now, lastDepthSignatureChangeAt);
    StringBuilder sb = new StringBuilder();
    sb.append("DOM ").append(domHealthStatus);
    if (domAge >= 0.0) sb.append(" | D ").append(LzsFormatUtils.fmt1(domAge)).append("s");
    if (quoteAge >= 0.0) sb.append(" | Q ").append(LzsFormatUtils.fmt1(quoteAge)).append("s");
    if (depthAge >= 0.0) sb.append(" | Sig ").append(LzsFormatUtils.fmt1(depthAge)).append("s");
    if (domAutoResetCount > 0) sb.append(" | R ").append(domAutoResetCount);
    return sb.toString();
  }

  private double ageSeconds(long now, long then) {
    if (then == Long.MIN_VALUE || now < then) return -1.0;
    return (now - then) / 1000.0;
  }

  private String buildStateSignature() {
    return buildSideSignature(longState, longContext, longMergedContext) + "||" + buildSideSignature(shortState, shortContext, shortMergedContext);
  }

  private String buildSideSignature(LzsSideState state, LzsContextResult context, LzsMergedContextResult merged) {
    if (state == null) return "NA";
    String candSig = state.candidate == null ? "-" : state.candidate.signature();
    String dbg = state.interaction.debug == null ? "" : state.interaction.debug;
    return state.side + "|" + state.interaction.phase + "|" + candSig + "|" + dbg + "|"
        + LzsFormatUtils.fmt1(state.interaction.execSameSideVol) + "|"
        + state.interaction.bubbleCount + "|" + LzsFormatUtils.fmt2(state.interaction.aggressionShare) + "|"
        + LzsFormatUtils.fmt1(state.interaction.reversalTicks) + "|"
        + LzsFormatUtils.fmt2(state.interaction.remainingZonePct) + "|"
        + LzsFormatUtils.fmt1(state.interaction.pathClearTicks) + "|"
        + (context == null ? "" : context.summary) + "|"
        + (merged == null ? "" : merged.summary);
  }

  private double safeManual(double v) {
    return Math.abs(v) < 1e-9 ? Double.NaN : v;
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
