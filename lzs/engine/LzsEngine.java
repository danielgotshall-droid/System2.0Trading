package study_examples.lzs.engine;

import java.util.ArrayList;
import java.util.List;

import study_examples.lzs.model.*;

public final class LzsEngine {

  public LzsEngineResult evaluate(LzsSide side, LzsSideState state, LzsSnapshot snap, LzsConfig cfg) {
    if (state == null) state = new LzsSideState(side);
    boolean enabled = side == LzsSide.LONG ? cfg.enableLong : cfg.enableShort;
    if (!enabled) {
      state.resetLifecycle();
      state.interaction.debug = "LZS disabled";
      return new LzsEngineResult(side, state, false);
    }
    if (snap == null || !snap.hasBook()) {
      state.interaction.debug = "Waiting for DOM rows";
      return new LzsEngineResult(side, state, false);
    }

    state.interaction.updatesSeen++;
    boolean emitted = false;

    boolean hardLocked = isHardLocked(state);
    boolean softLocked = state.activeLifecycleLocked && !hardLocked;

    if (!hardLocked) {
      LzsZoneCandidate liveCand = findCandidate(side, snap, cfg);
      if (liveCand == null || !liveCand.valid) {
        String debug = softLocked
            ? "LZS reset | zone lost before exec"
            : "LZS no active zone";
        state.resetLifecycle();
        state.interaction.phase = LzsPhase.IDLE;
        state.interaction.debug = debug;
        return new LzsEngineResult(side, state, false);
      }

      updateDistanceFromPrice(liveCand, snap.lastPrice, snap.tickSize);
      String liveSig = liveCand.signature();
      boolean sameZone = state.candidate != null && liveSig.equals(state.candidate.signature());
      if (!sameZone) {
        state.resetLifecycle();
        state.candidate = liveCand.copy();
        state.interaction.phase = LzsPhase.ZONE_FOUND;
        state.interaction.valid = true;
        state.interaction.debug = buildCandidateDebug(state.candidate);
        softLocked = false;
      }
      else {
        state.candidate = liveCand.copy();
      }

      if (!isArmed(state.candidate, snap.lastPrice, snap.tickSize, cfg.armProximityTicks)) {
        if (softLocked) {
          state.resetLifecycle();
          state.interaction.phase = LzsPhase.IDLE;
          state.interaction.debug = "LZS reset | drifted away before exec";
        }
        else {
          state.interaction.debug = buildCandidateDebug(state.candidate);
        }
        return new LzsEngineResult(side, state, false);
      }

      if (!state.activeLifecycleLocked) {
        latchActiveLifecycle(state, snap);
      }
      else {
        state.activeZoneSignature = state.candidate == null ? "" : state.candidate.signature();
        state.interaction.valid = true;
      }
      hardLocked = false;
    }
    else if (state.candidate == null || !state.candidate.valid) {
      state.resetLifecycle();
      state.interaction.debug = "LZS active zone missing";
      return new LzsEngineResult(side, state, false);
    }

    LzsZoneCandidate cand = state.candidate;
    updateDistanceFromPrice(cand, snap.lastPrice, snap.tickSize);

    if (isTouched(cand, snap.barHigh, snap.barLow)) {
      if (state.interaction.touchTime == Long.MIN_VALUE) {
        state.interaction.touchTime = snap.time;
        state.interaction.touchPrice = snap.lastPrice;
      }
      state.interaction.updatesSinceTouch++;
      if (state.interaction.phase.ordinal() < LzsPhase.TOUCHED.ordinal()) {
        state.interaction.phase = LzsPhase.TOUCHED;
      }
    }

    if (state.interaction.phase.ordinal() >= LzsPhase.ARMED.ordinal()
        && state.interaction.phase.ordinal() < LzsPhase.EXECUTION_CONFIRMED.ordinal()) {
      state.interaction.updatesSinceArm++;
      LzsExecutionStats stats = computeExecutionStats(cand, snap, cfg);
      populateExecutionFields(state, stats);

      if (stats.qualifies(cfg)) {
        state.interaction.phase = LzsPhase.EXECUTION_CONFIRMED;
        state.activeLifecycleLocked = true;
        state.activeZoneSignature = state.candidate == null ? "" : state.candidate.signature();
        if (state.interaction.execConfirmTime == Long.MIN_VALUE) {
          state.interaction.execConfirmTime = snap.time;
          double execRef = !Double.isNaN(stats.referencePrice) ? stats.referencePrice : snap.lastPrice;
          state.interaction.execConfirmPrice = execRef;
          state.interaction.reversalRefPrice = execRef;
        }
        state.interaction.debug = buildExecDebug(state);
      }
      else if (state.interaction.updatesSinceArm > Math.max(1, cfg.maxUpdatesFromArmToExec)) {
        fail(state, "timed out", "LZS fail | timed out before exec confirm");
        return new LzsEngineResult(side, state, false);
      }
      else {
        state.interaction.debug = buildExecPendingDebug(state);
        return new LzsEngineResult(side, state, false);
      }
    }

    if (state.interaction.phase == LzsPhase.EXECUTION_CONFIRMED ||
        state.interaction.phase == LzsPhase.REVERSAL_CONFIRMED) {
      state.interaction.updatesSinceExec++;
      state.interaction.reversalTicks = computeReversalTicksFromExec(state, snap);
      state.interaction.maxFavorableTicksFromExec = Math.max(state.interaction.maxFavorableTicksFromExec,
          state.interaction.reversalTicks);
      if (state.interaction.reversalTicks >= Math.max(0, cfg.minReversalTicks)) {
        state.interaction.phase = LzsPhase.REVERSAL_CONFIRMED;
        if (state.interaction.reverseTime == Long.MIN_VALUE) {
          state.interaction.reverseTime = snap.time;
        }
        state.interaction.debug = buildReversalDebug(state);
      }
      else if (state.interaction.updatesSinceExec > Math.max(1, cfg.maxUpdatesToReverse)) {
        fail(state, "timed out", "LZS fail | timed out before reversal");
        return new LzsEngineResult(side, state, false);
      }
      else {
        state.interaction.debug = buildReversalPendingDebug(state);
        return new LzsEngineResult(side, state, false);
      }
    }

    if (state.interaction.phase.ordinal() >= LzsPhase.REVERSAL_CONFIRMED.ordinal()
        && state.interaction.phase.ordinal() < LzsPhase.COOLDOWN.ordinal()) {
      boolean protectionReady = evaluateProtection(state, snap, cfg);
      if (!protectionReady) {
        return new LzsEngineResult(side, state, false);
      }

      boolean pathReady = evaluatePath(state, snap, cfg);
      if (!pathReady) {
        state.interaction.phase = LzsPhase.PROTECTED;
        state.interaction.debug = buildPathBlockedDebug(state);
        return new LzsEngineResult(side, state, false);
      }

      if (isCooldownActive(state, snap, cfg)) {
        String cooldownDebug = buildCooldownDebug(state);
        state.resetLifecycle();
        state.interaction.debug = cooldownDebug;
        state.interaction.reason = "cooldown";
        return new LzsEngineResult(side, state, false);
      }

      if (!state.fired) {
        state.interaction.phase = LzsPhase.SIGNAL_READY;
        state.markEmitted(state.activeZoneSignature, snap.time, snap.barIndex);
        String fireDebug = buildSignalReadyDebug(state);
        emitted = true;
        state.resetLifecycle();
        state.interaction.debug = fireDebug;
        state.interaction.reason = "cooldown";
      }
    }

    return new LzsEngineResult(side, state, emitted);
  }

  private boolean isHardLocked(LzsSideState state) {
    if (state == null || !state.activeLifecycleLocked) return false;
    LzsPhase p = state.interaction.phase;
    return p == LzsPhase.EXECUTION_CONFIRMED
        || p == LzsPhase.REVERSAL_CONFIRMED
        || p == LzsPhase.PROTECTED
        || p == LzsPhase.SIGNAL_READY;
  }

  private void latchActiveLifecycle(LzsSideState state, LzsSnapshot snap) {
    state.activeLifecycleLocked = true;
    state.activeZoneSignature = state.candidate == null ? "" : state.candidate.signature();
    state.interaction.valid = true;
    state.interaction.phase = LzsPhase.ARMED;
    if (state.interaction.armedTime == Long.MIN_VALUE) {
      state.interaction.armedTime = snap.time;
      state.interaction.armedPrice = snap.lastPrice;
    }
    state.interaction.updatesSinceArm = 0;
    state.interaction.originalZoneTotalSize = state.candidate == null ? 0.0 : state.candidate.zoneTotalSize;
    state.interaction.minObservedZoneTotalSize = state.interaction.originalZoneTotalSize;
    state.interaction.debug = buildCandidateDebug(state.candidate);
  }

  private void fail(LzsSideState state, String reason, String debug) {
    state.activeLifecycleLocked = false;
    state.interaction.phase = LzsPhase.FAILED;
    state.interaction.reason = reason == null ? "" : reason;
    state.interaction.debug = debug == null ? "LZS fail" : debug;
    state.lastFailureReason = state.interaction.reason;
  }

  private void updateDistanceFromPrice(LzsZoneCandidate cand, double lastPrice, double tickSize) {
    if (cand == null || Double.isNaN(lastPrice)) return;
    double tick = Math.max(1e-9, tickSize);
    cand.distanceFromPriceTicks = cand.side == LzsSide.SHORT
        ? (cand.zoneLow - lastPrice) / tick
        : (lastPrice - cand.zoneHigh) / tick;
  }

  private void populateExecutionFields(LzsSideState state, LzsExecutionStats stats) {
    state.interaction.execSameSideVol = stats.sameSideExecVol;
    state.interaction.execTotalVol = stats.totalExecVol;
    state.interaction.bubbleCount = stats.bubbleCount;
    state.interaction.aggressionShare = stats.aggressionShare;
    state.interaction.execBandLow = stats.execBandLow;
    state.interaction.execBandHigh = stats.execBandHigh;
  }

  private LzsZoneCandidate findCandidate(LzsSide side, LzsSnapshot snap, LzsConfig cfg) {
    List<LzsRow> rows = side == LzsSide.SHORT ? snap.askRowsNear : snap.bidRowsNear;
    if (rows == null || rows.isEmpty()) return null;

    double last = snap.lastPrice;
    double tick = Math.max(1e-9, snap.tickSize);

    LzsRow anchor = null;
    int anchorIdx = -1;
    for (int i = 0; i < rows.size(); i++) {
      LzsRow r = rows.get(i);
      if (r == null || r.size < Math.max(1.0, cfg.zoneMinSize)) continue;

      double distTicks;
      if (side == LzsSide.SHORT) {
        if (r.price < last) continue;
        distTicks = (r.price - last) / tick;
      }
      else {
        if (r.price > last) continue;
        distTicks = (last - r.price) / tick;
      }
      if (distTicks > Math.max(0, cfg.zoneMaxDistanceTicks)) continue;

      if (anchor == null || r.size > anchor.size) {
        anchor = r;
        anchorIdx = i;
      }
    }
    if (anchor == null) return null;

    double rowThreshold = Math.max(cfg.zoneMinSize, anchor.size * Math.max(0.0, cfg.zoneMinRowPctOfAnchor));
    List<LzsRow> zoneRows = new ArrayList<LzsRow>();
    zoneRows.add(anchor);

    int gaps = 0;
    for (int i = anchorIdx - 1; i >= 0; i--) {
      LzsRow r = rows.get(i);
      if (r == null) continue;
      if (r.size >= rowThreshold) {
        zoneRows.add(r);
        gaps = 0;
      }
      else if (++gaps > cfg.zoneMaxGapRows) {
        break;
      }
    }

    gaps = 0;
    for (int i = anchorIdx + 1; i < rows.size(); i++) {
      LzsRow r = rows.get(i);
      if (r == null) continue;
      if (r.size >= rowThreshold) {
        zoneRows.add(r);
        gaps = 0;
      }
      else if (++gaps > cfg.zoneMaxGapRows) {
        break;
      }
    }

    if (zoneRows.size() < Math.max(1, cfg.zoneMinContiguousRows)) return null;

    double low = Double.POSITIVE_INFINITY;
    double high = Double.NEGATIVE_INFINITY;
    double total = 0.0;
    for (LzsRow r : zoneRows) {
      low = Math.min(low, r.price);
      high = Math.max(high, r.price);
      total += r.size;
    }
    double heightTicks = (high - low) / tick;
    if (heightTicks > Math.max(1, cfg.zoneMaxHeightTicks)) return null;

    LzsZoneCandidate c = new LzsZoneCandidate();
    c.side = side;
    c.valid = true;
    c.zoneLow = low;
    c.zoneHigh = high;
    c.zoneMid = 0.5 * (low + high);
    c.anchorPrice = anchor.price;
    c.anchorSize = anchor.size;
    c.zoneTotalSize = total;
    c.persistenceUpdates = 1;
    c.rowCount = zoneRows.size();
    c.zoneHeightTicks = heightTicks;
    c.distanceFromPriceTicks = side == LzsSide.SHORT ? (c.zoneLow - last) / tick : (last - c.zoneHigh) / tick;
    return c;
  }

  private boolean isArmed(LzsZoneCandidate cand, double lastPrice, double tickSize, int armTicks) {
    if (cand == null || Double.isNaN(lastPrice)) return false;
    double tick = Math.max(1e-9, tickSize);
    if (cand.side == LzsSide.SHORT) {
      return (cand.zoneLow - lastPrice) <= (Math.max(0, armTicks) * tick);
    }
    return (lastPrice - cand.zoneHigh) <= (Math.max(0, armTicks) * tick);
  }

  private boolean isTouched(LzsZoneCandidate cand, double barHigh, double barLow) {
    if (cand == null || Double.isNaN(barHigh) || Double.isNaN(barLow)) return false;
    return barHigh >= cand.zoneLow - 1e-9 && barLow <= cand.zoneHigh + 1e-9;
  }

  private LzsExecutionStats computeExecutionStats(LzsZoneCandidate cand, LzsSnapshot snap, LzsConfig cfg) {
    LzsExecutionStats out = new LzsExecutionStats();
    if (cand == null || snap == null || snap.execRows.isEmpty()) return out;

    double tick = Math.max(1e-9, snap.tickSize);
    double prox = Math.max(0, cfg.executionProximityTicks) * tick;

    double bandLow = cand.zoneLow;
    double bandHigh = cand.zoneHigh;
    if (cand.side == LzsSide.SHORT) {
      bandLow = cand.zoneLow - prox;
    }
    else {
      bandHigh = cand.zoneHigh + prox;
    }
    out.execBandLow = bandLow;
    out.execBandHigh = bandHigh;

    for (LzsExecRow row : snap.execRows) {
      if (row == null) continue;
      if (row.price < bandLow - 1e-9 || row.price > bandHigh + 1e-9) continue;

      double sameSide = cand.side == LzsSide.SHORT ? row.askVol : row.bidVol;
      out.sameSideExecVol += sameSide;
      out.totalExecVol += row.totalVol;
      out.weightedExecPriceSum += sameSide * row.price;
      if (sameSide >= Math.max(1.0, cfg.minBubbleSize)) out.bubbleCount++;
    }
    out.aggressionShare = out.totalExecVol > 0.0 ? out.sameSideExecVol / out.totalExecVol : 0.0;
    out.referencePrice = out.sameSideExecVol > 0.0 ? out.weightedExecPriceSum / out.sameSideExecVol : Double.NaN;
    return out;
  }

  private double computeReversalTicksFromExec(LzsSideState state, LzsSnapshot snap) {
    if (state == null || snap == null || Double.isNaN(snap.lastPrice)) return 0.0;
    double ref = state.interaction.reversalRefPrice;
    if (Double.isNaN(ref)) ref = state.interaction.execConfirmPrice;
    if (Double.isNaN(ref)) return 0.0;
    double tick = Math.max(1e-9, snap.tickSize);
    if (state.side == LzsSide.SHORT) {
      return Math.max(0.0, (ref - snap.lastPrice) / tick);
    }
    return Math.max(0.0, (snap.lastPrice - ref) / tick);
  }

  private boolean evaluateProtection(LzsSideState state, LzsSnapshot snap, LzsConfig cfg) {
    double currentZoneSize = computeObservableZoneSize(state, snap);
    if (Double.isNaN(currentZoneSize)) {
      state.interaction.zoneObservable = false;
      state.interaction.debug = buildProtectionDeferredDebug(state);
      return false;
    }

    state.interaction.zoneObservable = true;
    double original = Math.max(1e-9, state.interaction.originalZoneTotalSize);
    state.interaction.minObservedZoneTotalSize = Double.isNaN(state.interaction.minObservedZoneTotalSize)
        ? currentZoneSize
        : Math.min(state.interaction.minObservedZoneTotalSize, currentZoneSize);
    state.interaction.remainingZonePct = currentZoneSize / original;
    state.interaction.reloadPct = Math.max(0.0,
        currentZoneSize - state.interaction.minObservedZoneTotalSize) / original;

    if (cfg.rejectIfConsumed && currentZoneSize <= Math.max(1.0, original * 0.01)) {
      fail(state, "consumed", "LZS fail | zone consumed");
      return false;
    }

    boolean remainingOk = state.interaction.remainingZonePct >= Math.max(0.0, cfg.minRemainingZonePct);
    boolean reloadOk = !cfg.requireReload || state.interaction.reloadPct >= Math.max(0.0, cfg.minReloadPct);
    if (!remainingOk || !reloadOk) {
      state.interaction.phase = LzsPhase.REVERSAL_CONFIRMED;
      state.interaction.debug = buildProtectionPendingDebug(state);
      return false;
    }

    state.interaction.phase = LzsPhase.PROTECTED;
    state.interaction.debug = buildProtectionDebug(state);
    return true;
  }

  private double computeObservableZoneSize(LzsSideState state, LzsSnapshot snap) {
    LzsZoneCandidate cand = state.candidate;
    if (cand == null || snap == null) return Double.NaN;
    List<LzsRow> rows = cand.side == LzsSide.SHORT ? snap.askRowsNear : snap.bidRowsNear;
    if (rows == null || rows.isEmpty()) return Double.NaN;

    double minPx = Double.POSITIVE_INFINITY;
    double maxPx = Double.NEGATIVE_INFINITY;
    for (LzsRow row : rows) {
      if (row == null) continue;
      minPx = Math.min(minPx, row.price);
      maxPx = Math.max(maxPx, row.price);
    }

    if (cand.side == LzsSide.SHORT) {
      if (maxPx + 1e-9 < cand.zoneHigh) return Double.NaN;
    }
    else {
      if (minPx - 1e-9 > cand.zoneLow) return Double.NaN;
    }

    double total = 0.0;
    for (LzsRow row : rows) {
      if (row == null) continue;
      if (row.price + 1e-9 < cand.zoneLow || row.price - 1e-9 > cand.zoneHigh) continue;
      total += row.size;
    }
    return total;
  }

  private boolean evaluatePath(LzsSideState state, LzsSnapshot snap, LzsConfig cfg) {
    int lookaheadTicks = Math.max(Math.max(0, cfg.minOpenPathTicks), Math.max(0, cfg.attractionPenaltyLookaheadTicks));
    if (lookaheadTicks <= 0) {
      state.interaction.pathClearTicks = 0.0;
      state.interaction.blockedByOpposingLiquidity = false;
      return true;
    }

    List<LzsRow> rows = state.side == LzsSide.SHORT ? snap.bidRowsNear : snap.askRowsNear;
    if (rows == null || rows.isEmpty()) {
      state.interaction.pathClearTicks = lookaheadTicks;
      state.interaction.blockedByOpposingLiquidity = false;
      return true;
    }

    double tick = Math.max(1e-9, snap.tickSize);
    double blockThreshold = Math.max(0.0, cfg.maxOpposingBlockInPath);
    double pathClearTicks = lookaheadTicks;
    boolean blocked = false;

    for (LzsRow row : rows) {
      if (row == null) continue;
      double distTicks = state.side == LzsSide.SHORT
          ? (snap.lastPrice - row.price) / tick
          : (row.price - snap.lastPrice) / tick;
      if (distTicks <= 0) continue;
      if (distTicks > lookaheadTicks) continue;
      if (blockThreshold > 0.0 && row.size >= blockThreshold) {
        pathClearTicks = distTicks;
        blocked = distTicks < Math.max(1, cfg.minOpenPathTicks);
        break;
      }
    }

    state.interaction.pathClearTicks = pathClearTicks;
    state.interaction.blockedByOpposingLiquidity = blocked;
    return !blocked;
  }

  private boolean isCooldownActive(LzsSideState state, LzsSnapshot snap, LzsConfig cfg) {
    if (state == null || snap == null || cfg == null) return false;
    boolean zoneCooldown = state.activeZoneSignature != null
        && state.activeZoneSignature.equals(state.lastEmittedZoneSig)
        && state.lastEmittedTime != Long.MIN_VALUE
        && cfg.minMsBetweenSameZoneSignals > 0
        && (snap.time - state.lastEmittedTime) < cfg.minMsBetweenSameZoneSignals;

    boolean sideCooldown = state.lastEmittedBarIndex >= 0
        && cfg.minBarsBetweenSameSideSignals > 0
        && snap.barIndex >= 0
        && (snap.barIndex - state.lastEmittedBarIndex) < cfg.minBarsBetweenSameSideSignals;

    return zoneCooldown || sideCooldown;
  }

  private String buildCandidateDebug(LzsZoneCandidate cand) {
    return String.format(java.util.Locale.US,
        "LZS cand | %s %.2f-%.2f | A %.2f | Z %.0f | D %.1ft",
        cand.side == LzsSide.SHORT ? "ASK" : "BID",
        cand.zoneLow, cand.zoneHigh, cand.anchorPrice, cand.zoneTotalSize, cand.distanceFromPriceTicks);
  }

  private String buildExecPendingDebug(LzsSideState state) {
    return String.format(java.util.Locale.US,
        "LZS %s | ArmUpd %d | Exec %.0f | Bub %d | Agg %.2f",
        state.interaction.phase,
        state.interaction.updatesSinceArm,
        state.interaction.execSameSideVol,
        state.interaction.bubbleCount,
        state.interaction.aggressionShare);
  }

  private String buildExecDebug(LzsSideState state) {
    return String.format(java.util.Locale.US,
        "LZS exec ok | Ref %.2f | Exec %.0f | Bub %d | Agg %.2f",
        state.interaction.execConfirmPrice,
        state.interaction.execSameSideVol,
        state.interaction.bubbleCount,
        state.interaction.aggressionShare);
  }

  private String buildReversalPendingDebug(LzsSideState state) {
    return String.format(java.util.Locale.US,
        "LZS exec confirmed | Ref %.2f | Rev %.1ft | ExecUpd %d",
        state.interaction.reversalRefPrice,
        state.interaction.reversalTicks,
        state.interaction.updatesSinceExec);
  }

  private String buildReversalDebug(LzsSideState state) {
    return String.format(java.util.Locale.US,
        "LZS reversal ok | Ref %.2f | Rev %.1ft | Max %.1ft",
        state.interaction.reversalRefPrice,
        state.interaction.reversalTicks,
        state.interaction.maxFavorableTicksFromExec);
  }

  private String buildProtectionDeferredDebug(LzsSideState state) {
    return String.format(java.util.Locale.US,
        "LZS reversal ok | zone off-screen | Rev %.1ft",
        state.interaction.reversalTicks);
  }

  private String buildProtectionPendingDebug(LzsSideState state) {
    return String.format(java.util.Locale.US,
        "LZS wait protect | Rem %.2f | Rel %.2f",
        state.interaction.remainingZonePct,
        state.interaction.reloadPct);
  }

  private String buildProtectionDebug(LzsSideState state) {
    return String.format(java.util.Locale.US,
        "LZS protected | Rem %.2f | Rel %.2f",
        state.interaction.remainingZonePct,
        state.interaction.reloadPct);
  }

  private String buildPathBlockedDebug(LzsSideState state) {
    return String.format(java.util.Locale.US,
        "LZS path blocked | Path %.1ft",
        state.interaction.pathClearTicks);
  }

  private String buildCooldownDebug(LzsSideState state) {
    return String.format(java.util.Locale.US,
        "LZS cooldown | Zone %s | Bar %d",
        state.lastEmittedZoneSig,
        state.lastEmittedBarIndex);
  }

  private String buildSignalReadyDebug(LzsSideState state) {
    return String.format(java.util.Locale.US,
        "LZS fire | Ref %.2f | Rev %.1ft | Path %.1ft",
        state.interaction.reversalRefPrice,
        state.interaction.reversalTicks,
        state.interaction.pathClearTicks);
  }
}
