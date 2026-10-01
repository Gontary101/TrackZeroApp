package app.trackzero.base;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.sdk.Framework;

/**
 * The route line on the map: drawn by the engine's route renderer (mint line, glow, start ring), not imported as a
 * bookmark, so nothing is persisted and nothing appears in base-app lists. A line requested before the map engine
 * exists is drawn once it does.
 */
public final class TzRouteLayer
{
  @Nullable
  private static int[] sLineIds;
  @Nullable
  private static double[] sLat;
  @Nullable
  private static double[] sLon;

  private TzRouteLayer() {}

  /** Replaces the shown line. */
  public static void show(@NonNull double[] lat, @NonNull double[] lon)
  {
    hide();
    sLat = lat;
    sLon = lon;
    draw();
  }

  public static void hide()
  {
    sLat = null;
    sLon = null;
    if (sLineIds == null)
      return;
    Framework.nativeRemoveTrackLine(sLineIds);
    sLineIds = null;
  }

  public static boolean isShown()
  {
    return sLat != null;
  }

  static void onEngineReady()
  {
    if (sLineIds == null)
      draw();
  }

  private static void draw()
  {
    if (sLat != null && sLon != null)
      sLineIds = Framework.nativeShowTrackLine(sLat, sLon);
  }
}
