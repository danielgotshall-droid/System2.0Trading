package study_examples.lzs.context;

public final class LzsReferenceValue {
  public final String label;
  public double value = Double.NaN;
  public LzsReferenceSource source = LzsReferenceSource.NONE;
  public LzsReferenceAvailability availability = LzsReferenceAvailability.UNAVAILABLE;
  public boolean active;

  public LzsReferenceValue(String label) {
    this.label = label == null ? "" : label;
  }

  public static LzsReferenceValue unavailable(String label) {
    return new LzsReferenceValue(label);
  }

  public LzsReferenceValue with(double value, LzsReferenceSource source, LzsReferenceAvailability availability, boolean active) {
    this.value = value;
    this.source = source == null ? LzsReferenceSource.NONE : source;
    this.availability = availability == null ? LzsReferenceAvailability.UNAVAILABLE : availability;
    this.active = active;
    return this;
  }


  public LzsReferenceValue with(LzsReferenceValue other) {
    if (other == null) return this;
    this.value = other.value;
    this.source = other.source;
    this.availability = other.availability;
    this.active = other.active;
    return this;
  }

  public boolean hasValue() {
    return !Double.isNaN(value);
  }

  public boolean isReady() {
    return availability == LzsReferenceAvailability.READY && hasValue();
  }

  public boolean isDeveloping() {
    return availability == LzsReferenceAvailability.DEVELOPING && hasValue();
  }

  public String sourceTag() {
    switch (source) {
      case AUTO: return "A";
      case MANUAL: return "M";
      case HYBRID_AUTO: return "HA";
      case HYBRID_MANUAL: return "HM";
      default: return "-";
    }
  }
}
