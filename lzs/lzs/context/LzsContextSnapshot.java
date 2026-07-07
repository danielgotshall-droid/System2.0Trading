package study_examples.lzs.context;

public final class LzsContextSnapshot {
  public long sessionStartTime = Long.MIN_VALUE;
  public long barTime = Long.MIN_VALUE;
  public boolean inRthSession;

  public double tickSize = Double.NaN;
  public double lastPrice = Double.NaN;
  public double sessionHigh = Double.NaN;
  public double sessionLow = Double.NaN;
  public double sessionRangePct = Double.NaN;

  public final LzsReferenceValue sessionOpen = new LzsReferenceValue("OPEN");
  public final LzsReferenceValue priorDayHigh = new LzsReferenceValue("PDH");
  public final LzsReferenceValue priorDayLow = new LzsReferenceValue("PDL");
  public final LzsReferenceValue priorDayClose = new LzsReferenceValue("PDC");
  public final LzsReferenceValue overnightHigh = new LzsReferenceValue("ONH");
  public final LzsReferenceValue overnightLow = new LzsReferenceValue("ONL");
  public final LzsReferenceValue sessionVwap = new LzsReferenceValue("VWAP");
  public final LzsReferenceValue openingRangeHigh = new LzsReferenceValue("ORH");
  public final LzsReferenceValue openingRangeLow = new LzsReferenceValue("ORL");
  public final LzsReferenceValue ibHigh = new LzsReferenceValue("IBH");
  public final LzsReferenceValue ibLow = new LzsReferenceValue("IBL");
  public final LzsReferenceValue priorValueAreaHigh = new LzsReferenceValue("VAH");
  public final LzsReferenceValue priorValueAreaLow = new LzsReferenceValue("VAL");
  public final LzsReferenceValue priorPoc = new LzsReferenceValue("POC");

  public boolean ibComplete;
  public boolean openingRangeComplete;

  public final LzsDayTypeContext dayType = new LzsDayTypeContext();

  public boolean hasStructuralRefs() {
    return sessionOpen.isReady() || priorDayHigh.isReady() || priorDayLow.isReady()
        || overnightHigh.isReady() || overnightLow.isReady()
        || openingRangeHigh.isReady() || openingRangeLow.isReady()
        || priorValueAreaHigh.isReady() || priorValueAreaLow.isReady() || priorPoc.isReady();
  }
}
