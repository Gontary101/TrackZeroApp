package app.trackzero.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.R;

/** Ride bottom card: three label/value cells separated by hairlines. */
public class TzStatsIsland extends LinearLayout
{
  public static final int CELLS = 3;

  private final TextView[] mLabels = new TextView[CELLS];
  private final TzMetricView[] mValues = new TzMetricView[CELLS];

  public TzStatsIsland(@NonNull Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
    setOrientation(HORIZONTAL);
    setGravity(Gravity.CENTER_VERTICAL);
    setBackgroundResource(R.drawable.tz_bg_surface2_r32);
    setElevation(getResources().getDimension(R.dimen.tz_elev2));
    final int pad = getResources().getDimensionPixelSize(R.dimen.tz_space_8);
    setPadding(pad, 0, pad, 0);
    setMinimumHeight(getResources().getDimensionPixelSize(R.dimen.tz_stats_island_h));

    for (int i = 0; i < CELLS; i++)
    {
      if (i > 0)
      {
        final View divider = new View(context);
        divider.setBackgroundResource(R.color.tz_divider);
        addView(divider, new LayoutParams(getResources().getDimensionPixelSize(R.dimen.tz_divider),
                                          getResources().getDimensionPixelSize(R.dimen.tz_stat_divider_h)));
      }
      final LinearLayout cell = new LinearLayout(context);
      cell.setOrientation(VERTICAL);
      cell.setGravity(Gravity.CENTER_HORIZONTAL);
      mLabels[i] = new TextView(context, null, 0, R.style.TzText_Label);
      // A third of the card each: the labels keep to one line like the numbers (capped font scale).
      mLabels[i].setMaxLines(1);
      TzTextSize.capCurrent(mLabels[i]);
      cell.addView(mLabels[i], new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
      mValues[i] = new TzMetricView(context, R.style.TzText_MetricM, 32f);
      final LayoutParams vlp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
      vlp.topMargin = getResources().getDimensionPixelSize(R.dimen.tz_label_gap);
      cell.addView(mValues[i], vlp);
      addView(cell, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
    }
  }

  public void setCell(int i, @NonNull CharSequence label, @NonNull CharSequence value, @NonNull CharSequence unit)
  {
    TzViews.setText(mLabels[i], label);
    mValues[i].setValue(value, unit);
  }
}
