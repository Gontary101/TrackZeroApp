package app.trackzero.ride;

import androidx.annotation.NonNull;

import app.trackzero.geo.Geo;

/** Replays a recorded polyline at a constant speed. */
public final class ReplayPath
{
  private final double[] lat;
  private final double[] lon;
  private final double[] ele;
  private final double[] cumulativeM;
  private final double speedMps;
  private final int endSegment;
  private int cursor;
  private long lastElapsedMs = Long.MIN_VALUE;

  public ReplayPath(@NonNull double[] lat, @NonNull double[] lon,
      @NonNull double[] ele, double speedMps)
  {
    if (lat.length < 2 || lat.length != lon.length || lat.length != ele.length)
    {
      throw new IllegalArgumentException("Coordinate arrays must have equal length >= 2");
    }
    if (Double.isNaN(speedMps) || Double.isInfinite(speedMps) || speedMps < 0)
    {
      throw new IllegalArgumentException("Speed must be finite and non-negative");
    }
    this.lat = lat.clone();
    this.lon = lon.clone();
    this.ele = ele.clone();
    this.speedMps = speedMps;
    cumulativeM = new double[lat.length];
    int lastNonZero = 0;
    for (int i = 1; i < lat.length; i++)
    {
      double distance = Geo.distance(this.lat[i - 1], this.lon[i - 1],
          this.lat[i], this.lon[i]);
      cumulativeM[i] = cumulativeM[i - 1] + distance;
      if (distance > 0)
      {
        lastNonZero = i - 1;
      }
    }
    endSegment = lastNonZero;
  }

  public double lengthM()
  {
    return cumulativeM[cumulativeM.length - 1];
  }

  public boolean isFinished(long elapsedMs)
  {
    return distanceAt(elapsedMs) >= lengthM();
  }

  /** Returns the interpolated fix at the elapsed replay time with the supplied timestamp. */
  @NonNull
  public TzFix fixAt(long elapsedMs, long timeMs)
  {
    double distance = distanceAt(elapsedMs);
    boolean finished = distance >= lengthM();
    int start;
    int end;
    double fraction;
    int bearingSegment;
    if (finished)
    {
      start = lat.length - 2;
      end = lat.length - 1;
      fraction = 1;
      bearingSegment = endSegment;
    }
    else
    {
      if (elapsedMs < lastElapsedMs)
      {
        int low = 0;
        int high = cumulativeM.length - 1;
        while (low < high)
        {
          int middle = low + (high - low) / 2;
          if (cumulativeM[middle] <= distance)
          {
            low = middle + 1;
          }
          else
          {
            high = middle;
          }
        }
        cursor = low - 1;
      }
      else
      {
        while (cursor < lat.length - 2 && cumulativeM[cursor + 1] <= distance)
        {
          cursor++;
        }
      }
      start = cursor;
      end = start + 1;
      fraction = (distance - cumulativeM[start])
          / (cumulativeM[end] - cumulativeM[start]);
      bearingSegment = start;
    }
    lastElapsedMs = elapsedMs;

    TzFix fix = new TzFix();
    fix.lat = finished ? lat[end] : lat[start] + fraction * (lat[end] - lat[start]);
    fix.lon = finished ? lon[end] : lon[start] + fraction * (lon[end] - lon[start]);
    if (!Double.isNaN(ele[start]) && !Double.isNaN(ele[end]))
    {
      fix.altM = ele[start] + fraction * (ele[end] - ele[start]);
      fix.hasAlt = true;
    }
    else if (!Double.isNaN(ele[start]) || !Double.isNaN(ele[end]))
    {
      fix.altM = Double.isNaN(ele[start]) ? ele[end] : ele[start];
      fix.hasAlt = true;
    }
    fix.speedMps = finished ? 0 : (float) speedMps;
    fix.hasSpeed = true;
    fix.accuracyM = 5;
    fix.bearingDeg = (float) Geo.bearing(lat[bearingSegment], lon[bearingSegment],
        lat[bearingSegment + 1], lon[bearingSegment + 1]);
    fix.hasBearing = true;
    fix.timeMs = timeMs;
    return fix;
  }

  private double distanceAt(long elapsedMs)
  {
    return speedMps * (Math.max(0L, elapsedMs) / 1000.0);
  }
}
