package app.trackzero;

/** Feature flags and tuning constants (plan §3.1, §X4). */
public final class TzConfig
{
  /** Shows TrackZero instead of the base-app UI. */
  public static final boolean ENABLED = true;
  /** Replays {@code filesDir/tz/replay.gpx} as GPS during rides (debug testing). */
  public static final boolean DEBUG_REPLAY = false;

  public static final double AUTO_PAUSE_KMH = 3.0;
  public static final double AUTO_RESUME_KMH = 5.0;
  public static final int AUTO_PAUSE_SECONDS = 5;
  /** On foot (Walk, Hike; Phase 25): pause below 1 km/h for 10 s, resume above 2 km/h. */
  public static final double AUTO_PAUSE_FOOT_KMH = 1.0;
  public static final double AUTO_RESUME_FOOT_KMH = 2.0;
  public static final int AUTO_PAUSE_FOOT_SECONDS = 10;

  public static final double OFF_ROUTE_M = 40;
  public static final double BACK_ON_ROUTE_M = 20;
  public static final int OFF_ROUTE_SECONDS = 5;

  public static final double IMMINENT_M = 30;
  public static final double VOICE_FAR_M = 200;
  public static final double VOICE_NEAR_M = 50;

  public static final double DP_EPSILON_M = 8;
  public static final double MANEUVER_BEARING_WINDOW_M = 30;
  public static final double MANEUVER_MERGE_M = 20;

  public static final double GRADE_WINDOW_M = 50;
  public static final double GRADE_MIN_KMH = 4;
  public static final double ELE_HYSTERESIS_M = 3;

  public static final double CLIMB_MIN_GAIN_M = 30;
  public static final double CLIMB_MIN_LEN_M = 500;
  public static final double CLIMB_MIN_GRADE = 3.0;
  public static final double CLIMB_DROP_END_M = 10;

  public static final long HOLD_TO_END_MS = 1500;
  /** The map's position updates once a second, as the ride's GPS (CoMaps: twice a second). */
  public static final long MAP_LOCATION_INTERVAL_MS = 1000;
  public static final long PREVIEW_REEXPAND_MS = 1500;
  public static final int ETA_WINDOW_MIN = 15;
  public static final double ETA_FALLBACK_KMH = 18;
  /** On foot: the walking speed of the time estimates (Phase 25). */
  public static final double ETA_FALLBACK_FOOT_KMH = 4.8;
  /** The ride zoom on foot, whatever the speed (Phase 25). */
  public static final int RIDE_ZOOM_FOOT = 17;

  public static final double REPLAY_KMH = 25;
  public static final double IGNORE_FIX_ACCURACY_M = 30;

  private TzConfig() {}
}
