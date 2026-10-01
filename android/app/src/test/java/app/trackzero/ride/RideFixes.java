package app.trackzero.ride;

/** Synthetic fixes for ride tests: a rider going due north from 45°N. */
final class RideFixes
{
  static final double LAT0 = 45.0;
  static final double LON0 = 3.0;
  /** Metres per degree of latitude for Geo's spherical earth. */
  static final double M_PER_DEG = 6_371_000.0 * Math.PI / 180;

  private RideFixes() {}

  /** A fix {@code northM} metres north of the origin at second {@code t}; speedKmh < 0 means "no speed". */
  static TzFix at(double t, double northM, double altM, double speedKmh)
  {
    final TzFix f = new TzFix();
    f.lat = LAT0 + northM / M_PER_DEG;
    f.lon = LON0;
    f.altM = altM;
    f.hasAlt = !Double.isNaN(altM);
    f.hasSpeed = speedKmh >= 0;
    f.speedMps = (float) (Math.max(0, speedKmh) / 3.6);
    f.accuracyM = 5;
    f.elapsedMs = Math.round(t * 1000);
    f.timeMs = 1_780_000_000_000L + f.elapsedMs;
    return f;
  }
}
