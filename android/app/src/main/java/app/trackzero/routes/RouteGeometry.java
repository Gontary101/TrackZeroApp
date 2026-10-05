package app.trackzero.routes;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.trackzero.geo.DouglasPeucker;
import app.trackzero.geo.Geo;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A route prepared for display and riding: map line, elevation profiles, waypoints and bounds. Built once at import
 * and cached next to the GPX (a few hundred KB for the largest tracks), so opening a route never parses its GPX again.
 * The road surfaces along it (Phase 23) are added to the cache the first time the route is previewed with maps.
 *
 * Every distance is measured along {@link #lineLat}/{@link #lineLon}, the line the rider is followed on, so a place
 * on the profile and a place on the route agree to the metre even 1000 km in.
 */
public final class RouteGeometry
{
  /**
   * Bump when the format or the simplification changes; older caches are rebuilt from the GPX, except version 2, which
   * is version 3 without the surfaces.
   */
  private static final int FORMAT = 0x545A4703; // "TZG", version 3
  private static final int FORMAT_V2 = 0x545A4702;

  /** Line simplification for drawing: invisible at riding zooms, cuts big tracks to a fraction of the points. */
  private static final double LINE_EPSILON_M = 2;
  private static final int MAX_PROFILE_POINTS = 600;
  /** Spacing of {@link #ele}: climbs are found and drawn at this resolution. */
  public static final double ELE_STEP_M = 50;
  private static final int MAX_NAME = 200;

  @NonNull
  public final double[] lineLat;
  @NonNull
  public final double[] lineLon;
  /** Up to 600 (distance, smoothed elevation) pairs for small charts; null when the GPX has no elevation. */
  @Nullable
  public final float[] profileDist;
  @Nullable
  public final float[] profileAlt;
  /** Smoothed elevation every {@link #ELE_STEP_M} from the start (the last sample at the end); null without one. */
  @Nullable
  public final float[] ele;
  @NonNull
  public final List<GpxTrack.Waypoint> waypoints;
  public final double minLat;
  public final double minLon;
  public final double maxLat;
  public final double maxLon;
  /** Road surface codes every {@link #surfaceStepM} along the line ({@code SurfaceProfile}); null until computed. */
  @Nullable
  public final byte[] surfaces;
  public final double surfaceStepM;
  /** The downloaded maps the surfaces were read from ({@code RouteSurfaces.mapsStamp}). */
  public final long surfaceMaps;

  private RouteGeometry(@NonNull GpxTrack track)
  {
    surfaces = null;
    surfaceStepM = 0;
    surfaceMaps = 0;
    final int[] keep = DouglasPeucker.simplifyLatLon(track.lat, track.lon, LINE_EPSILON_M);
    lineLat = new double[keep.length];
    lineLon = new double[keep.length];
    double minLa = 90, minLo = 180, maxLa = -90, maxLo = -180;
    for (int i = 0; i < keep.length; i++)
    {
      lineLat[i] = track.lat[keep[i]];
      lineLon[i] = track.lon[keep[i]];
      minLa = Math.min(minLa, lineLat[i]);
      maxLa = Math.max(maxLa, lineLat[i]);
      minLo = Math.min(minLo, lineLon[i]);
      maxLo = Math.max(maxLo, lineLon[i]);
    }
    minLat = minLa;
    minLon = minLo;
    maxLat = maxLa;
    maxLon = maxLo;
    waypoints = Collections.unmodifiableList(new ArrayList<>(track.waypoints));

    final double[] smoothed = RouteStats.smoothedElevation(track.ele);
    if (smoothed == null)
    {
      profileDist = null;
      profileAlt = null;
      ele = null;
      return;
    }
    final double[] along = alongLine(RouteStats.cumulativeDistance(track.lat, track.lon), keep, lineLat, lineLon);
    final int n = along.length;
    final int m = Math.min(n, MAX_PROFILE_POINTS);
    profileDist = new float[m];
    profileAlt = new float[m];
    for (int i = 0; i < m; i++)
    {
      final int src = m == 1 ? 0 : (int) Math.round((double) i * (n - 1) / (m - 1));
      profileDist[i] = (float) along[src];
      profileAlt[i] = (float) smoothed[src];
    }
    ele = resample(along, smoothed, ELE_STEP_M);
  }

  private RouteGeometry(@NonNull double[] lineLat, @NonNull double[] lineLon, @Nullable float[] profileDist,
                        @Nullable float[] profileAlt, @Nullable float[] ele,
                        @NonNull List<GpxTrack.Waypoint> waypoints, double minLat, double minLon, double maxLat,
                        double maxLon, @Nullable byte[] surfaces, double surfaceStepM, long surfaceMaps)
  {
    this.surfaces = surfaces;
    this.surfaceStepM = surfaceStepM;
    this.surfaceMaps = surfaceMaps;
    this.lineLat = lineLat;
    this.lineLon = lineLon;
    this.profileDist = profileDist;
    this.profileAlt = profileAlt;
    this.ele = ele;
    this.waypoints = waypoints;
    this.minLat = minLat;
    this.minLon = minLon;
    this.maxLat = maxLat;
    this.maxLon = maxLon;
  }

  @NonNull
  static RouteGeometry of(@NonNull GpxTrack track)
  {
    return new RouteGeometry(track);
  }

  /** This geometry with the surfaces read from {@code maps} added. */
  @NonNull
  RouteGeometry withSurfaces(@NonNull int[] codes, double stepM, long maps)
  {
    final byte[] b = new byte[codes.length];
    for (int i = 0; i < codes.length; i++)
      b[i] = (byte) codes[i];
    return new RouteGeometry(lineLat, lineLon, profileDist, profileAlt, ele, waypoints, minLat, minLon, maxLat, maxLon,
                             b, stepM, maps);
  }

  /** The surface codes as ints; null until computed. */
  @Nullable
  public int[] surfaceCodes()
  {
    if (surfaces == null)
      return null;
    final int[] codes = new int[surfaces.length];
    for (int i = 0; i < codes.length; i++)
      codes[i] = surfaces[i];
    return codes;
  }

  /**
   * Distance along the simplified line of every original point: each kept point's own, and the points between two
   * kept ones spread over that line segment in proportion to the distance they cover.
   */
  @NonNull
  static double[] alongLine(@NonNull double[] cum, @NonNull int[] keep, @NonNull double[] lineLat,
                            @NonNull double[] lineLon)
  {
    final double[] along = new double[cum.length];
    double lineAt = 0;
    for (int k = 0; k + 1 < keep.length; k++)
    {
      final int a = keep[k], b = keep[k + 1];
      final double seg = Geo.distance(lineLat[k], lineLon[k], lineLat[k + 1], lineLon[k + 1]);
      final double raw = cum[b] - cum[a];
      for (int i = a; i < b; i++)
        along[i] = lineAt + (raw > 0 ? (cum[i] - cum[a]) / raw * seg : 0);
      lineAt += seg;
    }
    along[cum.length - 1] = lineAt;
    return along;
  }

  /**
   * The climbs' elevation profile of any line (a routed path): smoothed, every {@link #ELE_STEP_M} from the start;
   * null without enough elevation.
   */
  @Nullable
  public static float[] elevationProfile(@NonNull double[] along, @NonNull double[] ele)
  {
    final double[] smoothed = RouteStats.smoothedElevation(ele);
    return smoothed == null ? null : resample(along, smoothed, ELE_STEP_M);
  }

  /** {@code y(x)} sampled every {@code step} from 0 to the last x (included), by linear interpolation. */
  @NonNull
  static float[] resample(@NonNull double[] x, @NonNull double[] y, double step)
  {
    final double end = x[x.length - 1];
    final int n = (int) Math.ceil(end / step) + 1;
    final float[] out = new float[Math.max(1, n)];
    int j = 0;
    for (int i = 0; i < out.length; i++)
    {
      final double at = Math.min(end, i * step);
      while (j < x.length - 2 && x[j + 1] < at)
        j++;
      final double span = j + 1 < x.length ? x[j + 1] - x[j] : 0;
      final double t = span > 0 ? Math.max(0, Math.min(1, (at - x[j]) / span)) : 0;
      out[i] = (float) (y[j] + t * (j + 1 < y.length ? y[j + 1] - y[j] : 0));
    }
    return out;
  }

  void write(@NonNull DataOutputStream out) throws IOException
  {
    out.writeInt(FORMAT);
    out.writeDouble(minLat);
    out.writeDouble(minLon);
    out.writeDouble(maxLat);
    out.writeDouble(maxLon);
    out.writeInt(lineLat.length);
    for (int i = 0; i < lineLat.length; i++)
    {
      out.writeDouble(lineLat[i]);
      out.writeDouble(lineLon[i]);
    }
    out.writeInt(profileDist == null ? -1 : profileDist.length);
    if (profileDist != null)
    {
      for (int i = 0; i < profileDist.length; i++)
      {
        out.writeFloat(profileDist[i]);
        out.writeFloat(profileAlt[i]);
      }
    }
    out.writeInt(ele == null ? -1 : ele.length);
    if (ele != null)
    {
      for (float e : ele)
        out.writeFloat(e);
    }
    out.writeInt(waypoints.size());
    for (GpxTrack.Waypoint w : waypoints)
    {
      out.writeDouble(w.lat);
      out.writeDouble(w.lon);
      // writeUTF takes up to 64 KB; a waypoint name is shown in a 26sp title.
      final String name = w.name == null ? "" : w.name;
      out.writeUTF(name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name);
    }
    out.writeDouble(surfaceStepM);
    out.writeLong(surfaceMaps);
    out.writeInt(surfaces == null ? -1 : surfaces.length);
    if (surfaces != null)
      out.write(surfaces);
  }

  /** Null when the data was written in another format version (version 2 is read, without surfaces). */
  @Nullable
  static RouteGeometry read(@NonNull DataInputStream in) throws IOException
  {
    final int format = in.readInt();
    if (format != FORMAT && format != FORMAT_V2)
      return null;
    final double minLat = in.readDouble();
    final double minLon = in.readDouble();
    final double maxLat = in.readDouble();
    final double maxLon = in.readDouble();
    final int n = in.readInt();
    final double[] lat = new double[n];
    final double[] lon = new double[n];
    for (int i = 0; i < n; i++)
    {
      lat[i] = in.readDouble();
      lon[i] = in.readDouble();
    }
    final int m = in.readInt();
    float[] dist = null;
    float[] alt = null;
    if (m >= 0)
    {
      dist = new float[m];
      alt = new float[m];
      for (int i = 0; i < m; i++)
      {
        dist[i] = in.readFloat();
        alt[i] = in.readFloat();
      }
    }
    final int e = in.readInt();
    float[] ele = null;
    if (e >= 0)
    {
      ele = new float[e];
      for (int i = 0; i < e; i++)
        ele[i] = in.readFloat();
    }
    final int w = in.readInt();
    final List<GpxTrack.Waypoint> waypoints = new ArrayList<>(w);
    for (int i = 0; i < w; i++)
    {
      final double wLat = in.readDouble();
      final double wLon = in.readDouble();
      waypoints.add(new GpxTrack.Waypoint(wLat, wLon, in.readUTF()));
    }
    double step = 0;
    long maps = 0;
    byte[] surfaces = null;
    if (format == FORMAT)
    {
      step = in.readDouble();
      maps = in.readLong();
      final int s = in.readInt();
      if (s >= 0)
      {
        surfaces = new byte[s];
        in.readFully(surfaces);
      }
    }
    return new RouteGeometry(lat, lon, dist, alt, ele, Collections.unmodifiableList(waypoints), minLat, minLon,
                             maxLat, maxLon, surfaces, step, maps);
  }
}
