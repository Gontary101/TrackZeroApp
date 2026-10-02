package app.trackzero.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Paint;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import app.organicmaps.R;
import app.trackzero.format.TzFormat;

/** A number with its unit in a separate, half-size {@code text2} TextView, sharing the baseline (plan §X3). */
public class TzMetricView extends LinearLayout
{
  @NonNull
  private final TextView mNumber;
  @NonNull
  private final TextView mUnit;
  /** Shrink to the width given (R5's three columns: "14.9 km", "+3,281 ft"). */
  private boolean mFitWidth;
  /** The sizes asked for, px; with {@link #mFitWidth} the views may be smaller. */
  private float mNumberPx;
  private float mUnitPx;
  @Nullable
  private TextPaint mScratch;
  /** Moves shrunk numbers down onto the baseline they have at full size, px. */
  private int mBaselineShift;

  public TzMetricView(@NonNull Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
    final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.TzMetricView);
    final int style = a.getResourceId(R.styleable.TzMetricView_tzNumberStyle, R.style.TzText_MetricM);
    final float sp = a.getFloat(R.styleable.TzMetricView_tzNumberSize, 32f);
    mFitWidth = a.getBoolean(R.styleable.TzMetricView_tzFitWidth, false);
    a.recycle();
    mNumber = new TextView(context, null, 0, style);
    mUnit = new TextView(context, null, 0, R.style.TzText_Unit);
    init(sp);
  }

  public TzMetricView(@NonNull Context context, @StyleRes int numberStyle, float numberSp)
  {
    super(context);
    mNumber = new TextView(context, null, 0, numberStyle);
    mUnit = new TextView(context, null, 0, R.style.TzText_Unit);
    init(numberSp);
  }

  private void init(float numberSp)
  {
    setOrientation(HORIZONTAL);
    setBaselineAligned(true);
    mNumber.setMaxLines(1);
    mUnit.setMaxLines(1);
    addView(mNumber, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    final LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    lp.setMarginStart(getResources().getDimensionPixelSize(R.dimen.tz_unit_gap));
    addView(mUnit, lp);
    setNumberSize(numberSp);
  }

  /** Number size in sp (capped at 1.3x font scale); the unit is half of it. */
  public void setNumberSize(float sp)
  {
    TzTextSize.applyCapped(mNumber, sp);
    TzTextSize.applyCapped(mUnit, sp / 2);
    mNumberPx = mNumber.getTextSize();
    mUnitPx = mUnit.getTextSize();
  }

  /** Overrides the default half-size unit (the turn card uses 28sp under a 64sp number). */
  public void setUnitSize(float sp)
  {
    TzTextSize.applyCapped(mUnit, sp);
    mUnitPx = mUnit.getTextSize();
  }

  public void setUnitGap(int px)
  {
    ((LayoutParams) mUnit.getLayoutParams()).setMarginStart(px);
    mUnit.requestLayout();
  }

  public void setValue(@NonNull TzFormat.Value value)
  {
    setValue(value.number, value.unit);
  }

  public void setValue(@NonNull CharSequence number, @NonNull CharSequence unit)
  {
    mNumber.setText(number);
    mUnit.setText(unit);
    mUnit.setVisibility(unit.length() == 0 ? GONE : VISIBLE);
  }

  public void setNumberColor(int color)
  {
    mNumber.setTextColor(color);
  }

  public void setUnitColor(int color)
  {
    mUnit.setTextColor(color);
  }

  @Override
  protected void onMeasure(int widthSpec, int heightSpec)
  {
    if (!mFitWidth || MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED)
    {
      super.onMeasure(widthSpec, heightSpec);
      return;
    }
    // Sized from the paint, not by measuring at full size first: changing the text size in a measure pass
    // would ask for another layout every time.
    final boolean unit = mUnit.getVisibility() != GONE;
    final float number = textWidth(mNumber, mNumberPx);
    final float units = unit ? textWidth(mUnit, mUnitPx) : 0;
    final int fixed = getPaddingLeft() + getPaddingRight() + mNumber.getTotalPaddingLeft()
                    + mNumber.getTotalPaddingRight()
                    + (unit ? ((LayoutParams) mUnit.getLayoutParams()).getMarginStart()
                                  + mUnit.getTotalPaddingLeft() + mUnit.getTotalPaddingRight()
                            : 0);
    final float room = MeasureSpec.getSize(widthSpec) - fixed;
    final float scale = number + units <= room ? 1f : Math.max(0f, room) / (number + units);
    setPx(mNumber, mNumberPx * scale);
    setPx(mUnit, mUnitPx * scale);
    super.onMeasure(widthSpec, heightSpec);
    mBaselineShift = 0;
    if (scale < 1f)
    {
      // As tall as at full size, with the baseline where it is at full size: a shrunk value lines up with its
      // neighbours and does not move what is below.
      final Paint.FontMetricsInt full = new Paint.FontMetricsInt();
      scratch(mNumber, mNumberPx).getFontMetricsInt(full);
      final Paint.FontMetricsInt shrunk = mNumber.getPaint().getFontMetricsInt();
      mBaselineShift = shrunk.top - full.top;
      final int height = getPaddingTop() + getPaddingBottom() + mNumber.getTotalPaddingTop()
                       + mNumber.getTotalPaddingBottom() + full.bottom - full.top;
      setMeasuredDimension(getMeasuredWidth(), resolveSize(Math.max(getMeasuredHeight(), height), heightSpec));
    }
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b)
  {
    super.onLayout(changed, l, t, r, b);
    if (mBaselineShift != 0)
      for (int i = 0; i < getChildCount(); i++)
        getChildAt(i).offsetTopAndBottom(mBaselineShift);
  }

  @Override
  public int getBaseline()
  {
    final int baseline = super.getBaseline();
    return baseline < 0 ? baseline : baseline + mBaselineShift;
  }

  private float textWidth(@NonNull TextView tv, float px)
  {
    // Rounded up as TextView does, plus a pixel so the last glyph is never clipped.
    return (float) Math.ceil(scratch(tv, px).measureText(tv.getText().toString())) + 1;
  }

  @NonNull
  private TextPaint scratch(@NonNull TextView tv, float px)
  {
    if (mScratch == null)
      mScratch = new TextPaint();
    mScratch.set(tv.getPaint());
    mScratch.setTextSize(px);
    return mScratch;
  }

  private static void setPx(@NonNull TextView tv, float px)
  {
    if (Math.abs(tv.getTextSize() - px) > 0.5f)
      tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
  }

  @NonNull
  public TextView numberView()
  {
    return mNumber;
  }
}
