package app.trackzero.routes;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GpxTrack
{
  @Nullable public final String name;
  public final double[] lat, lon, ele;
  public final long[] timeMs;
  public final List<Waypoint> waypoints;

  GpxTrack(@Nullable String name, double[] lat, double[] lon, double[] ele,
           long[] timeMs, List<Waypoint> waypoints)
  {
    this.name = name;
    this.lat = lat.clone();
    this.lon = lon.clone();
    this.ele = ele.clone();
    this.timeMs = timeMs.clone();
    this.waypoints = Collections.unmodifiableList(new ArrayList<>(waypoints));
  }

  public int size()
  {
    return lat.length;
  }

  public static final class Waypoint
  {
    public final double lat, lon;
    public final String name;

    public Waypoint(double lat, double lon, String name)
    {
      this.lat = lat;
      this.lon = lon;
      this.name = name;
    }
  }
}
