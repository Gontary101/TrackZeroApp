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
 * or the path to a place with a ring at the place), a leg leading onto it, and the part of a path to a place that is
 * walked (Phase 27: dots, a hollow ring where the bike is left). Lines requested before the map engine
 * exists are drawn once it does. Each line belongs to the screen that showed it: a screen going away hides only its
 * own (the next screen may have shown its lines already: fragment transactions reorder).
 */
public final class TzRouteLayer
{
  private static final int RING_NONE = 0;
  private static final int RING_START = 1;
  private static final int RING_END = 2;
  /** A hollow ring at the start only (the dotted hint to a stop, Phase 30). */
  private static final int RING_HOLLOW_START = 3;
  /** Hollow where the bike is left, and the place's ring at the end. */
  private static final int RING_LEAVE_BIKE_AND_END = 4;
  /** How a line looks: drawn, dimmed (off route), on foot (dots). */
  private static final int LOOK_LINE = 0;
  private static final int LOOK_DIMMED = 1;
  private static final int LOOK_WALK = 2;

  /** What draws the lines: the map engine (tests use a fake). */
  interface Engine
  {
    @NonNull
    int[] show(@NonNull double[] lat, @NonNull double[] lon, int look, int ring, int style, double chevronSpacingM);

    void remove(@NonNull int[] ids);

    /** The ridden part of a line: drawn faded up to {@code passedMercator} along it (mercator units). */
    default void setPassed(@NonNull int[] ids, double passedMercator) {}
  }

  @NonNull
  static Engine sEngine = new Engine() {
    @NonNull
    @Override
    public int[] show(@NonNull double[] lat, @NonNull double[] lon, int look, int ring, int style,
                      double chevronSpacingM)
    {
      return Framework.nativeShowTrackLine(lat, lon, look, ring, style, chevronSpacingM);
    }

    @Override
    public void remove(@NonNull int[] ids)
    {
      Framework.nativeRemoveTrackLine(ids);
    }

    @Override
    public void setPassed(@NonNull int[] ids, double passedMercator)
    {
      Framework.nativeSetTrackLinePassed(ids, passedMercator);
    }
  };

  /** A new faded length is sent when it has moved this far (mercator units: about a metre at 45°). */
  private static final double PASSED_STEP_MERCATOR = 1e-5;

  /** One line: its points, its ring and what the engine drew for it. */
  private static final class Line
  {
    @NonNull
    final String name;
    /** LOOK_LINE or LOOK_WALK; dimmed overrides it. */
    final int look;

    Line(@NonNull String name, int look)
    {
      this.name = name;
      this.look = look;
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
    /** Metres of it already ridden (drawn faded, Phase 38); 0: none. */
    double passedM;
    /** The faded length last sent for {@link #ids}, mercator units; -1: none. */
    double sentMercator = -1;
    /** Metres and mercator units from the start to each point, made when first needed. */
    @Nullable
    double[] cumM;
    @Nullable
    double[] cumMercator;

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
      passedM = 0;
      cumM = null;
      cumMercator = null;
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
        ids = sEngine.show(lat, lon, dimmed ? LOOK_DIMMED : look, ring, sStyle, dimmed ? 0 : spacingM);
        sentMercator = -1;
        applyPassed();
        if (TzTrace.on())
          TzTrace.log("line", name + " drawn owner=" + ownerName(owner) + " points=" + lat.length + " dimmed=" + dimmed
                              + " ids=" + Arrays.toString(ids));
      }
    }

    /** The ridden part, drawn faded (not on a dimmed line: off route the whole line is already faded). */
    void setPassed(double m)
    {
      passedM = Math.max(0, m);
      applyPassed();
    }

    private void applyPassed()
    {
      if (ids == null || lat == null || lon == null || dimmed)
        return;
      final double mercator = passedM <= 0 ? 0 : mercatorAt(passedM);
      if (sentMercator >= 0 && Math.abs(mercator - sentMercator) < PASSED_STEP_MERCATOR)
        return;
      sentMercator = mercator;
      sEngine.setPassed(ids, mercator);
    }

    /** The mercator distance along the line (as the engine measures it) at {@code m} metres along it. */
    private double mercatorAt(double m)
    {
      if (cumM == null || cumMercator == null)
      {
        final int n = lat.length;
        cumM = new double[n];
        cumMercator = new double[n];
        for (int i = 1; i < n; i++)
        {
          cumM[i] = cumM[i - 1] + Geo.distance(lat[i - 1], lon[i - 1], lat[i], lon[i]);
          cumMercator[i] = cumMercator[i - 1] + Math.hypot(lon[i] - lon[i - 1], mercatorY(lat[i]) - mercatorY(lat[i - 1]));
        }
      }
      final int n = cumM.length;
      if (m >= cumM[n - 1])
        return cumMercator[n - 1];
      int i = Arrays.binarySearch(cumM, m);
      if (i >= 0)
        return cumMercator[i];
      i = -i - 1;
      final double seg = cumM[i] - cumM[i - 1];
      final double t = seg <= 0 ? 0 : (m - cumM[i - 1]) / seg;
      return cumMercator[i - 1] + (cumMercator[i] - cumMercator[i - 1]) * t;
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

  private static final Line sRoute = new Line("route", LOOK_LINE);
  private static final Line sLeg = new Line("leg", LOOK_LINE);
  private static final Line sWalk = new Line("walk", LOOK_WALK);

  /** Route line candidates for the daylight check (FEAT-005, D45): B (shipped), A (before v2), C, D. */
  public static final String[] STYLES = {"B", "A", "C", "D"};
  private static int sStyle;

  /** Draws the lines in another candidate style (the hidden About tools, for the owner's outdoor comparison). */
  public static void setStyle(int style)
  {
    if (style == sStyle)
      return;
    sStyle = style;
    for (Line l : new Line[] {sRoute, sLeg, sWalk})
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
    if (sWalk.owner == from)
      sWalk.owner = to;
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

  /**
   * The walked part of a path to a place a bike cannot reach (Phase 27): dots from where the bike is left (a hollow
   * ring) to the place (its ring). The ridden part is shown with {@link #showRide}.
   */
  public static void showWalk(@NonNull Object owner, @NonNull double[] lat, @NonNull double[] lon)
  {
    if (TzTrace.on())
      TzTrace.log("line", "showWalk owner=" + ownerName(owner) + " points=" + lat.length);
    sWalk.set(owner, lat, lon, RING_LEAVE_BIKE_AND_END, 0);
  }

  /**
   * S2's hint to a stop (Phase 30): dots in a straight line from the route (a hollow ring there) to the place, whose
   * pin is a view. Uses the walk's slot: a ride screen is not showing at the same time.
   */
  public static void showHint(@NonNull Object owner, @NonNull double[] lat, @NonNull double[] lon)
  {
    sWalk.set(owner, lat, lon, RING_HOLLOW_START, 0);
  }

  public static void hideWalk()
  {
    sWalk.set(null, null, null, RING_NONE, 0);
  }

  /** The ridden part of a bike + walk path: no ring (the walk's ring and the place's are on the walk). */
  public static void showRide(@NonNull Object owner, @NonNull double[] lat, @NonNull double[] lon, boolean ride)
  {
    if (TzTrace.on())
      TzTrace.log("line", "showRide owner=" + ownerName(owner) + " points=" + lat.length + (ride ? " ride" : ""));
    sRoute.set(owner, lat, lon, RING_NONE, ride ? RIDE_CHEVRON_M : previewSpacing(lat, lon));
  }

  /** On a ride: the route's first {@code m} metres are ridden, drawn at a reduced opacity (Phase 38). */
  public static void setPassed(double m)
  {
    sRoute.setPassed(m);
  }

  /** CoMaps' mercator y of a latitude ({@code mercator::LatToY}), the unit the engine measures lines in. */
  static double mercatorY(double lat)
  {
    final double sin = Math.sin(Math.toRadians(Math.max(-86, Math.min(86, lat))));
    return Math.max(-180, Math.min(180, Math.toDegrees(0.5 * Math.log((1 + sin) / (1 - sin)))));
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
    if (sWalk.owner == owner)
      sWalk.set(null, null, null, RING_NONE, 0);
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
    sWalk.draw();
  }
}
