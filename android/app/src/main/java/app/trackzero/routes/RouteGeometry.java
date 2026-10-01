package app.trackzero.routes;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.trackzero.geo.DouglasPeucker;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * A route prepared for display: map line, elevation profile and bounds. Built once at import and cached next to the
 * GPX (a few hundred KB for the largest tracks), so opening a route never parses its GPX again.
 */
public final class RouteGeometry
{
  /** Bump when the format or the simplification changes; older caches are rebuilt from the GPX. */
  private static final int FORMAT = 0x545A4701; // "TZG", version 1

  /** Line simplification for drawing: invisible at riding zooms, cuts big tracks to a fraction of the points. */
  private static final double LINE_EPSILON_M = 2;
  private static final int MAX_PROFILE_POINTS = 600;

  @NonNull
  public final double[] lineLat;
  @NonNull
  public final double[] lineLon;
  /** Cumulative distance (m) and smoothed elevation (m); null when the GPX has no elevation. */
  @Nullable
  public final float[] profileDist;
  @Nullable
  public final float[] profileAlt;
  public final double minLat;
  public final double minLon;
  public final double maxLat;
  public final double maxLon;

  private RouteGeometry(@NonNull GpxTrack track)
  {
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

    final double[] smoothed = RouteStats.smoothedElevation(track.ele);
    if (smoothed == null)
    {
      profileDist = null;
      profileAlt = null;
      return;
    }
    final double[] cum = RouteStats.cumulativeDistance(track.lat, track.lon);
    final int n = cum.length;
    final int m = Math.min(n, MAX_PROFILE_POINTS);
    profileDist = new float[m];
    profileAlt = new float[m];
    for (int i = 0; i < m; i++)
    {
      final int src = m == 1 ? 0 : (int) Math.round((double) i * (n - 1) / (m - 1));
      profileDist[i] = (float) cum[src];
      profileAlt[i] = (float) smoothed[src];
    }
  }

  private RouteGeometry(@NonNull double[] lineLat, @NonNull double[] lineLon, @Nullable float[] profileDist,
                        @Nullable float[] profileAlt, double minLat, double minLon, double maxLat, double maxLon)
  {
    this.lineLat = lineLat;
    this.lineLon = lineLon;
    this.profileDist = profileDist;
    this.profileAlt = profileAlt;
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
  }

  /** Null when the data was written in another format version. */
  @Nullable
  static RouteGeometry read(@NonNull DataInputStream in) throws IOException
  {
    if (in.readInt() != FORMAT)
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
    return new RouteGeometry(lat, lon, dist, alt, minLat, minLon, maxLat, maxLon);
  }
}
