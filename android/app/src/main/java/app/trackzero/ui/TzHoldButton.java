package app.trackzero.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import app.organicmaps.R;
import app.trackzero.TzConfig;

/**
 * "Hold to end & save": a fill grows left → right while the finger is down and confirms at 100 %.
 * Progress follows elapsed time on the frame clock (not an animator), so disabling system animations can never
 * shorten the hold.
 */
public class TzHoldButton extends View
{
  public interface Listener
  {
    void onConfirmed();
  }

  private static final long RELEASE_MS = 200;

  private final Paint mBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final TextPaint mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private final RectF mRect = new RectF();
  private final Path mClip = new Path();
  @NonNull
  private final Drawable mIcon;
  private final float mRadius;
  private final int mIconSize;
  private final int mIconGap;
  @NonNull
  private String mLabel;
  @Nullable
  private Listener mListener;

  private float mProgress;
  private boolean mHolding;
  private boolean mConfirmed;
  // Frame-clock anchors: progress at the last direction change and when it happened.
  private float mFromProgress;
  private long mFromTime;

  private final Runnable mFrame = this::onFrame;

  public TzHoldButton(@NonNull Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
    mRadius = getResources().getDimension(R.dimen.tz_radius_button);
    mIconSize = getResources().getDimensionPixelSize(R.dimen.tz_button_icon);
    mIconGap = getResources().getDimensionPixelSize(R.dimen.tz_button_icon_gap);
    final int danger = ContextCompat.getColor(context, R.color.tz_danger);
    mBgPaint.setColor(ContextCompat.getColor(context, R.color.tz_danger_bg));
    mFillPaint.setColor(ContextCompat.getColor(context, R.color.tz_danger_fill));
    mTextPaint.setColor(danger);
    mTextPaint.setTypeface(ResourcesCompat.getFont(context, R.font.barlow_condensed_bold));
    mTextPaint.setTextSize(getResources().getDimension(R.dimen.tz_text_22));
    mTextPaint.setLetterSpacing(0.09f);
    mIcon = ContextCompat.getDrawable(context, R.drawable.ic_tz_stop).mutate();
    mIcon.setTint(danger);
    mLabel = getResources().getString(R.string.tz_hold_to_end);
    setContentDescription(getResources().getString(R.string.tz_cd_hold_to_end));
    setFocusable(true);
    // Screen readers: double-tap-and-hold maps to a long click.
    ViewCompat.replaceAccessibilityAction(this, AccessibilityActionCompat.ACTION_LONG_CLICK,
                                          getResources().getString(R.string.tz_cd_hold_to_end), (v, args) -> {
                                            confirm();
                                            return true;
                                          });
  }

  public void setListener(@Nullable Listener listener)
  {
    mListener = listener;
  }

  /** Back to the initial state, e.g. when the sheet is reopened. */
  public void reset()
  {
    removeCallbacks(mFrame);
    mHolding = false;
    mConfirmed = false;
    mProgress = 0;
    mLabel = getResources().getString(R.string.tz_hold_to_end);
    invalidate();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
  {
    final int h = getResources().getDimensionPixelSize(R.dimen.tz_button_h);
    setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                         resolveSize(h, heightMeasureSpec));
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh)
  {
    mRect.set(0, 0, w, h);
    mClip.rewind();
    mClip.addRoundRect(mRect, mRadius, mRadius, Path.Direction.CW);
  }

  @Override
  public boolean onTouchEvent(@NonNull MotionEvent e)
  {
    if (mConfirmed || !isEnabled())
      return false;
    switch (e.getActionMasked())
    {
    case MotionEvent.ACTION_DOWN ->
    {
      getParent().requestDisallowInterceptTouchEvent(true);
      performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
      setHolding(true);
      return true;
    }
    case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
    {
      setHolding(false);
      return true;
    }
    default ->
    {
      return true;
    }
    }
  }

  private void setHolding(boolean holding)
  {
    mHolding = holding;
    mFromProgress = mProgress;
    mFromTime = SystemClock.uptimeMillis();
    removeCallbacks(mFrame);
    postOnAnimation(mFrame);
  }

  private void onFrame()
  {
    final long dt = SystemClock.uptimeMillis() - mFromTime;
    if (mHolding)
    {
      mProgress = Math.min(1f, mFromProgress + (float) dt / TzConfig.HOLD_TO_END_MS);
      if (mProgress >= 1f)
      {
        confirm();
        return;
      }
    }
    else
    {
      // Release early: drain the whole fill in 200 ms whatever its level.
      mProgress = Math.max(0f, mFromProgress - mFromProgress * dt / RELEASE_MS);
    }
    invalidate();
    if (mHolding || mProgress > 0f)
      postOnAnimation(mFrame);
  }

  private void confirm()
  {
    if (mConfirmed)
      return;
    removeCallbacks(mFrame);
    mHolding = false;
    mConfirmed = true;
    mProgress = 1f;
    mLabel = getResources().getString(R.string.tz_ride_saved_button);
    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    invalidate();
    if (mListener != null)
      mListener.onConfirmed();
  }

  @Override
  protected void onDetachedFromWindow()
  {
    super.onDetachedFromWindow();
    removeCallbacks(mFrame);
    if (!mConfirmed)
    {
      mHolding = false;
      mProgress = 0;
    }
  }

  @Override
  protected void onDraw(@NonNull Canvas canvas)
  {
    canvas.drawRoundRect(mRect, mRadius, mRadius, mBgPaint);
    if (mProgress > 0f)
    {
      final int save = canvas.save();
      canvas.clipPath(mClip);
      canvas.drawRect(0, 0, mRect.width() * mProgress, mRect.height(), mFillPaint);
      canvas.restoreToCount(save);
    }
    final float textW = mTextPaint.measureText(mLabel);
    final float contentW = mIconSize + mIconGap + textW;
    final float left = (getWidth() - contentW) / 2f;
    final int iconTop = (getHeight() - mIconSize) / 2;
    mIcon.setBounds((int) left, iconTop, (int) left + mIconSize, iconTop + mIconSize);
    mIcon.draw(canvas);
    final float baseline = getHeight() / 2f - (mTextPaint.descent() + mTextPaint.ascent()) / 2f;
    canvas.drawText(mLabel, left + mIconSize + mIconGap, baseline, mTextPaint);
  }
}
