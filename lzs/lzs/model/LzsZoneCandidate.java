package study_examples.lzs.model;

public final class LzsZoneCandidate {
  public LzsSide side;
  public boolean valid;

  public double zoneLow = Double.NaN;
  public double zoneHigh = Double.NaN;
  public double zoneMid = Double.NaN;
  public double anchorPrice = Double.NaN;
  public double anchorSize = 0.0;
  public double zoneTotalSize = 0.0;
  public int persistenceUpdates = 0;
  public int rowCount = 0;
  public double zoneHeightTicks = Double.NaN;
  public double distanceFromPriceTicks = Double.NaN;

  public boolean nearWatchedLevel = false;
  public String watchedLevelKey = "";
  public double watchedLevelPrice = Double.NaN;

  public double attractionPenalty = 0.0;

  public LzsZoneCandidate copy() {
    LzsZoneCandidate c = new LzsZoneCandidate();
    c.side = side;
    c.valid = valid;
    c.zoneLow = zoneLow;
    c.zoneHigh = zoneHigh;
    c.zoneMid = zoneMid;
    c.anchorPrice = anchorPrice;
    c.anchorSize = anchorSize;
    c.zoneTotalSize = zoneTotalSize;
    c.persistenceUpdates = persistenceUpdates;
    c.rowCount = rowCount;
    c.zoneHeightTicks = zoneHeightTicks;
    c.distanceFromPriceTicks = distanceFromPriceTicks;
    c.nearWatchedLevel = nearWatchedLevel;
    c.watchedLevelKey = watchedLevelKey;
    c.watchedLevelPrice = watchedLevelPrice;
    c.attractionPenalty = attractionPenalty;
    return c;
  }

  public String signature() {
    return String.format(java.util.Locale.US,
        "%s|%.4f|%.4f|%.4f",
        side == null ? "NA" : side.name(),
        zoneLow,
        zoneHigh,
        anchorPrice);
  }
}
