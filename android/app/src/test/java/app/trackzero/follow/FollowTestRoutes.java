package app.trackzero.follow;

import app.trackzero.geo.Geo;
import java.util.ArrayList;
import java.util.List;

/** Small, dense GPX-like routes expressed in metres around Clermont-Ferrand. */
final class FollowTestRoutes
{
  private static final double LATITUDE = 45.77;
  private static final double LONGITUDE = 3.08;
  private static final double SPACING_M = 5;

  private FollowTestRoutes()
  {
  }

  /** Returns {latitude, longitude} for an east/north offset in metres (on the app's sphere, {@link Geo}). */
  static double[] point(double east, double north)
  {
    return new double[] {LATITUDE + north / Geo.M_PER_DEG,
        LONGITUDE + east / (Geo.M_PER_DEG * Math.cos(Math.toRadians(LATITUDE)))};
  }

  /** Connects {east, north} corners with segments at most 5 m long, retaining every corner. */
  static FollowRoute polyline(double[]... corners)
  {
    List<double[]> points = new ArrayList<>();
    points.add(point(corners[0][0], corners[0][1]));
    for (int i = 1; i < corners.length; i++)
    {
      double[] from = corners[i - 1];
      double[] to = corners[i];
      double east = to[0] - from[0];
      double north = to[1] - from[1];
      int steps = Math.max(1, (int) Math.ceil(Math.hypot(east, north) / SPACING_M));
      for (int step = 1; step <= steps; step++)
      {
        double fraction = (double) step / steps;
        points.add(point(from[0] + fraction * east, from[1] + fraction * north));
      }
    }
    double[] lat = new double[points.size()];
    double[] lon = new double[points.size()];
    for (int i = 0; i < points.size(); i++)
    {
      lat[i] = points.get(i)[0];
      lon[i] = points.get(i)[1];
    }
    return new FollowRoute(lat, lon);
  }
}
