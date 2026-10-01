package app.trackzero.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import app.organicmaps.R;

/** 32dp status pill with a dot: GPS accuracy (B1) and REC / PAUSED (ride pages). */
public class TzChip extends LinearLayout
{
  @NonNull
  private final View mDot;
  @NonNull
  private final TextView mText;
  @Nullable
  private ObjectAnimator mPulse;
  private boolean mPulsing;
  private boolean mAttached;

  public TzChip(@NonNull Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
    setOrientation(HORIZONTAL);
    setGravity(Gravity.CENTER_VERTICAL);
    setBackgroundResource(R.drawable.tz_bg_surface2_r16);
    final int pad = getResources().getDimensionPixelSize(R.dimen.tz_space_12);
    setPadding(pad, 0, pad, 0);
    setMinimumHeight(getResources().getDimensionPixelSize(R.dimen.tz_chip_small_h));

    final int dot = getResources().getDimensionPixelSize(R.dimen.tz_dot);
    mDot = new View(context);
    mDot.setBackgroundResource(R.drawable.tz_dot);
    addView(mDot, new LayoutParams(dot, dot));

    mText = new TextView(context, null, 0, R.style.TzText_Chip);
    final LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    lp.setMarginStart(getResources().getDimensionPixelSize(R.dimen.tz_space_8));
    addView(mText, lp);
  }

  /** Dot and text colours. */
  public void setState(@NonNull CharSequence text, @ColorRes int dotColor, @ColorRes int textColor)
  {
    mText.setText(text);
    mText.setTextColor(ContextCompat.getColor(getContext(), textColor));
    mDot.setBackgroundTintList(ContextCompat.getColorStateList(getContext(), dotColor));
  }

  /** REC dot: alpha 1 → 0.4 at 1 Hz. Off when the system disables animations. */
  public void setPulsing(boolean pulsing)
  {
    mPulsing = pulsing;
    updatePulse();
  }

  private void updatePulse()
  {
    final boolean run = mPulsing && mAttached && TzMotion.enabled(getContext());
    if (run == (mPulse != null))
      return;
    if (!run)
    {
      mPulse.cancel();
      mPulse = null;
      mDot.setAlpha(1f);
      return;
    }
    mPulse = ObjectAnimator.ofFloat(mDot, View.ALPHA, 1f, 0.4f);
    mPulse.setDuration(500);
    mPulse.setRepeatMode(ValueAnimator.REVERSE);
    mPulse.setRepeatCount(ValueAnimator.INFINITE);
    mPulse.start();
  }

  @Override
  protected void onAttachedToWindow()
  {
    super.onAttachedToWindow();
    mAttached = true;
    updatePulse();
  }

  @Override
  protected void onDetachedFromWindow()
  {
    super.onDetachedFromWindow();
    mAttached = false;
    updatePulse();
  }
}
