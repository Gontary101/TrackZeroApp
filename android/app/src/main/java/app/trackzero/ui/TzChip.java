package app.trackzero.ui;

import android.content.Context;
import android.os.SystemClock;
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
  /** Pulse frames per second: a small dot fading at 1 Hz looks the same at 10 frames as at the display's 60. */
  private static final int PULSE_FPS = 10;
  private static final long PULSE_PERIOD_MS = 1000;

  private boolean mPulsing;
  private boolean mAttached;
  private boolean mPulseRunning;
  private final Runnable mPulseFrame = this::pulseFrame;

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
    TzViews.setText(mText, text);
    mText.setTextColor(ContextCompat.getColor(getContext(), textColor));
    mDot.setBackgroundTintList(ContextCompat.getColorStateList(getContext(), dotColor));
  }

  /**
   * REC dot: alpha 1 → 0.4 → 1 every second. Off when the system disables animations. The caller stops it while the
   * chip is not on screen (a ride page beside the shown one is still attached).
   */
  public void setPulsing(boolean pulsing)
  {
    mPulsing = pulsing;
    updatePulse();
  }

  private void updatePulse()
  {
    final boolean run = mPulsing && mAttached && TzMotion.enabled(getContext());
    if (run == mPulseRunning)
      return;
    mPulseRunning = run;
    removeCallbacks(mPulseFrame);
    if (run)
      pulseFrame();
    else
      mDot.setAlpha(1f);
  }

  private void pulseFrame()
  {
    final double phase = (SystemClock.uptimeMillis() % PULSE_PERIOD_MS) / (double) PULSE_PERIOD_MS;
    mDot.setAlpha((float) (0.7 + 0.3 * Math.cos(2 * Math.PI * phase)));
    postDelayed(mPulseFrame, 1000 / PULSE_FPS);
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
