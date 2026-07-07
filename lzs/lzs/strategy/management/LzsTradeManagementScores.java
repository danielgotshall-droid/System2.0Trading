package study_examples.lzs.strategy.management;

/** Composite management scores and current management state. */
public class LzsTradeManagementScores {
  public LzsTradeManagementState state = LzsTradeManagementState.MGMT_INACTIVE;
  public double css = Double.NaN;
  public double exs = Double.NaN;
  public double ers = Double.NaN;
  public String reasons = "";

  public static LzsTradeManagementScores inactive() {
    return new LzsTradeManagementScores();
  }
}
