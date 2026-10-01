package app.trackzero.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import app.organicmaps.R;

/** Visual-only switch of TzToggleRow: 56×34 track (mint on / text3 off), 26dp knob (black on / text2 off). */
class TzSwitch extends View
{
  private static final long MOVE_MS = 150;

  private final Paint mTrack = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint mKnob = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF mRect = new RectF();
  private final int mTrackOn;
  private final int mTrackOff;
  private final int mKnobOn;
  private final int mKnobOff;
  private final float mKnobRadius;
  private final float mInset;
  /** 0 = off, 1 = on. */
  private float mPosition;
  @Nullable
  private ValueAnimator mAnimator;

  TzSwitch(@NonNull Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
    mTrackOn = ContextCompat.getColor(context, R.color.tz_mint);
    mTrackOff = ContextCompat.getColor(context, R.color.tz_text3);
    mKnobOn = ContextCompat.getColor(context, R.color.tz_on_mint);
    mKnobOff = ContextCompat.getColor(context, R.color.tz_text2);
    mKnobRadius = getResources().getDimension(R.dimen.tz_switch_knob) / 2f;
    mInset = (getResources().getDimension(R.dimen.tz_switch_h) - getResources().getDimension(R.dimen.tz_switch_knob))
             / 2f;
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  }

  void setChecked(boolean checked, boolean animate)
  {
    final float target = checked ? 1f : 0f;
    if (mAnimator != null)
      mAnimator.cancel();
    if (!animate || !TzMotion.enabled(getContext()))
    {
      mPosition = target;
      invalidate();
      return;
    }
    mAnimator = ValueAnimator.ofFloat(mPosition, target);
    mAnimator.setDuration(MOVE_MS);
    mAnimator.setInterpolator(TzMotion.EASE);
    mAnimator.addUpdateListener(a -> {
      mPosition = (float) a.getAnimatedValue();
      invalidate();
    });
    mAnimator.start();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
  {
    setMeasuredDimension(getResources().getDimensionPixelSize(R.dimen.tz_switch_w),
                         getResources().getDimensionPixelSize(R.dimen.tz_switch_h));
  }

  @Override
  protected void onDraw(@NonNull Canvas canvas)
  {
    final float h = getHeight();
    mRect.set(0, 0, getWidth(), h);
    mTrack.setColor(ColorUtils.blendARGB(mTrackOff, mTrackOn, mPosition));
    canvas.drawRoundRect(mRect, h / 2f, h / 2f, mTrack);
    final float cxOff = mInset + mKnobRadius;
    final float cxOn = getWidth() - mInset - mKnobRadius;
    mKnob.setColor(ColorUtils.blendARGB(mKnobOff, mKnobOn, mPosition));
    canvas.drawCircle(cxOff + (cxOn - cxOff) * mPosition, h / 2f, mKnobRadius, mKnob);
  }
}
