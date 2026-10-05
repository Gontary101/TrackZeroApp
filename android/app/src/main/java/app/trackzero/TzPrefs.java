package app.trackzero;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import app.trackzero.format.TzFormat;
import app.trackzero.routes.Profile;

/** TrackZero settings in SharedPreferences "tz_prefs" (plan §9.6, Phase 13). */
public final class TzPrefs
{
  private static final String FILE = "tz_prefs";
  private static final String UNITS = "units";
  private static final String VOICE = "voice";
  private static final String KEEP_AWAKE = "keepAwake";
  private static final String AUTO_PAUSE = "autoPause";
  private static final String FIRST_CAMERA_DONE = "firstCameraDone";
  private static final String TRACE = "trace";
  private static final String LINE_STYLE = "lineStyle";
  private static final String PROFILE = "profile";

  private TzPrefs() {}

  @NonNull
  private static SharedPreferences prefs(@NonNull Context c)
  {
    return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
  }

  @NonNull
  public static TzFormat.Units units(@NonNull Context c)
  {
    return "imperial".equals(prefs(c).getString(UNITS, "metric")) ? TzFormat.Units.IMPERIAL : TzFormat.Units.METRIC;
  }

  /** False until units were chosen (or taken from the map's default, {@code TzBase.syncUnits}). */
  public static boolean hasUnits(@NonNull Context c)
  {
    return prefs(c).contains(UNITS);
  }

  public static void setUnits(@NonNull Context c, @NonNull TzFormat.Units units)
  {
    prefs(c).edit().putString(UNITS, units == TzFormat.Units.IMPERIAL ? "imperial" : "metric").apply();
  }

  public static boolean voice(@NonNull Context c)
  {
    return prefs(c).getBoolean(VOICE, true);
  }

  public static void setVoice(@NonNull Context c, boolean on)
  {
    prefs(c).edit().putBoolean(VOICE, on).apply();
  }

  public static boolean keepAwake(@NonNull Context c)
  {
    return prefs(c).getBoolean(KEEP_AWAKE, true);
  }

  public static void setKeepAwake(@NonNull Context c, boolean on)
  {
    prefs(c).edit().putBoolean(KEEP_AWAKE, on).apply();
  }

  public static boolean autoPause(@NonNull Context c)
  {
    return prefs(c).getBoolean(AUTO_PAUSE, true);
  }

  public static void setAutoPause(@NonNull Context c, boolean on)
  {
    prefs(c).edit().putBoolean(AUTO_PAUSE, on).apply();
  }

  /** The very first launch centres the map on the rider at z15 once; later launches keep the last viewport. */
  public static boolean firstCameraDone(@NonNull Context c)
  {
    return prefs(c).getBoolean(FIRST_CAMERA_DONE, false);
  }

  public static void setFirstCameraDone(@NonNull Context c)
  {
    prefs(c).edit().putBoolean(FIRST_CAMERA_DONE, true).apply();
  }

  /** The route line candidate shown (hidden About tools, Phase 20): 0 = B, the default. */
  public static int lineStyle(@NonNull Context c)
  {
    return prefs(c).getInt(LINE_STYLE, 0);
  }

  public static void setLineStyle(@NonNull Context c, int style)
  {
    prefs(c).edit().putInt(LINE_STYLE, style).apply();
  }

  /** The profile chosen last (Phase 22), the default for the next route and free ride; ROAD at first. */
  @NonNull
  public static Profile profile(@NonNull Context c)
  {
    final Profile p = Profile.of(prefs(c).getString(PROFILE, null));
    return Profile.available().contains(p) ? p : Profile.ROAD;
  }

  public static void setProfile(@NonNull Context c, @NonNull Profile p)
  {
    prefs(c).edit().putString(PROFILE, p.id).apply();
  }

  /** The hidden tracing switch of release builds (About, Phase 16). */
  public static boolean trace(@NonNull Context c)
  {
    return prefs(c).getBoolean(TRACE, false);
  }

  public static void setTrace(@NonNull Context c, boolean on)
  {
    prefs(c).edit().putBoolean(TRACE, on).apply();
  }
}
