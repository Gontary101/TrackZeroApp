package app.trackzero.geo;

public final class Geo
{
  static final double EARTH_RADIUS_M = 6371000;

  private Geo()
  {
  }

  public static double distance(double lat1, double lon1, double lat2, double lon2)
  {
    double p1 = Math.toRadians(lat1);
    double p2 = Math.toRadians(lat2);
    double dp = p2 - p1;
    double dl = Math.toRadians(lon2 - lon1);
    double a = Math.sin(dp / 2) * Math.sin(dp / 2)
        + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
    return 2 * EARTH_RADIUS_M * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }

  public static double bearing(double lat1, double lon1, double lat2, double lon2)
  {
    double p1 = Math.toRadians(lat1);
    double p2 = Math.toRadians(lat2);
    double dl = Math.toRadians(lon2 - lon1);
    double y = Math.sin(dl) * Math.cos(p2);
    double x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
    return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
  }

  public static double normalizeAngle(double deg)
  {
    double angle = deg % 360;
    if (angle <= -180)
    {
      angle += 360;
    }
    else if (angle > 180)
    {
      angle -= 360;
    }
    return angle;
  }
}
