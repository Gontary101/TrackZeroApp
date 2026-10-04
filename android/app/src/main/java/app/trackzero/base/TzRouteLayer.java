package app.trackzero.base;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.sdk.Framework;
import app.trackzero.TzTrace;
import java.util.Arrays;

/**
 * The route lines on the map: drawn by the engine's route renderer (mint line, glow, ring), not imported as a
 * bookmark, so nothing is persisted and nothing appears in base-app lists. The route (a track with a ring at its start,
 * or the path to a place with a ring at the place) and a leg leading onto it. Lines requested before the map engine
 * exists are drawn once it does. Each line belongs to the screen that showed it: a screen going away hides only its
 * own (the next screen may have shown its lines already: fragment transactions reorder).
 */
public final class TzRouteLayer
{
  private static final int RING_NONE = 0;
  private static final int RING_START = 1;
  private static final int RING_END = 2;

  /** One line: its points, its ring and what the engine drew for it. */
  private static final class Line
  {
    @NonNull
    final String name;

    Line(@NonNull String name)
    {
      this.name = name;
    }

    @Nullable
    double[] lat;
    @Nullable
    double[] lon;
    int ring;
    boolean dimmed;
    @Nullable
    Object owner;
    @Nullable
    int[] ids;

    void set(@Nullable Object owner, @Nullable double[] lat, @Nullable double[] lon, int ring)
    {
      remove();
      this.owner = owner;
      this.lat = lat;
      this.lon = lon;
      this.ring = ring;
      dimmed = false;
      draw();
    }

    void setDimmed(boolean d)
    {
      if (dimmed == d)
        return;
      dimmed = d;
      remove();
      draw();
    }

    void draw()
    {
      if (lat != null && lon != null && ids == null)
      {
        ids = Framework.nativeShowTrackLine(lat, lon, dimmed, ring);
        if (TzTrace.on())
          TzTrace.log("line", name + " drawn owner=" + ownerName(owner) + " points=" + lat.length + " dimmed=" + dimmed
                              + " ids=" + Arrays.toString(ids));
      }
    }

    void remove()
    {
      if (ids == null)
        return;
      if (TzTrace.on())
        TzTrace.log("line", name + " removed ids=" + Arrays.toString(ids));
      Framework.nativeRemoveTrackLine(ids);
      ids = null;
    }
  }

  private static final Line sRoute = new Line("route");
  private static final Line sLeg = new Line("leg");

  private TzRouteLayer() {}

  /** Shows a track (ring at its start), replacing the route shown. */
  public static void show(@NonNull Object owner, @NonNull double[] lat, @NonNull double[] lon)
  {
    if (TzTrace.on())
      TzTrace.log("line", "show owner=" + ownerName(owner) + " points=" + lat.length);
    sRoute.set(owner, lat, lon, RING_START);
  }

  /** Shows the path to a place (ring at the place), replacing the route shown. */
  public static void showPath(@NonNull Object owner, @NonNull double[] lat, @NonNull double[] lon)
  {
    if (TzTrace.on())
      TzTrace.log("line", "showPath owner=" + ownerName(owner) + " points=" + lat.length);
    sRoute.set(owner, lat, lon, RING_END);
  }

  /** Shows the leg leading onto the route (to its start, or back onto it). */
  public static void showLeg(@NonNull Object owner, @NonNull double[] lat, @NonNull double[] lon)
  {
    if (TzTrace.on())
      TzTrace.log("line", "showLeg owner=" + ownerName(owner) + " points=" + lat.length);
    sLeg.set(owner, lat, lon, RING_NONE);
  }

  public static void hideLeg()
  {
    sLeg.set(null, null, null, RING_NONE);
  }

  /** Off route (R3): the route dashed, at 45 %, without its glow. */
  public static void setDimmed(boolean dimmed)
  {
    if (TzTrace.on() && dimmed != sRoute.dimmed)
      TzTrace.log("line", "setDimmed " + dimmed);
    sRoute.setDimmed(dimmed);
  }

  /** Hides the lines {@code owner} showed. */
  public static void hide(@NonNull Object owner)
  {
    if (TzTrace.on())
      TzTrace.log("line", "hide owner=" + ownerName(owner) + " route=" + ownerName(sRoute.owner) + " leg="
                          + ownerName(sLeg.owner));
    if (sRoute.owner == owner)
      sRoute.set(null, null, null, RING_NONE);
    if (sLeg.owner == owner)
      sLeg.set(null, null, null, RING_NONE);
  }

  @NonNull
  private static String ownerName(@Nullable Object owner)
  {
    return owner == null ? "none" : owner.getClass().getSimpleName();
  }

  public static boolean isShown()
  {
    return sRoute.lat != null;
  }

  static void onEngineReady()
  {
    if (TzTrace.on())
      TzTrace.log("line", "onEngineReady route=" + ownerName(sRoute.owner) + " leg=" + ownerName(sLeg.owner));
    sRoute.draw();
    sLeg.draw();
  }
}
