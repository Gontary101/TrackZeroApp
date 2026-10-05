package app.trackzero.ui;

import android.app.Activity;
import android.os.Build;
import android.view.Display;
import android.view.Window;
import android.view.WindowManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/** The display's refresh rate and system bars. */
public final class TzDisplay
{
  /** The lowest rate that still looks smooth. */
  private static final float SMOOTH_HZ = 59f;

  private TzDisplay() {}

  /**
   * During a ride the screen stays on for hours and the map moves at cycling speed: 60 Hz is plenty, and a 120 Hz
   * panel draws noticeably more power. Asks for the lowest smooth rate the display has ({@code on}), or gives the
   * choice back to the system.
   */
  public static void limitRefreshRate(@NonNull Activity activity, boolean on)
  {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
      return;
    final Window window = activity.getWindow();
    final WindowManager.LayoutParams lp = window.getAttributes();
    final Display.Mode mode = on ? lowestSmoothMode(display(activity)) : null;
    final int id = mode == null ? 0 : mode.getModeId();
    if (lp.preferredDisplayModeId == id)
      return;
    lp.preferredDisplayModeId = id;
    window.setAttributes(lp);
  }

  /**
   * The ride screen shows no status bar and no navigation bar (FEAT-001). A swipe from an edge shows them for a moment
   * over the screen; they hide again by themselves. System gestures keep working.
   */
  public static void setImmersive(@NonNull Activity activity, boolean on)
  {
    final Window window = activity.getWindow();
    final WindowInsetsControllerCompat c = WindowCompat.getInsetsController(window, window.getDecorView());
    if (on)
    {
      c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
      c.hide(WindowInsetsCompat.Type.systemBars());
    }
    else
    {
      c.show(WindowInsetsCompat.Type.systemBars());
    }
  }

  @NonNull
  private static Display display(@NonNull Activity activity)
  {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
      return activity.getDisplay();
    return defaultDisplay(activity);
  }

  /** Before Android 11, the only way. */
  @SuppressWarnings("deprecation")
  @NonNull
  private static Display defaultDisplay(@NonNull Activity activity)
  {
    return activity.getWindowManager().getDefaultDisplay();
  }

  /**
   * The slowest mode at the current resolution that is at least 59 Hz; null when the display has no faster one
   * (nothing to save).
   */
  @Nullable
  private static Display.Mode lowestSmoothMode(@NonNull Display display)
  {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
      return null;
    final Display.Mode current = display.getMode();
    Display.Mode slowest = null;
    float fastest = 0;
    for (Display.Mode m : display.getSupportedModes())
    {
      if (m.getPhysicalWidth() != current.getPhysicalWidth() || m.getPhysicalHeight() != current.getPhysicalHeight())
        continue;
      fastest = Math.max(fastest, m.getRefreshRate());
      if (m.getRefreshRate() >= SMOOTH_HZ && (slowest == null || m.getRefreshRate() < slowest.getRefreshRate()))
        slowest = m;
    }
    return slowest == null || slowest.getRefreshRate() >= fastest ? null : slowest;
  }
}
