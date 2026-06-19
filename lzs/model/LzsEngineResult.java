package study_examples.lzs.model;

public final class LzsEngineResult {
  public final LzsSide side;
  public final LzsSideState state;
  public final boolean emitted;

  public LzsEngineResult(LzsSide side, LzsSideState state, boolean emitted) {
    this.side = side;
    this.state = state;
    this.emitted = emitted;
  }
}
