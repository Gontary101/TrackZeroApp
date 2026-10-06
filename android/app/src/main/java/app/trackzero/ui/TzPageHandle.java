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
import app.organicmaps.R;
import java.util.Arrays;

/**
 * Ride carousel handle: grabber + exactly 3 page dots; the active dot is a 24dp mint pill. {@link #showDotsOnly}
 * makes it the tutorial's dots (Phase 37): no grabber, any number of pages, from the start edge.
 */
public class TzPageHandle extends View
{
  public static final int PAGES = 3;

  private final Paint mGrabber = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint mDot = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint mActive = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF mRect = new RectF();
  private final float mGrabberW;
  private final float mGrabberH;
  private final float mDotSize;
  private final float mActiveW;
  private final float mDotGap;
  private final float mRowGap;
  /** Each dot's current "activeness", 0..1, animated so widths change smoothly. */
  private float[] mWeights = new float[PAGES];
  private int mPages = PAGES;
  private boolean mDotsOnly;
  private int mPage;
  @Nullable
  private ValueAnimator mAnimator;

  public TzPageHandle(@NonNull Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
    mGrabber.setColor(ContextCompat.getColor(context, R.color.tz_grabber));
    mDot.setColor(ContextCompat.getColor(context, R.color.tz_text3));
    mActive.setColor(ContextCompat.getColor(context, R.color.tz_mint));
    mGrabberW = getResources().getDimension(R.dimen.tz_grabber_w);
    mGrabberH = getResources().getDimension(R.dimen.tz_grabber_h);
    mDotSize = getResources().getDimension(R.dimen.tz_dot);
    mActiveW = getResources().getDimension(R.dimen.tz_dot_active_w);
    mDotGap = getResources().getDimension(R.dimen.tz_dot_gap);
    mRowGap = getResources().getDimension(R.dimen.tz_handle_row_gap);
    mWeights[0] = 1f;
    updateDescription();
  }

  /** Only the dots, {@code pages} of them, from the start edge, the first one active. */
  public void showDotsOnly(int pages)
  {
    mDotsOnly = true;
    mPages = pages;
    mWeights = new float[pages];
    mWeights[0] = 1f;
    mPage = 0;
    updateDescription();
    requestLayout();
  }

  public void setPage(int page, boolean animate)
  {
    if (page == mPage)
      return;
    mPage = page;
    updateDescription();
    if (mAnimator != null)
      mAnimator.cancel();
    if (!animate || !TzMotion.enabled(getContext()))
    {
      Arrays.fill(mWeights, 0f);
      mWeights[page] = 1f;
      invalidate();
      return;
    }
    final float[] start = mWeights.clone();
    mAnimator = ValueAnimator.ofFloat(0f, 1f);
    mAnimator.setDuration(TzMotion.DOT_MS);
    mAnimator.setInterpolator(TzMotion.EASE);
    mAnimator.addUpdateListener(a -> {
      final float t = (float) a.getAnimatedValue();
      for (int i = 0; i < mPages; i++)
        mWeights[i] = start[i] + ((i == page ? 1f : 0f) - start[i]) * t;
      invalidate();
    });
    mAnimator.start();
  }

  private void updateDescription()
  {
    setContentDescription(getResources().getString(mDotsOnly ? R.string.tz_cd_tutorial_step : R.string.tz_cd_page_handle,
                                                   mPage + 1, mPages));
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
  {
    setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                         mDotsOnly ? Math.round(mDotSize) : getResources().getDimensionPixelSize(R.dimen.tz_handle_h));
  }

  @Override
  protected void onDraw(@NonNull Canvas canvas)
  {
    final float cx = getWidth() / 2f;
    float rowW = mDotGap * (mPages - 1);
    for (float w : mWeights)
      rowW += mDotSize + (mActiveW - mDotSize) * w;
    float x;
    final float dotTop;
    if (mDotsOnly)
    {
      x = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? getWidth() - rowW : 0;
      dotTop = (getHeight() - mDotSize) / 2f;
    }
    else
    {
      final float contentH = mGrabberH + mRowGap + mDotSize;
      final float top = (getHeight() - contentH) / 2f;
      mRect.set(cx - mGrabberW / 2f, top, cx + mGrabberW / 2f, top + mGrabberH);
      canvas.drawRoundRect(mRect, mGrabberH / 2f, mGrabberH / 2f, mGrabber);
      x = cx - rowW / 2f;
      dotTop = top + mGrabberH + mRowGap;
    }
    final float r = mDotSize / 2f;
    for (int i = 0; i < mPages; i++)
    {
      final float w = mDotSize + (mActiveW - mDotSize) * mWeights[i];
      mRect.set(x, dotTop, x + w, dotTop + mDotSize);
      mActive.setAlpha(Math.round(255 * mWeights[i]));
      canvas.drawRoundRect(mRect, r, r, mDot);
      canvas.drawRoundRect(mRect, r, r, mActive);
      x += w + mDotGap;
    }
  }
}
