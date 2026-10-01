package app.trackzero.base;

import android.content.Context;
import androidx.annotation.NonNull;
import app.organicmaps.MwmApplication;
import app.organicmaps.sdk.Framework;
import app.trackzero.TzIo;

/** The road network of the downloaded maps: street names and junctions, for turn-by-turn on a GPX track. */
public final class TzRoads
{
  /** A GPX track is drawn a few metres beside the street it follows. */
  private static final double MAX_DISTANCE_M = 20;
  /** A recorded track turns a few metres from the junction node; a planned one on it. */
  private static final double JUNCTION_RADIUS_M = 12;

  private TzRoads() {}

  /**
   * Per point: 1 if roads meet there, 0 if not, -1 unknown (no map). Blocking: call off the main thread, once the
   * app has loaded its maps ({@link #ready}).
   */
  @NonNull
  public static int[] junctions(@NonNull double[] lat, @NonNull double[] lon)
  {
    return Framework.nativeRoadJunctions(lat, lon, JUNCTION_RADIUS_M);
  }

  /** The maps are loaded. A ride restored by the system can run before the app has done it. */
  public static boolean ready(@NonNull Context context)
  {
    return MwmApplication.from(context).getOrganicMaps().arePlatformAndCoreInitialized();
  }

  /**
   * The street running through all the points (along a leg of the route), delivered on the UI thread: "" when they
   * are not on one named street, without a map, or early.
   */
  public static void nameAt(@NonNull Context context, @NonNull double[] lat, @NonNull double[] lon,
                            @NonNull TzIo.Callback<String> callback)
  {
    if (!ready(context))
    {
      callback.onResult("");
      return;
    }
    TzIo.submit(() -> {
      String name = null;
      for (int i = 0; i < lat.length; i++)
      {
        final String here = Framework.nativeGetStreetName(lat[i], lon[i], MAX_DISTANCE_M);
        if (here.isEmpty() || (name != null && !name.equals(here)))
          return "";
        name = here;
      }
      return name == null ? "" : name;
    }, callback);
  }
}
