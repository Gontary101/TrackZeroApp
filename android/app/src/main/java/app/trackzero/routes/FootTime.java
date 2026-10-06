package app.trackzero.routes;

import app.trackzero.TzConfig;

/** How long a path takes on foot: walking (Phase 25) and hiking (Phase 26). Pure. */
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

  /**
   * Seconds to hike {@code distanceM} climbing {@code ascentM} and descending {@code descentM} (NaN: no altitudes,
   * distance only): the longer of the walking and the climbing time, plus half the shorter. Walking 4 km/h; 300 m an
   * hour up, 500 m an hour down.
   */
  public static long hikeSeconds(double distanceM, double ascentM, double descentM)
  {
    final double th = distanceM / 1000 / 4;
    final double tv = Double.isNaN(ascentM) || Double.isNaN(descentM)
                      ? 0 : Math.max(0, ascentM) / 300 + Math.max(0, descentM) / 500;
    return Math.round((Math.max(th, tv) + Math.min(th, tv) / 2) * 3600);
  }
}
