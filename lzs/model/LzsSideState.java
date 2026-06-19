package study_examples.lzs.model;

public final class LzsSideState {
  public final LzsSide side;
  public LzsZoneCandidate candidate;
  public final LzsInteraction interaction = new LzsInteraction();

  public boolean activeLifecycleLocked;
  public String activeZoneSignature = "";

  public boolean fired;
  public int lastEmittedBarIndex = -1;
  public long lastEmittedTime = Long.MIN_VALUE;
  public String lastEmittedZoneSig = "";
  public String lastFailureReason = "";

  public int lastRenderedBarIndex = -1;
  public String lastRenderSignature = "";

  public LzsSideState(LzsSide side) {
    this.side = side;
  }

  public void resetLifecycle() {
    candidate = null;
    activeLifecycleLocked = false;
    activeZoneSignature = "";
    interaction.phase = LzsPhase.IDLE;
    interaction.valid = false;
    interaction.updatesSeen = 0;
    interaction.updatesSinceArm = 0;
    interaction.updatesSinceTouch = 0;
    interaction.updatesSinceExec = 0;
    interaction.armedTime = Long.MIN_VALUE;
    interaction.touchTime = Long.MIN_VALUE;
    interaction.execConfirmTime = Long.MIN_VALUE;
    interaction.reverseTime = Long.MIN_VALUE;
    interaction.armedPrice = Double.NaN;
    interaction.touchPrice = Double.NaN;
    interaction.execConfirmPrice = Double.NaN;
    interaction.reversalRefPrice = Double.NaN;
    interaction.reversalTicks = 0.0;
    interaction.maxFavorableTicksFromExec = 0.0;
    interaction.execSameSideVol = 0.0;
    interaction.execTotalVol = 0.0;
    interaction.bubbleCount = 0;
    interaction.aggressionShare = 0.0;
    interaction.execBandLow = Double.NaN;
    interaction.execBandHigh = Double.NaN;
    interaction.originalZoneTotalSize = 0.0;
    interaction.minObservedZoneTotalSize = Double.NaN;
    interaction.zoneObservable = false;
    interaction.reloadPct = 0.0;
    interaction.remainingZonePct = 0.0;
    interaction.pathClearTicks = 0.0;
    interaction.blockedByOpposingLiquidity = false;
    interaction.score = 0.0;
    interaction.reason = "";
    interaction.debug = "";
    fired = false;
  }

  public void markEmitted(String zoneSig, long time, int barIndex) {
    fired = true;
    lastEmittedZoneSig = zoneSig == null ? "" : zoneSig;
    lastEmittedTime = time;
    lastEmittedBarIndex = barIndex;
  }
}
