package app.trackzero.ui;

import android.util.TypedValue;
import android.widget.TextView;
import androidx.annotation.NonNull;

/** Number sizing that follows the system font scale only up to 1.3x so hero digits never wrap. */
public final class TzTextSize
{
  public static final float MAX_FONT_SCALE = 1.3f;

  private TzTextSize() {}

  public static void applyCapped(@NonNull TextView tv, float sp)
  {
    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, cappedSp(tv.getResources().getConfiguration().fontScale, sp));
  }

  /** Size in sp that renders as {@code sp * min(fontScale, 1.3)}. */
  static float cappedSp(float fontScale, float sp)
  {
    return sp * Math.min(fontScale, MAX_FONT_SCALE) / fontScale;
  }
}
