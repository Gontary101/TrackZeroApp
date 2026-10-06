package app.trackzero.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import app.organicmaps.R;

/**
 * ToneTile (Components v2, Phase 32): 104dp, radius 20, surface3 (a warning: amberBg and amber), a 36dp glyph and its
 * label; pressed it shrinks to 0.96. M1's "Tap to hear".
 */
public class TzToneTile extends LinearLayout
{
  @NonNull
  private final ImageView mGlyph;
  @NonNull
  private final TextView mLabel;

  public TzToneTile(@NonNull Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
    setOrientation(VERTICAL);
    setGravity(Gravity.CENTER);
    setClickable(true);
    setFocusable(true);
    mGlyph = new ImageView(context);
    mGlyph.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    final int size = getResources().getDimensionPixelSize(R.dimen.tz_tone_tile_glyph);
    addView(mGlyph, new LayoutParams(size, size));
    mLabel = new TextView(context, null, 0, R.style.TzText_Tile);
    final LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    lp.topMargin = getResources().getDimensionPixelSize(R.dimen.tz_space_12);
    addView(mLabel, lp);
  }

  public void bind(@DrawableRes int glyph, @StringRes int label, boolean warning)
  {
    mGlyph.setImageResource(glyph);
    final int ink = ContextCompat.getColor(getContext(), warning ? R.color.tz_amber : R.color.tz_text1);
    ImageViewCompat.setImageTintList(mGlyph, ColorStateList.valueOf(ink));
    mLabel.setText(label);
    mLabel.setTextColor(ink);
    setBackgroundResource(warning ? R.drawable.tz_bg_amber_bg_r20 : R.drawable.tz_bg_surface3_r20);
    setContentDescription(getResources().getString(R.string.tz_tone_cd, mLabel.getText()));
  }

  @Override
  public boolean onTouchEvent(MotionEvent e)
  {
    switch (e.getActionMasked())
    {
    case MotionEvent.ACTION_DOWN -> animate().scaleX(TzMotion.PRESS_SCALE).scaleY(TzMotion.PRESS_SCALE).setDuration(TzMotion.PRESS_MS);
    case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> animate().scaleX(1f).scaleY(1f).setDuration(TzMotion.PRESS_MS);
    default -> {}
    }
    return super.onTouchEvent(e);
  }
}
