package app.trackzero.ride;

/**
 * How often the GPS reports (FEAT-011, Phase 35). Normally once a second, batched up to 15 s with the screen off when
 * nothing is spoken. In the battery saver: on a guided ride every 3 s (batched up to 9 s with the screen off) while
 * the next maneuver is more than 600 m away and the rider is on the route; once a second and at once within 600 m
 * of it, or off route, or riding the wrong way. A free ride in the saver: every 3 s, batched up to 15 s. Pure.
 */
public final class SaverPolicy
{
  static final long NORMAL_INTERVAL_MS = 1000;
  static final long SAVER_INTERVAL_MS = 3000;
  static final long SCREEN_OFF_BATCH_MS = 15_000;
  static final long SAVER_GUIDED_BATCH_MS = 9_000;
  /** Within this of the next maneuver, fixes come every second: the 50 m tone must not be late. */
  public static final double NEAR_MANEUVER_M = 600;

  /** The request: fixes this often, and delivered within this delay (0: at once). */
  public static final class Gps
  {
    public final long intervalMs;
    public final long maxDelayMs;

    Gps(long intervalMs, long maxDelayMs)
    {
      this.intervalMs = intervalMs;
      this.maxDelayMs = maxDelayMs;
    }

    @Override
    public String toString()
    {
      return intervalMs + "/" + maxDelayMs;
    }
  }

  private SaverPolicy() {}

  /**
   * @param nextManeuverM distance to the next maneuver, NaN when not known (not placed yet: treated as near)
   * @param warning       off route or riding the wrong way
   * @param speaks        a phrase or a tone may play (it must not come late)
   */
  public static Gps choose(boolean saver, boolean guided, double nextManeuverM, boolean warning, boolean interactive,
                           boolean speaks)
  {
    if (!saver)
      return new Gps(NORMAL_INTERVAL_MS, interactive || speaks ? 0 : SCREEN_OFF_BATCH_MS);
    if (!guided)
      return new Gps(SAVER_INTERVAL_MS, interactive ? 0 : SCREEN_OFF_BATCH_MS);
    if (warning || Double.isNaN(nextManeuverM) || nextManeuverM <= NEAR_MANEUVER_M)
      return new Gps(NORMAL_INTERVAL_MS, 0);
    return new Gps(SAVER_INTERVAL_MS, interactive ? 0 : SAVER_GUIDED_BATCH_MS);
  }
}
