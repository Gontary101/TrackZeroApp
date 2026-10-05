package app.trackzero.routes;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.downloader.MapManager;
import app.trackzero.TzIo;
import app.trackzero.base.TzRoads;
import app.trackzero.follow.FollowRoute;

/**
 * The road surfaces along a line (FEAT-006, Phase 23), read from the downloaded maps: a sample every
 * {@link SurfaceProfile#step} metres. A route of the library keeps them in its geometry cache
 * ({@link RouteStore#loadSurfaces}); a routed path is sampled when shown.
 */
public final class RouteSurfaces
{
  private RouteSurfaces() {}

  /**
   * Which maps are on the phone, to know when samples without a map may have one now. Main thread only (CoMaps'
   * storage checks it), once the maps are loaded.
   */
  @MainThread
  static long mapsStamp()
  {
    return Framework.getDataVersion().getTime() + MapManager.nativeGetDownloadedCount();
  }

  /** The surface codes along the line, one every {@code stepM}; blocking, once the maps are loaded. */
  @WorkerThread
  @NonNull
  static int[] sample(@NonNull FollowRoute line, double stepM)
  {
    final int n = SurfaceProfile.samples(line.totalM(), stepM);
    final double[] lat = new double[n];
    final double[] lon = new double[n];
    for (int i = 0; i < n; i++)
    {
      final double at = SurfaceProfile.at(i, line.totalM(), stepM);
      lat[i] = line.latAt(at);
      lon[i] = line.lonAt(at);
    }
    return TzRoads.surfaces(lat, lon);
  }

  /** The surfaces of a routed path (not cached: a path is worked out again each time); null before the maps load. */
  public static void forPath(@NonNull Context context, @NonNull FollowRoute line,
                             @NonNull TzIo.Callback<SurfaceProfile> callback)
  {
    if (!TzRoads.ready(context))
    {
      callback.onResult(null);
      return;
    }
    TzIo.submit(() -> {
      final double step = SurfaceProfile.step(line.totalM());
      return SurfaceProfile.of(sample(line, step), line.totalM(), step);
    }, callback);
  }

  /** {@link SurfaceProfile} of cached codes. */
  @Nullable
  static SurfaceProfile of(@NonNull RouteGeometry g, double lengthM)
  {
    final int[] codes = g.surfaceCodes();
    return codes == null ? null : SurfaceProfile.of(codes, lengthM, g.surfaceStepM);
  }
}
