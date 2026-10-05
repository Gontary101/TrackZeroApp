package app.trackzero.ui;

import android.content.Context;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

/** Motion tokens (plan §X5) and the system "Remove animations" switch. */
public final class TzMotion
{
  public static final long PRESS_MS = 90;
  public static final float PRESS_SCALE = 0.96f;
  public static final long CROSSFADE_MS = 150;
  public static final long DOT_MS = 200;
  public static final long CAROUSEL_MS = 280;
  public static final long SHEET_MS = 320;
  public static final long COLLAPSE_MS = 180;
  /** A card growing for content that arrives late (the preview's surface bar). */
  public static final long GROW_MS = 180;
  public static final long EXPAND_MS = 240;
  public static final long RESCALE_MS = 300;

  public static final FastOutSlowInInterpolator EASE = new FastOutSlowInInterpolator();

  private TzMotion() {}

  /** False when the user turned animations off (animator duration scale 0). */
  public static boolean enabled(@NonNull Context context)
  {
    return Settings.Global.getFloat(context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
  }
}
