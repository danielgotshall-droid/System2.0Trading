package study_examples.lzs.model;

public final class LzsExecRow {
  public double price;
  public double bidVol;
  public double askVol;
  public double totalVol;

  public LzsExecRow() {}

  public LzsExecRow(double price, double bidVol, double askVol) {
    this.price = price;
    this.bidVol = bidVol;
    this.askVol = askVol;
    this.totalVol = bidVol + askVol;
  }
}
