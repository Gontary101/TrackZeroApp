package app.trackzero.base;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.sdk.Framework;

/**
 * The route line on the map: drawn by the engine's route renderer (mint line, glow, start ring), not imported as a
 * bookmark, so nothing is persisted and nothing appears in base-app lists.
 */
public final class TzRouteLayer
{
  @Nullable
  private static int[] sLineIds;

  private TzRouteLayer() {}

  /** Replaces the shown line. Needs a running map engine. */
  public static void show(@NonNull double[] lat, @NonNull double[] lon)
  {
    hide();
    sLineIds = Framework.nativeShowTrackLine(lat, lon);
  }

  public static void hide()
  {
    if (sLineIds == null)
      return;
    Framework.nativeRemoveTrackLine(sLineIds);
    sLineIds = null;
  }

  public static boolean isShown()
  {
    return sLineIds != null;
  }
}
