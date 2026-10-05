package app.trackzero.base;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.sdk.Framework;
import app.trackzero.TzTrace;
import app.trackzero.geo.Geo;
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

  /** What draws the lines: the map engine (tests use a fake). */
  interface Engine
  {
    @NonNull
    int[] show(@NonNull double[] lat, @NonNull double[] lon, boolean dimmed, int ring, int style, double chevronSpacingM);

    void remove(@NonNull int[] ids);
  }

  @NonNull
  static Engine sEngine = new Engine() {
    @NonNull
    @Override
    public int[] show(@NonNull double[] lat, @NonNull double[] lon, boolean dimmed, int ring, int style,
                      double chevronSpacingM)
    {
      return Framework.nativeShowTrackLine(lat, lon, dimmed, ring, style, chevronSpacingM);
    }

    @Override
    public void remove(@NonNull int[] ids)
    {
      Framework.nativeRemoveTrackLine(ids);
    }
  };

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
    /** Metres between direction chevrons; 0 for none. */
    double spacingM;
    boolean dimmed;
    @Nullable
    Object owner;
    @Nullable
    int[] ids;

    void set(@Nullable Object owner, @Nullable double[] lat, @Nullable double[] lon, int ring, double spacingM)
    {
      final boolean same = lat != null && ids != null && ring == this.ring && Arrays.equals(lat, this.lat)
                           && Arrays.equals(lon, this.lon);
      // Already on the map (a line handed over by the screen before): only the owner changes.
      if (same && spacingM == this.spacingM)
      {
        this.owner = owner;
        setDimmed(false);
        return;
      }
      // The new line is drawn before the old one goes, so the route never blinks out.
      final int[] old = ids;
      ids = null;
      this.owner = owner;
      this.lat = lat;
      this.lon = lon;
      this.ring = ring;
      this.spacingM = spacingM;
      dimmed = false;
      draw();
      if (old != null)
      {
        if (TzTrace.on())
          TzTrace.log("line", name + " removed ids=" + Arrays.toString(old));
        sEngine.remove(old);
      }
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
        ids = sEngine.show(lat, lon, dimmed, ring, sStyle, dimmed ? 0 : spacingM);
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
      sEngine.remove(ids);
      ids = null;
    }
  }

  private static final Line sRoute = new Line("route");
  private static final Line sLeg = new Line("leg");

  /** Route line candidates for the daylight check (FEAT-005, D45): B (shipped), A (before v2), C, D. */
  public static final String[] STYLES = {"B", "A", "C", "D"};
  private static int sStyle;

  /** Draws the lines in another candidate style (the hidden About tools, for the owner's outdoor comparison). */
  public static void setStyle(int style)
  {
    if (style == sStyle)
      return;
    sStyle = style;
    for (Line l : new Line[] {sRoute, sLeg})
    {
      l.remove();
      l.draw();
    }
  }

  public static int style()
  {
    return sStyle;
  }

  /** Owns the lines between START RIDE and the ride screen taking them ({@link #transfer}). */
  public static final Object RIDE_START = new Object();

  private TzRouteLayer() {}

  /**
   * Hands {@code from}'s lines to {@code to} without touching the engine: the route stays drawn while one screen
   * replaces another (BUG-004: the preview's line went before the ride screen had loaded its own).
   */
  public static void transfer(@NonNull Object from, @NonNull Object to)
  {
    if (TzTrace.on())
      TzTrace.log("line", "transfer " + ownerName(from) + " -> " + ownerName(to) + " route=" + ownerName(sRoute.owner)
                          + " leg=" + ownerName(sLeg.owner));
    if (sRoute.owner == from)
      sRoute.owner = to;
    if (sLeg.owner == from)
      sLeg.owner = to;
  }

  /** Metres between direction chevrons on a ride's lines (BUG-002): about 96dp apart at the ride zooms. */
  static final double RIDE_CHEVRON_M = 150;
  /** A preview shows about this many chevrons along the route... */
  static final int PREVIEW_CHEVRONS = 14;

  /** ...so they are spaced by length, never closer than on a ride. */
  static double previewSpacing(@NonNull double[] lat, @NonNull double[] lon)
  {
    double m = 0;
    for (int i = 1; i < lat.length; i++)
      m += Geo.distance(lat[i - 1], lon[i - 1], lat[i], lon[i]);
    return Math.max(RIDE_CHEVRON_M, m / PREVIEW_CHEVRONS);
  }

  /** Shows a track (ring at its start), replacing the route shown; {@code ride}: chevrons spaced for the ride. */
  public static void show(@NonNull Object owner, @NonNull double[] lat, @NonNull double[] lon, boolean ride)
  {
    if (TzTrace.on())
      TzTrace.log("line", "show owner=" + ownerName(owner) + " points=" + lat.length + (ride ? " ride" : ""));
    sRoute.set(owner, lat, lon, RING_START, ride ? RIDE_CHEVRON_M : previewSpacing(lat, lon));
  }

  /** Shows the path to a place (ring at the place), replacing the route shown. */
  public static void showPath(@NonNull Object owner, @NonNull double[] lat, @NonNull double[] lon, boolean ride)
  {
    if (TzTrace.on())
      TzTrace.log("line", "showPath owner=" + ownerName(owner) + " points=" + lat.length + (ride ? " ride" : ""));
    sRoute.set(owner, lat, lon, RING_END, ride ? RIDE_CHEVRON_M : previewSpacing(lat, lon));
  }

  /** Shows the leg leading onto the route (to its start, or back onto it). */
  public static void showLeg(@NonNull Object owner, @NonNull double[] lat, @NonNull double[] lon)
  {
    if (TzTrace.on())
      TzTrace.log("line", "showLeg owner=" + ownerName(owner) + " points=" + lat.length);
    sLeg.set(owner, lat, lon, RING_NONE, RIDE_CHEVRON_M);
  }

  public static void hideLeg()
  {
    sLeg.set(null, null, null, RING_NONE, 0);
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
      sRoute.set(null, null, null, RING_NONE, 0);
    if (sLeg.owner == owner)
      sLeg.set(null, null, null, RING_NONE, 0);
  }

  @NonNull
  private static String ownerName(@Nullable Object owner)
  {
    if (owner == RIDE_START)
      return "RIDE_START";
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
