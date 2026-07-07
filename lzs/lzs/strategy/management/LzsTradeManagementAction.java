package study_examples.lzs.strategy.management;

/** Actions the strategy may take from management-state policy. */
public enum LzsTradeManagementAction {
  NONE,
  KEEP_STOP,
  TIGHTEN_STOP,
  EXIT_MARKET
}
