package study_examples.lzs.model;

public final class LzsRow {
  public final double price;
  public final double size;
  public final int orderCount;

  public LzsRow(double price, double size, int orderCount) {
    this.price = price;
    this.size = size;
    this.orderCount = orderCount;
  }
}
