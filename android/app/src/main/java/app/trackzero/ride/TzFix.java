package app.trackzero.ride;

/** One position sample from a {@link TzLocationSource}. Mutable plain data, created per fix. */
public final class TzFix
{
  public double lat;
  public double lon;
  /** Metres above mean sea level when the source knows it (GPX elevations are MSL too). */
  public double altM;
  public boolean hasAlt;
  public float speedMps;
  public boolean hasSpeed;
  /** Horizontal accuracy (68 %), metres; 0 when unknown. */
  public float accuracyM;
  public float bearingDeg;
  public boolean hasBearing;
  /** UTC time of the fix, for the saved track. */
  public long timeMs;
  /** Monotonic time of the fix ({@code SystemClock.elapsedRealtime} base); all durations are measured on it. */
  public long elapsedMs;
}
