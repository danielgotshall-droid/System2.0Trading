package study_examples.lzs.strategy.management;

/** Concrete management action decision for the current update. */
public class LzsTradeManagementDecision {
  public LzsTradeManagementState state = LzsTradeManagementState.MGMT_INACTIVE;
  public LzsTradeManagementAction action = LzsTradeManagementAction.NONE;
  public double targetStopPrice = Double.NaN;
  public String reason = "";

  public static LzsTradeManagementDecision none(String reason) {
    LzsTradeManagementDecision d = new LzsTradeManagementDecision();
    d.reason = reason == null ? "" : reason;
    return d;
  }
}
