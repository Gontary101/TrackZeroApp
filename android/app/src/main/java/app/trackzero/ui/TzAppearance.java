package app.trackzero.ui;

import android.app.UiModeManager;
import android.content.Context;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;

/**
 * Applies the rider's appearance (Phase 36) as the app's night mode, with one recreation of the screen when it
 * changes (Phase 38). On Android 12+ the system keeps an app's own night mode (it also themes the system splash):
 * that is the one switch, and AppCompat follows it. Before 12, AppCompat's mode is the switch. Setting both made two
 * recreations in a row with a black screen between them.
 */
public final class TzAppearance
{
  private TzAppearance() {}

  public static void apply(@NonNull Context context, boolean light)
  {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
    {
      ((UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE))
          .setApplicationNightMode(light ? UiModeManager.MODE_NIGHT_NO : UiModeManager.MODE_NIGHT_YES);
      // "System" here is the app's own mode set just above, not the phone's.
      AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
    else
    {
      AppCompatDelegate.setDefaultNightMode(light ? AppCompatDelegate.MODE_NIGHT_NO
                                                  : AppCompatDelegate.MODE_NIGHT_YES);
    }
  }
}
