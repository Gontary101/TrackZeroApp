package app.trackzero.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
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

  public TzMetricView(@NonNull Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
    final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.TzMetricView);
    final int style = a.getResourceId(R.styleable.TzMetricView_tzNumberStyle, R.style.TzText_MetricM);
    final float sp = a.getFloat(R.styleable.TzMetricView_tzNumberSize, 32f);
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
  }

  /** Overrides the default half-size unit (the turn card uses 28sp under a 64sp number). */
  public void setUnitSize(float sp)
  {
    TzTextSize.applyCapped(mUnit, sp);
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

  @NonNull
  public TextView numberView()
  {
    return mNumber;
  }
}
