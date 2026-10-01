package app.trackzero.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import app.organicmaps.R;

public final class TzElevationProfileView extends View
{
  private static final float GRID_DP = 1;
  private static final float RIDDEN_STROKE_DP = 3.5f;
  private static final float AHEAD_STROKE_DP = 3;
  private static final float DASH_ON_DP = 2;
  private static final float DASH_OFF_DP = 6;
  private static final float RIDER_DASH_ON_DP = 3;
  private static final float RIDER_DASH_OFF_DP = 4;
  private static final float DOT_RADIUS_DP = 7;
  private static final float DOT_BORDER_DP = 3;
  private static final float HALO_RADIUS_DP = 16;
  private static final float FLAG_SIZE_DP = 24;
  private static final float COMPACT_STROKE_DP = 2;
  private static final float POINT_SPACING_PX = 2;
  private static final long RANGE_DURATION_MS = 300;

  private final Paint mGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint mRiddenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint mAheadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint mRiderLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint mDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint mDotBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint mHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint mCompactPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint mAxisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint mSummitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path mCurve = new Path();
  private final Path mArea = new Path();
  private final RectF mPlot = new RectF();
  private final FastOutSlowInInterpolator mRangeInterpolator = new FastOutSlowInInterpolator();
  @Nullable private final Drawable mFlag;
  private final int mMint;
  private final float mDotRadius;
  private final float mHaloRadius;
  private final float mFlagSize;
  private final float mGap;
  private final float mAxisHeight;
  private final float mSummitHeight;

  @Nullable private float[] mDist;
  @Nullable private float[] mAlt;
  private float[] mX = new float[0];
  private float[] mY = new float[0];
  private int mPointCount;
  private float mFrom;
  private float mTo;
  private float mMinAlt;
  private float mAltSpan;
  private float mRiderDistance = Float.NaN;
  private float mSummitDistance = Float.NaN;
  private float mSummitAltitude = Float.NaN;
  @Nullable private String mSummitLabel;
  @Nullable private String mStartLabel;
  @Nullable private String mEndLabel;
  @Nullable private ValueAnimator mRangeAnimator;
  private boolean mCompact;
  private boolean mShowSummit;
  private float mSummitLabelX;
  private float mSummitLabelY;

  public TzElevationProfileView(@NonNull Context context)
  {
    this(context, null);
  }

  public TzElevationProfileView(@NonNull Context context, @Nullable AttributeSet attrs)
  {
    this(context, attrs, 0);
  }

  public TzElevationProfileView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr)
  {
    super(context, attrs, defStyleAttr);
    final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.TzElevationProfileView);
    mCompact = a.getBoolean(R.styleable.TzElevationProfileView_tzCompact, false);
    a.recycle();
    mMint = ContextCompat.getColor(context, R.color.tz_mint);
    mDotRadius = dp(DOT_RADIUS_DP);
    mHaloRadius = dp(HALO_RADIUS_DP);
    mFlagSize = dp(FLAG_SIZE_DP);
    mGap = dp(DOT_BORDER_DP);
    setStroke(mGridPaint, ContextCompat.getColor(context, R.color.tz_grid), dp(GRID_DP));
    setStroke(mRiddenPaint, mMint, dp(RIDDEN_STROKE_DP));
    setStroke(mAheadPaint, withAlpha(mMint, 0.5f), dp(AHEAD_STROKE_DP));
    mAheadPaint.setPathEffect(new DashPathEffect(new float[] {dp(DASH_ON_DP), dp(DASH_OFF_DP)}, 0));
    setStroke(mRiderLinePaint, withAlpha(mMint, 0.35f), dp(GRID_DP));
    mRiderLinePaint.setPathEffect(new DashPathEffect(
        new float[] {dp(RIDER_DASH_ON_DP), dp(RIDER_DASH_OFF_DP)}, 0));
    setStroke(mCompactPaint, mMint, dp(COMPACT_STROKE_DP));
    mDotPaint.setColor(mMint);
    setStroke(mDotBorderPaint, ContextCompat.getColor(context, R.color.tz_bg), mGap);
    mHaloPaint.setColor(withAlpha(mMint, 0.18f));
    mAxisPaint.setColor(ContextCompat.getColor(context, R.color.tz_text2));
    mAxisPaint.setTextSize(getResources().getDimension(R.dimen.tz_text_11));
    mAxisPaint.setTypeface(ResourcesCompat.getFont(context, R.font.barlow_semibold));
    mSummitPaint.setColor(ContextCompat.getColor(context, R.color.tz_text1));
    mSummitPaint.setTextSize(getResources().getDimension(R.dimen.tz_text_13));
    mSummitPaint.setTypeface(ResourcesCompat.getFont(context, R.font.barlow_condensed_semibold));
    mAxisHeight = mAxisPaint.descent() - mAxisPaint.ascent();
    mSummitHeight = mSummitPaint.descent() - mSummitPaint.ascent();
    Drawable flag = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_tz_flag, context.getTheme());
    mFlag = flag == null ? null : flag.mutate();
    if (mFlag != null)
    {
      mFlag.setTint(ContextCompat.getColor(context, R.color.tz_text1));
    }
  }

  private float dp(float value)
  {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
  }

  private static int withAlpha(int color, float alpha)
  {
    return Color.argb(Math.round(Color.alpha(color) * alpha), Color.red(color), Color.green(color), Color.blue(color));
  }

  private static boolean isFinite(float value)
  {
    return !Float.isNaN(value) && !Float.isInfinite(value);
  }

  private static void setStroke(Paint paint, int color, float width)
  {
    paint.setColor(color);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(width);
    paint.setStrokeCap(Paint.Cap.ROUND);
    paint.setStrokeJoin(Paint.Join.ROUND);
  }

  public void setProfile(@Nullable float[] distM, @Nullable float[] altM)
  {
    cancelRangeAnimation();
    if (distM != null && altM != null && distM.length != altM.length)
    {
      throw new IllegalArgumentException("Profile arrays must have equal lengths");
    }
    mDist = distM == null || altM == null ? null : distM.clone();
    mAlt = mDist == null ? null : altM.clone();
    if (mDist != null && mDist.length > 0)
    {
      mFrom = mDist[0];
      mTo = mDist[mDist.length - 1];
    }
    rebuildPaths();
  }

  public void setRiderDistance(float distM)
  {
    mRiderDistance = distM;
    invalidate();
  }

  public void setSummit(float distM, float altM, @Nullable String label)
  {
    mSummitDistance = distM;
    mSummitAltitude = altM;
    mSummitLabel = label;
    updateSummit();
    invalidate();
  }

  public void setAxisLabels(@Nullable String start, @Nullable String end)
  {
    mStartLabel = start;
    mEndLabel = end;
    invalidate();
  }

  public void setRange(float fromM, float toM, boolean animate)
  {
    if (!isFinite(fromM) || !isFinite(toM) || fromM >= toM)
    {
      throw new IllegalArgumentException("Visible range must be finite and increasing");
    }
    cancelRangeAnimation();
    if (mDist != null && mDist.length >= 2)
    {
      fromM = Math.max(mDist[0], fromM);
      toM = Math.min(mDist[mDist.length - 1], toM);
      if (fromM >= toM)
      {
        return;
      }
    }
    if (fromM == mFrom && toM == mTo)
    {
      return;
    }
    if (!animate || mTo <= mFrom || !TzMotion.enabled(getContext()))
    {
      mFrom = fromM;
      mTo = toM;
      rebuildPaths();
      return;
    }
    final float startFrom = mFrom;
    final float startTo = mTo;
    final float targetFrom = fromM;
    final float targetTo = toM;
    mRangeAnimator = ValueAnimator.ofFloat(0, 1);
    mRangeAnimator.setDuration(RANGE_DURATION_MS);
    mRangeAnimator.setInterpolator(mRangeInterpolator);
    mRangeAnimator.addUpdateListener(animator ->
    {
      float fraction = animator.getAnimatedFraction();
      mFrom = startFrom + (targetFrom - startFrom) * fraction;
      mTo = startTo + (targetTo - startTo) * fraction;
      rebuildPaths();
    });
    mRangeAnimator.start();
  }

  public void setCompact(boolean compact)
  {
    if (mCompact == compact)
    {
      return;
    }
    mCompact = compact;
    rebuildPaths();
  }

  private void cancelRangeAnimation()
  {
    if (mRangeAnimator != null)
    {
      mRangeAnimator.cancel();
      mRangeAnimator = null;
    }
  }

  @Override
  protected void onDetachedFromWindow()
  {
    cancelRangeAnimation();
    super.onDetachedFromWindow();
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh)
  {
    super.onSizeChanged(w, h, oldw, oldh);
    rebuildPaths();
  }

  private void rebuildPaths()
  {
    mCurve.rewind();
    mArea.rewind();
    mPointCount = 0;
    mShowSummit = false;
    float side = mCompact ? mCompactPaint.getStrokeWidth() / 2 : Math.max(mHaloRadius, mFlagSize * 3 / 4);
    float top = mCompact ? side : mFlagSize + mSummitHeight + mGap;
    float bottom = mCompact ? side : mHaloRadius + mAxisHeight + mGap;
    mPlot.set(getPaddingLeft() + side, getPaddingTop() + top,
        getWidth() - getPaddingRight() - side, getHeight() - getPaddingBottom() - bottom);
    if (mDist == null || mAlt == null || mDist.length < 2 || mTo <= mFrom
        || mPlot.width() <= 0 || mPlot.height() <= 0)
    {
      invalidate();
      return;
    }

    float firstAlt = altitudeAt(mFrom);
    float lastAlt = altitudeAt(mTo);
    float min = Math.min(firstAlt, lastAlt);
    float max = Math.max(firstAlt, lastAlt);
    int first = upperBound(mFrom);
    int end = upperBound(mTo);
    for (int i = first; i < end; i++)
    {
      min = Math.min(min, mAlt[i]);
      max = Math.max(max, mAlt[i]);
    }
    float span = Math.max(1, max - min);
    mMinAlt = (min + max) / 2 - span * 0.6f;
    mAltSpan = span * 1.2f;

    int capacity = Math.max(2, (int) (mPlot.width() / POINT_SPACING_PX));
    capacity = Math.min(capacity, mDist.length + 2);
    if (mX.length < capacity)
    {
      mX = new float[capacity];
      mY = new float[capacity];
    }
    float spacing = Math.max(POINT_SPACING_PX, mPlot.width() / (capacity - 1));
    addPoint(mPlot.left, yForAltitude(firstAlt));
    for (int i = first; i < end && mPointCount < capacity - 1; i++)
    {
      float x = xForDistance(mDist[i]);
      if (x - mX[mPointCount - 1] >= spacing && mPlot.right - x >= spacing)
      {
        addPoint(x, yForAltitude(mAlt[i]));
      }
    }
    addPoint(mPlot.right, yForAltitude(lastAlt));
    mCurve.moveTo(mX[0], mY[0]);
    for (int i = 0; i < mPointCount - 1; i++)
    {
      int previous = Math.max(0, i - 1);
      int next = Math.min(mPointCount - 1, i + 2);
      mCurve.cubicTo(mX[i] + (mX[i + 1] - mX[previous]) / 6,
          mY[i] + (mY[i + 1] - mY[previous]) / 6,
          mX[i + 1] - (mX[next] - mX[i]) / 6,
          mY[i + 1] - (mY[next] - mY[i]) / 6, mX[i + 1], mY[i + 1]);
    }
    mArea.set(mCurve);
    mArea.lineTo(mPlot.right, mPlot.bottom);
    mArea.lineTo(mPlot.left, mPlot.bottom);
    mArea.close();
    mFillPaint.setShader(mCompact ? null : new LinearGradient(0, mPlot.top, 0, mPlot.bottom,
        withAlpha(mMint, 0.38f), withAlpha(mMint, 0.02f), Shader.TileMode.CLAMP));
    mFillPaint.setColor(mCompact ? withAlpha(mMint, 0.14f) : mMint);
    updateSummit();
    invalidate();
  }

  private void addPoint(float x, float y)
  {
    mX[mPointCount] = x;
    mY[mPointCount++] = y;
  }

  private int upperBound(float distance)
  {
    int low = 0;
    int high = mDist.length;
    while (low < high)
    {
      int mid = (low + high) >>> 1;
      if (mDist[mid] <= distance)
      {
        low = mid + 1;
      }
      else
      {
        high = mid;
      }
    }
    return low;
  }

  private float altitudeAt(float distance)
  {
    int next = upperBound(distance);
    if (next == 0)
    {
      return mAlt[0];
    }
    if (next == mDist.length)
    {
      return mAlt[next - 1];
    }
    float fraction = (distance - mDist[next - 1]) / (mDist[next] - mDist[next - 1]);
    return mAlt[next - 1] + fraction * (mAlt[next] - mAlt[next - 1]);
  }

  private float xForDistance(float distance)
  {
    return mPlot.left + (distance - mFrom) / (mTo - mFrom) * mPlot.width();
  }

  private float yForAltitude(float altitude)
  {
    return mPlot.bottom - (altitude - mMinAlt) / mAltSpan * mPlot.height();
  }

  private void updateSummit()
  {
    mShowSummit = !mCompact && mPointCount >= 2 && isFinite(mSummitDistance)
        && isFinite(mSummitAltitude) && mSummitDistance >= mFrom && mSummitDistance <= mTo;
    if (!mShowSummit)
    {
      return;
    }
    float x = xForDistance(mSummitDistance);
    float y = Math.max(mPlot.top, Math.min(mPlot.bottom, yForAltitude(mSummitAltitude)));
    if (mFlag != null)
    {
      // The vector's pole runs from (6, 4) to (6, 21) in a 24-unit viewport.
      int left = Math.round(x - mFlagSize * 6 / 24);
      int top = Math.round(y - mFlagSize * 21 / 24);
      mFlag.setBounds(left, top, left + Math.round(mFlagSize), top + Math.round(mFlagSize));
    }
    float labelWidth = mSummitLabel == null ? 0 : mSummitPaint.measureText(mSummitLabel);
    mSummitLabelX = Math.max(getPaddingLeft(), x - mGap - labelWidth);
    mSummitLabelX = Math.min(mSummitLabelX, Math.max(getPaddingLeft(), getWidth() - getPaddingRight() - labelWidth));
    mSummitLabelY = y - mFlagSize - mGap - mSummitPaint.descent();
  }

  private static float cubic(float a, float b, float c, float d, float t)
  {
    float u = 1 - t;
    return u * u * u * a + 3 * u * u * t * b + 3 * u * t * t * c + t * t * t * d;
  }

  private float curveY(float x)
  {
    int low = 0;
    int high = mPointCount - 1;
    while (high - low > 1)
    {
      int mid = (low + high) >>> 1;
      if (mX[mid] <= x)
      {
        low = mid;
      }
      else
      {
        high = mid;
      }
    }
    int previous = Math.max(0, low - 1);
    int next = Math.min(mPointCount - 1, high + 1);
    float c1x = mX[low] + (mX[high] - mX[previous]) / 6;
    float c2x = mX[high] - (mX[next] - mX[low]) / 6;
    float from = 0;
    float to = 1;
    // Invert the cached cubic's x coordinate so the dot lies on the drawn curve.
    for (int i = 0; i < 16; i++)
    {
      float t = (from + to) / 2;
      if (cubic(mX[low], c1x, c2x, mX[high], t) < x)
      {
        from = t;
      }
      else
      {
        to = t;
      }
    }
    return cubic(mY[low], mY[low] + (mY[high] - mY[previous]) / 6,
        mY[high] - (mY[next] - mY[low]) / 6, mY[high], (from + to) / 2);
  }

  @Override
  protected void onDraw(@NonNull Canvas canvas)
  {
    super.onDraw(canvas);
    if (mPointCount < 2)
    {
      return;
    }
    if (!mCompact)
    {
      for (int i = 1; i <= 3; i++)
      {
        float y = mPlot.top + mPlot.height() * i / 4;
        canvas.drawLine(mPlot.left, y, mPlot.right, y, mGridPaint);
      }
    }
    canvas.drawPath(mArea, mFillPaint);
    if (mCompact)
    {
      canvas.drawPath(mCurve, mCompactPaint);
      return;
    }
    if (Float.isNaN(mRiderDistance))
    {
      canvas.drawPath(mCurve, mRiddenPaint);
    }
    else
    {
      float x = Math.max(mPlot.left, Math.min(mPlot.right, xForDistance(mRiderDistance)));
      int saved = canvas.save();
      canvas.clipRect(0, 0, x, getHeight());
      canvas.drawPath(mCurve, mRiddenPaint);
      canvas.restoreToCount(saved);
      saved = canvas.save();
      canvas.clipRect(x, 0, getWidth(), getHeight());
      canvas.drawPath(mCurve, mAheadPaint);
      canvas.restoreToCount(saved);
      if (mRiderDistance >= mFrom && mRiderDistance <= mTo)
      {
        float y = curveY(x);
        canvas.drawLine(x, y, x, mPlot.bottom, mRiderLinePaint);
        canvas.drawCircle(x, y, mHaloRadius, mHaloPaint);
        canvas.drawCircle(x, y, mDotRadius, mDotPaint);
        canvas.drawCircle(x, y, mDotRadius, mDotBorderPaint);
      }
    }
    if (mShowSummit)
    {
      if (mFlag != null)
      {
        mFlag.draw(canvas);
      }
      if (mSummitLabel != null)
      {
        canvas.drawText(mSummitLabel, mSummitLabelX, mSummitLabelY, mSummitPaint);
      }
    }
    float baseline = getHeight() - getPaddingBottom() - mAxisPaint.descent();
    if (mStartLabel != null)
    {
      canvas.drawText(mStartLabel, mPlot.left, baseline, mAxisPaint);
    }
    if (mEndLabel != null)
    {
      canvas.drawText(mEndLabel, mPlot.right - mAxisPaint.measureText(mEndLabel), baseline, mAxisPaint);
    }
  }
}
