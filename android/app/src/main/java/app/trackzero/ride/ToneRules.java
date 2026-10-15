package app.trackzero.ride;

import androidx.annotation.NonNull;
import app.trackzero.follow.Maneuver;
import app.trackzero.follow.RouteManeuver;
import java.util.ArrayList;
import java.util.List;

/**
 * When a tone plays (FEAT-008, Phase 32). A turn about 200 m ahead: its pattern once; at about 50 m: twice, 250 ms
 * apart. Arriving, joining the route or reaching a stop: ARRIVE. Off route and wrong way: once when the state begins,
 * not again until the rider is back on the route. GPS lost: no fix for 20 s while recording, once per loss; GPS_BACK
 * once fixes have come for 5 s. Nothing while the ride is paused. Turn tones are the switch for every tone; the
 * off-route and GPS-lost warnings each have their own as well. Pure.
 */
public final class ToneRules
{
  static final long REPEAT_MS = 250;
  /** Between two patterns of one sound, as between notes. */
  static final long PATTERN_GAP_MS = 40;
  public static final long GPS_LOST_MS = 20_000;
  static final long GPS_BACK_MS = 5_000;
  /** A roundabout's exit lies this far round to count as right or left. */
  static final double SIDE_DEG = 20;

  /** The settings, read when a sound is due. */
  public interface Settings
  {
    boolean tones();

    boolean warnOffRoute();

    boolean warnGps();

    boolean paused();
  }

  /** A pattern to play {@code atMs} after the first. */
  public static final class Sound
  {
    @NonNull
    public final TonePattern pattern;
    public final long atMs;

    Sound(@NonNull TonePattern pattern, long atMs)
    {
      this.pattern = pattern;
      this.atMs = atMs;
    }

    @NonNull
    @Override
    public String toString()
    {
      return pattern + "@" + atMs;
    }
  }

  @NonNull
  private final Settings mSettings;
  private boolean mOffRouteArmed = true;
  private long mLastFixMs = -1;
  private boolean mGpsLost;
  /** Since when fixes have come again after a loss; -1 when none yet. */
  private long mBackSinceMs = -1;

  public ToneRules(@NonNull Settings settings)
  {
    mSettings = settings;
  }

  // ---- Turns ----

  @NonNull
  public List<Sound> turnSoon(@NonNull RouteManeuver m)
  {
    return turns() ? sequence(patterns(m), 1) : new ArrayList<>();
  }

  @NonNull
  public List<Sound> turnNow(@NonNull RouteManeuver m)
  {
    return turns() ? sequence(patterns(m), 2) : new ArrayList<>();
  }

  /** Arrived, joined the route, reached a stop. */
  @NonNull
  public List<Sound> arrived()
  {
    return turns() ? one(TonePattern.ARRIVE) : new ArrayList<>();
  }

  private boolean turns()
  {
    return mSettings.tones() && !mSettings.paused();
  }

  /** The patterns of a maneuver; LEAVE_BIKE is STRAIGHT then ARRIVE, a roundabout its two notes then the exit's side. */
  @NonNull
  static List<TonePattern> patterns(@NonNull RouteManeuver m)
  {
    final List<TonePattern> out = new ArrayList<>();
    switch (m.type)
    {
    case TURN_RIGHT, SLIGHT_RIGHT -> out.add(TonePattern.RIGHT);
    case TURN_LEFT, SLIGHT_LEFT -> out.add(TonePattern.LEFT);
    case STRAIGHT, HEAD_TO_START, HEAD_TO_PATH, REROUTE -> out.add(TonePattern.STRAIGHT);
    case UTURN -> out.add(TonePattern.UTURN);
    case ARRIVE, JOIN, STOP -> out.add(TonePattern.ARRIVE);
    case LEAVE_BIKE ->
    {
      out.add(TonePattern.STRAIGHT);
      out.add(TonePattern.ARRIVE);
    }
    case ROUNDABOUT ->
    {
      out.add(TonePattern.ROUNDABOUT);
      // The router gives the exit's number, not its side; a turn worked out from a track has the angle.
      if (m.angleDeg > SIDE_DEG)
        out.add(TonePattern.RIGHT);
      else if (m.angleDeg < -SIDE_DEG)
        out.add(TonePattern.LEFT);
    }
    }
    return out;
  }

  /** The patterns one after another (40 ms between), the whole {@code times} times, 250 ms apart. */
  @NonNull
  static List<Sound> sequence(@NonNull List<TonePattern> patterns, int times)
  {
    final List<Sound> out = new ArrayList<>();
    long at = 0;
    for (int t = 0; t < times; t++)
    {
      if (t > 0)
        at += REPEAT_MS - PATTERN_GAP_MS;
      for (TonePattern p : patterns)
      {
        out.add(new Sound(p, at));
        at += p.durationMs() + PATTERN_GAP_MS;
      }
    }
    return out;
  }

  @NonNull
  private static List<Sound> one(@NonNull TonePattern p)
  {
    final List<Sound> out = new ArrayList<>();
    out.add(new Sound(p, 0));
    return out;
  }

  // ---- Warnings ----

  /**
   * Off route began: the warning, once until the rider is back on the route. Call {@link #warnOffRoute} for the
   * spoken words, which have their own rule (voice on).
   */
  @NonNull
  public List<Sound> offRoute()
  {
    if (!mOffRouteArmed)
      return new ArrayList<>();
    mOffRouteArmed = false;
    return warnOffRouteTone() ? one(TonePattern.OFF_ROUTE) : new ArrayList<>();
  }

  /** Wrong way began (the engine tells it once per episode). */
  @NonNull
  public List<Sound> wrongWay()
  {
    return warnOffRouteTone() ? one(TonePattern.WRONG_WAY) : new ArrayList<>();
  }

  public void backOnRoute()
  {
    mOffRouteArmed = true;
  }

  /** Whether the off-route and wrong-way warnings are heard (their spoken words too). */
  public boolean warnOffRoute()
  {
    return mSettings.warnOffRoute() && !mSettings.paused();
  }

  private boolean tonesFor(boolean warning)
  {
    return mSettings.tones() && warning;
  }

  // ---- GPS ----

  /** An accepted fix: GPS_BACK once fixes have come again for 5 s after a loss. */
  @NonNull
  public List<Sound> fix(long elapsedMs)
  {
    mLastFixMs = elapsedMs;
    if (!mGpsLost)
      return new ArrayList<>();
    if (mBackSinceMs < 0)
      mBackSinceMs = elapsedMs;
    if (elapsedMs - mBackSinceMs < GPS_BACK_MS)
      return new ArrayList<>();
    mGpsLost = false;
    mBackSinceMs = -1;
    return gps() ? one(TonePattern.GPS_BACK) : new ArrayList<>();
  }

  /** The check, 20 s after the last fix (and whenever it is convenient): GPS_LOST once per loss. */
  @NonNull
  public List<Sound> check(long elapsedMs)
  {
    if (mSettings.paused() || mLastFixMs < 0)
      return new ArrayList<>();
    if (mGpsLost)
    {
      // A gap during the recovery: it starts again.
      if (mBackSinceMs >= 0 && elapsedMs - mLastFixMs >= GPS_BACK_MS)
        mBackSinceMs = -1;
      return new ArrayList<>();
    }
    if (elapsedMs - mLastFixMs < GPS_LOST_MS)
      return new ArrayList<>();
    mGpsLost = true;
    mBackSinceMs = -1;
    return gps() ? one(TonePattern.GPS_LOST) : new ArrayList<>();
  }

  /** The ride resumed (or started): the time without a fix counts from now. */
  public void restart(long elapsedMs)
  {
    mLastFixMs = elapsedMs;
    mGpsLost = false;
    mBackSinceMs = -1;
  }

  /** No fix for 20 s or more and not back yet: the NO GPS card. */
  public boolean gpsLost()
  {
    return mGpsLost;
  }

  /** When the last fix came, or -1. */
  public long lastFixMs()
  {
    return mLastFixMs;
  }

  private boolean gps()
  {
    return tonesFor(mSettings.warnGps());
  }

  private boolean warnOffRouteTone()
  {
    return tonesFor(warnOffRoute());
  }
}
