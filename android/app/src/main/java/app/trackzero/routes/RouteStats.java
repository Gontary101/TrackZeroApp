package app.trackzero.routes;

import androidx.annotation.Nullable;

import app.trackzero.TzConfig;
import app.trackzero.geo.DouglasPeucker;
import app.trackzero.geo.GainCounter;
import app.trackzero.geo.Geo;

public final class RouteStats
{
  public enum Kind
  {
    LOOP, OUT_AND_BACK, POINT_TO_POINT
  }

  public final double distanceM;
  public final double gainM, highM;
  public final boolean hasElevation;
  public final Kind kind;

  private RouteStats(double distanceM, double gainM, double highM, boolean hasElevation, Kind kind)
  {
    this.distanceM = distanceM;
    this.gainM = gainM;
    this.highM = highM;
    this.hasElevation = hasElevation;
    this.kind = kind;
  }

  public static RouteStats compute(double[] lat, double[] lon, double[] ele)
  {
    if (lat.length < 2 || lat.length != lon.length || lat.length != ele.length)
    {
      throw new IllegalArgumentException("Expected equal arrays with at least two points");
    }
    double[] cumulative = cumulativeDistance(lat, lon);
    double[] smoothed = smoothedElevation(ele);
    double high = Double.NaN, ascent = Double.NaN;
    if (smoothed != null)
    {
      high = smoothed[0];
      for (double e : smoothed)
      {
        high = Math.max(high, e);
      }
      ascent = gain(smoothed, TzConfig.ELE_HYSTERESIS_M);
    }
    return new RouteStats(cumulative[cumulative.length - 1], ascent, high,
                          smoothed != null, classify(lat, lon));
  }

  public static double[] cumulativeDistance(double[] lat, double[] lon)
  {
    if (lat.length != lon.length)
    {
      throw new IllegalArgumentException("Latitude and longitude lengths differ");
    }
    double[] cumulative = new double[lat.length];
    for (int i = 1; i < lat.length; i++)
    {
      cumulative[i] = cumulative[i - 1] + Geo.distance(lat[i - 1], lon[i - 1], lat[i], lon[i]);
    }
    return cumulative;
  }

  @Nullable
  public static double[] smoothedElevation(double[] ele)
  {
    int count = 0, first = -1;
    for (int i = 0; i < ele.length; i++)
    {
      if (!Double.isNaN(ele[i]))
      {
        count++;
        if (first < 0)
        {
          first = i;
        }
      }
    }
    if (count == 0 || count < ele.length - count)
    {
      return null;
    }
    double[] filled = ele.clone();
    for (int i = 0; i < first; i++)
    {
      filled[i] = ele[first];
    }
    int previous = first;
    for (int next = first + 1; next < ele.length; next++)
    {
      if (!Double.isNaN(ele[next]))
      {
        for (int i = previous + 1; i < next; i++)
        {
          double fraction = (double) (i - previous) / (next - previous);
          filled[i] = ele[previous] + fraction * (ele[next] - ele[previous]);
        }
        previous = next;
      }
    }
    for (int i = previous + 1; i < ele.length; i++)
    {
      filled[i] = ele[previous];
    }
    double[] smoothed = new double[ele.length];
    for (int i = 0; i < ele.length; i++)
    {
      int start = Math.max(0, i - 2), end = Math.min(ele.length - 1, i + 2);
      double sum = 0;
      for (int j = start; j <= end; j++)
      {
        sum += filled[j];
      }
      smoothed[i] = sum / (end - start + 1);
    }
    return smoothed;
  }

  static double gain(double[] smoothed, double hysteresisM)
  {
    final GainCounter counter = new GainCounter(hysteresisM);
    for (double e : smoothed)
    {
      counter.add(e);
    }
    return counter.gainM();
  }

  static Kind classify(double[] lat, double[] lon)
  {
    int n = lat.length, half = n / 2;
    if (Geo.distance(lat[0], lon[0], lat[n - 1], lon[n - 1]) >= 200)
    {
      return Kind.POINT_TO_POINT;
    }
    double scale = Math.cos(Math.toRadians(lat[0])) * 111320;
    double[] x = new double[half + 1], y = new double[half + 1];
    for (int i = 0; i <= half; i++)
    {
      x[i] = (lon[i] - lon[0]) * scale;
      y[i] = (lat[i] - lat[0]) * 110540;
    }
    // Zero tolerance removes collinear vertices without changing the polyline.
    boolean[] keep = DouglasPeucker.simplify(x, y, 0);
    int matches = 0;
    for (int sample = 0; sample < 20; sample++)
    {
      int index = half + (int) Math.round(sample * (n - 1 - half) / 19.0);
      double px = (lon[index] - lon[0]) * scale;
      double py = (lat[index] - lat[0]) * 110540;
      int previous = 0;
      for (int j = 1; j <= half; j++)
      {
        if (!keep[j])
        {
          continue;
        }
        double dx = x[j] - x[previous], dy = y[j] - y[previous];
        double lengthSquared = dx * dx + dy * dy;
        double t = lengthSquared == 0 ? 0 : Math.max(0, Math.min(1,
            ((px - x[previous]) * dx + (py - y[previous]) * dy) / lengthSquared));
        double distance = Math.hypot(px - x[previous] - t * dx, py - y[previous] - t * dy);
        if (distance <= 30)
        {
          matches++;
          break;
        }
        previous = j;
      }
    }
    return matches >= 16 ? Kind.OUT_AND_BACK : Kind.LOOP;
  }
}
