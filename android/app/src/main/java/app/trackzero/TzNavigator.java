package app.trackzero;

import android.app.Activity;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Hosts TrackZero screens inside {@code tz_root}, above the map. */
public final class TzNavigator
{
  @Nullable
  private static Activity sActivity;
  @Nullable
  private static FrameLayout sRoot;
  private static int sTopInset;
  private static int sBottomInset;

  private TzNavigator() {}

  public static void attach(@NonNull Activity activity, @NonNull FrameLayout root)
  {
    sActivity = activity;
    sRoot = root;
    ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
      final Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
      sTopInset = bars.top;
      sBottomInset = bars.bottom;
      return windowInsets;
    });
  }

  public static void detach(@NonNull Activity activity)
  {
    if (sActivity != activity)
      return;
    sActivity = null;
    sRoot = null;
  }

  /** Status bar height in px; screens place top content at this + {@code tz_top_inset_extra}. */
  public static int topInset()
  {
    return sTopInset;
  }

  /** Navigation bar / gesture area height in px; screens place bottom content above it. */
  public static int bottomInset()
  {
    return sBottomInset;
  }
}
