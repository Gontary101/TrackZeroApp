package app.trackzero.ui;

import android.graphics.Paint;
import android.util.TypedValue;
import android.view.ViewGroup;
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

  /**
   * CSS {@code line-height} for a single-line view: the font's ascent + descent box is centred in a box of
   * {@code factor × text size}, so big numerals sit as tight as on the boards. Uses negative margins.
   */
  public static void applyLineHeight(@NonNull TextView tv, float factor)
  {
    final Paint.FontMetrics fm = tv.getPaint().getFontMetrics();
    final int halfLeading = Math.round((factor * tv.getTextSize() - (fm.descent - fm.ascent)) / 2);
    final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) tv.getLayoutParams();
    lp.topMargin = halfLeading;
    lp.bottomMargin = halfLeading;
    tv.setLayoutParams(lp);
  }
}
