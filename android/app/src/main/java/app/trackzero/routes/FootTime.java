package app.trackzero.routes;

import app.trackzero.TzConfig;

/** How long a path takes on foot (Phase 25): walking speed plus the climbing. Pure. */
public final class FootTime
{
  /** An hour more for every this many metres climbed. */
  static final double CLIMB_M_PER_HOUR = 600;

  private FootTime() {}

  /** Seconds to walk {@code distanceM} climbing {@code ascentM} (NaN: no altitudes, distance only). */
  public static long walkSeconds(double distanceM, double ascentM)
  {
    final double hours = distanceM / 1000 / TzConfig.ETA_FALLBACK_FOOT_KMH
                         + (Double.isNaN(ascentM) ? 0 : Math.max(0, ascentM) / CLIMB_M_PER_HOUR);
    return Math.round(hours * 3600);
  }
}
