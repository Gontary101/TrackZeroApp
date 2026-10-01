package app.trackzero.follow;

import androidx.annotation.NonNull;
import app.trackzero.geo.Geo;

/**
 * A route line prepared for following: cumulative distance along it and a local flat projection (metres) used for
 * snapping and bearings, both on {@link Geo}'s sphere so distances off and along the line agree. Immutable.
 */
public final class FollowRoute
{
  @NonNull
  final double[] lat;
  @NonNull
  final double[] lon;
  /** Projected metres east / north of the route's centre. */
  @NonNull
  final double[] x;
  @NonNull
  final double[] y;
  /** Distance along the route at each point, metres; {@code cum[0] == 0}. */
  @NonNull
  final double[] cum;
  private final double mLat0;
  private final double mLon0;
  private final double mCosLat0;

  public FollowRoute(@NonNull double[] lat, @NonNull double[] lon)
  {
    if (lat.length < 2 || lat.length != lon.length)
      throw new IllegalArgumentException("A route needs at least 2 points");
    this.lat = lat;
    this.lon = lon;
    final int n = lat.length;
    double minLat = lat[0], maxLat = lat[0], minLon = lon[0], maxLon = lon[0];
    for (int i = 1; i < n; i++)
    {
      minLat = Math.min(minLat, lat[i]);
      maxLat = Math.max(maxLat, lat[i]);
      minLon = Math.min(minLon, lon[i]);
      maxLon = Math.max(maxLon, lon[i]);
    }
    mLat0 = (minLat + maxLat) / 2;
    mLon0 = (minLon + maxLon) / 2;
    mCosLat0 = Math.cos(Math.toRadians(mLat0));
    x = new double[n];
    y = new double[n];
    cum = new double[n];
    for (int i = 0; i < n; i++)
    {
      x[i] = projectX(lon[i]);
      y[i] = projectY(lat[i]);
      if (i > 0)
        cum[i] = cum[i - 1] + Geo.distance(lat[i - 1], lon[i - 1], lat[i], lon[i]);
    }
  }

  public int size()
  {
    return lat.length;
  }

  public double totalM()
  {
    return cum[cum.length - 1];
  }

  public double startLat()
  {
    return lat[0];
  }

  public double startLon()
  {
    return lon[0];
  }

  double projectX(double longitude)
  {
    return (longitude - mLon0) * mCosLat0 * Geo.M_PER_DEG;
  }

  double projectY(double latitude)
  {
    return (latitude - mLat0) * Geo.M_PER_DEG;
  }

  /** Index of the segment {@code [i, i + 1]} containing {@code along} (clamped to the route). */
  int segmentAt(double along)
  {
    int lo = 0;
    int hi = cum.length - 2;
    while (lo < hi)
    {
      final int mid = (lo + hi + 1) >>> 1;
      if (cum[mid] <= along)
        lo = mid;
      else
        hi = mid - 1;
    }
    return lo;
  }

  /** Fraction of segment {@code i} at {@code along}, 0…1. */
  private double fraction(int i, double along)
  {
    final double len = cum[i + 1] - cum[i];
    return len > 0 ? Math.max(0, Math.min(1, (along - cum[i]) / len)) : 0;
  }

  double xAt(double along)
  {
    final int i = segmentAt(along);
    return x[i] + fraction(i, along) * (x[i + 1] - x[i]);
  }

  double yAt(double along)
  {
    final int i = segmentAt(along);
    return y[i] + fraction(i, along) * (y[i + 1] - y[i]);
  }

  /** Latitude of the point {@code along} metres into the route. */
  public double latAt(double along)
  {
    final int i = segmentAt(along);
    return lat[i] + fraction(i, along) * (lat[i + 1] - lat[i]);
  }

  public double lonAt(double along)
  {
    final int i = segmentAt(along);
    return lon[i] + fraction(i, along) * (lon[i + 1] - lon[i]);
  }

  /** Compass bearing (degrees) from {@code fromAlong} to {@code toAlong} on the route, in the flat projection. */
  double bearing(double fromAlong, double toAlong)
  {
    return bearing(xAt(fromAlong), yAt(fromAlong), xAt(toAlong), yAt(toAlong));
  }

  static double bearing(double x1, double y1, double x2, double y2)
  {
    return (Math.toDegrees(Math.atan2(x2 - x1, y2 - y1)) + 360) % 360;
  }
}
