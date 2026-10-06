package app.trackzero.base;

import android.content.Context;
import androidx.annotation.NonNull;
import app.organicmaps.sdk.Framework;
import app.trackzero.TzIo;
import app.trackzero.follow.FollowRoute;
import app.trackzero.follow.Stop;
import java.util.ArrayList;
import java.util.List;

/**
 * Places from the downloaded maps for S1 (FEAT-007, Phase 30): water and bicycle repair near a stretch of route or
 * around the rider. Read off the main thread, only when asked; nothing runs in the background.
 */
public final class TzPois
{
  private TzPois() {}

  /**
   * The places of {@code category} ({@code Stops.WATER}, {@code Stops.REPAIR}, {@code Stops.BIVOUAC}) within
   * {@code radiusM} of the line.
   */
  public static void along(@NonNull Context c, @NonNull double[] lat, @NonNull double[] lon, double radiusM,
                           int category, @NonNull TzIo.Callback<List<Stop>> callback)
  {
    if (!TzRoads.ready(c) || lat.length == 0)
    {
      callback.onResult(new ArrayList<>());
      return;
    }
    TzIo.submit(() -> read(Framework.nativePoisAlong(lat, lon, radiusM, category)), callback);
  }

  /** The part of {@code route} between two marks, as points (the ends included). */
  @NonNull
  public static double[][] slice(@NonNull FollowRoute route, double fromAlong, double toAlong)
  {
    final double from = Math.max(0, fromAlong);
    final double to = Math.min(route.totalM(), toAlong);
    final List<double[]> pts = new ArrayList<>();
    pts.add(new double[] {route.latAt(from), route.lonAt(from)});
    for (int i = 0; i < route.size(); i++)
    {
      final double d = route.distanceAt(i);
      if (d > from && d < to)
        pts.add(new double[] {route.lats()[i], route.lons()[i]});
    }
    pts.add(new double[] {route.latAt(to), route.lonAt(to)});
    final double[][] out = new double[2][pts.size()];
    for (int i = 0; i < pts.size(); i++)
    {
      out[0][i] = pts.get(i)[0];
      out[1][i] = pts.get(i)[1];
    }
    return out;
  }

  @NonNull
  private static List<Stop> read(@NonNull Object[] r)
  {
    final String[] names = (String[]) r[0];
    final double[] lats = (double[]) r[1];
    final double[] lons = (double[]) r[2];
    final int[] kinds = (int[]) r[3];
    final String[] hours = (String[]) r[4];
    final int[] flags = (int[]) r[5];
    final List<Stop> out = new ArrayList<>(kinds.length);
    for (int i = 0; i < kinds.length; i++)
      out.add(new Stop(names[i], kinds[i], lats[i], lons[i], hours[i], flags[i]));
    return out;
  }
}
