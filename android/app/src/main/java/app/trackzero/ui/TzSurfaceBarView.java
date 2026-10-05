package app.trackzero.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import app.organicmaps.R;
import app.trackzero.routes.SurfaceProfile;
import app.trackzero.routes.SurfaceProfile.Surface;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What a route is made of (Components v2 "SurfaceBar", Phase 23): an 8dp bar of its surfaces in route order, each
 * segment as long as its share of the route with 2dp gaps, and under it a legend of the classes present (wrapping).
 */
public class TzSurfaceBarView extends View
{
  private final float mBarH;
  private final float mRadius;
  private final float mGap;
  private final float mLegendTop;
  private final float mSquare;
  private final float mSquareGap;
  private final float mItemGap;
  private final float mRowGap;
  private final int[] mColors = new int[Surface.values().length];
  private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final TextPaint mText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private final RectF mRect = new RectF();

  @Nullable
  private SurfaceProfile mProfile;
  /** The legend items present, with their text. */
  private final List<Surface> mItems = new ArrayList<>();
  private final List<String> mLabels = new ArrayList<>();

  public TzSurfaceBarView(@NonNull Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
    mBarH = getResources().getDimension(R.dimen.tz_sf_bar_h);
    mRadius = getResources().getDimension(R.dimen.tz_sf_bar_r);
    mGap = getResources().getDimension(R.dimen.tz_sf_gap);
    mLegendTop = getResources().getDimension(R.dimen.tz_sf_legend_top);
    mSquare = getResources().getDimension(R.dimen.tz_sf_square);
    mSquareGap = getResources().getDimension(R.dimen.tz_sf_square_gap);
    mItemGap = getResources().getDimension(R.dimen.tz_sf_item_gap);
    mRowGap = getResources().getDimension(R.dimen.tz_sf_row_gap);
    for (Surface s : Surface.values())
      mColors[s.ordinal()] = ContextCompat.getColor(context, color(s));
    mText.setTextSize(getResources().getDimension(R.dimen.tz_text_11));
    mText.setTypeface(ResourcesCompat.getFont(context, R.font.barlow_semibold));
    mText.setLetterSpacing(0.1f);
    mText.setColor(ContextCompat.getColor(context, R.color.tz_text2));
  }

  @ColorRes
  private static int color(@NonNull Surface s)
  {
    return switch (s)
    {
      case PAVED -> R.color.tz_sf_paved;
      case POOR -> R.color.tz_sf_poor;
      case GRAVEL -> R.color.tz_sf_gravel;
      case ROUGH -> R.color.tz_sf_rough;
      case NONE -> R.color.tz_sf_none;
    };
  }

  @StringRes
  public static int label(@NonNull Surface s)
  {
    return switch (s)
    {
      case PAVED -> R.string.tz_sf_paved;
      case POOR -> R.string.tz_sf_poor;
      case GRAVEL -> R.string.tz_sf_gravel;
      case ROUGH -> R.string.tz_sf_rough;
      case NONE -> R.string.tz_sf_none;
    };
  }

  public void setProfile(@Nullable SurfaceProfile p)
  {
    mProfile = p;
    mItems.clear();
    mLabels.clear();
    final StringBuilder cd = new StringBuilder();
    if (p != null)
    {
      final Locale locale = Locale.getDefault();
      for (Surface s : Surface.values())
      {
        final int pct = p.percent[s.ordinal()];
        if (pct <= 0)
          continue;
        final String name = getResources().getString(label(s));
        mItems.add(s);
        mLabels.add(getResources().getString(R.string.tz_sf_legend, name, pct).toUpperCase(locale));
        if (cd.length() > 0)
          cd.append(", ");
        cd.append(getResources().getString(R.string.tz_sf_cd_item, pct, name.toLowerCase(locale)));
      }
    }
    setContentDescription(cd.toString());
    requestLayout();
    invalidate();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
  {
    final int width = MeasureSpec.getSize(widthMeasureSpec);
    final int rows = rows(width - getPaddingLeft() - getPaddingRight());
    final float lineH = lineHeight();
    final float h = mProfile == null ? 0 : mBarH + mLegendTop + rows * lineH + Math.max(0, rows - 1) * mRowGap;
    setMeasuredDimension(width, (int) Math.ceil(h) + getPaddingTop() + getPaddingBottom());
  }

  private float lineHeight()
  {
    final Paint.FontMetrics fm = mText.getFontMetrics();
    return Math.max(mSquare, fm.descent - fm.ascent);
  }

  private float itemWidth(int i)
  {
    return mSquare + mSquareGap + mText.measureText(mLabels.get(i));
  }

  /** Rows the legend takes at this width. */
  private int rows(float width)
  {
    if (mItems.isEmpty())
      return 0;
    int rows = 1;
    float x = 0;
    for (int i = 0; i < mItems.size(); i++)
    {
      final float w = itemWidth(i);
      if (x > 0 && x + w > width)
      {
        rows++;
        x = 0;
      }
      x += w + mItemGap;
    }
    return rows;
  }

  @Override
  protected void onDraw(@NonNull Canvas canvas)
  {
    final SurfaceProfile p = mProfile;
    if (p == null || p.runs.isEmpty())
      return;
    final float left = getPaddingLeft();
    final float width = getWidth() - getPaddingLeft() - getPaddingRight();
    final float top = getPaddingTop();
    final double total = p.runs.get(p.runs.size() - 1).toM - p.runs.get(0).fromM;
    // Segments share the width left after the gaps, by length.
    final float avail = width - mGap * (p.runs.size() - 1);
    float x = left;
    for (int i = 0; i < p.runs.size(); i++)
    {
      final SurfaceProfile.Run r = p.runs.get(i);
      final float w = i == p.runs.size() - 1 ? left + width - x : (float) (avail * r.lengthM() / total);
      mFill.setColor(mColors[r.surface.ordinal()]);
      mRect.set(x, top, x + w, top + mBarH);
      canvas.drawRoundRect(mRect, mRadius, mRadius, mFill);
      x += w + mGap;
    }

    final float lineH = lineHeight();
    final Paint.FontMetrics fm = mText.getFontMetrics();
    float y = top + mBarH + mLegendTop;
    x = left;
    for (int i = 0; i < mItems.size(); i++)
    {
      final float w = itemWidth(i);
      if (x > left && x + w > left + width)
      {
        x = left;
        y += lineH + mRowGap;
      }
      final float mid = y + lineH / 2;
      mFill.setColor(mColors[mItems.get(i).ordinal()]);
      mRect.set(x, mid - mSquare / 2, x + mSquare, mid + mSquare / 2);
      canvas.drawRoundRect(mRect, mSquare / 4, mSquare / 4, mFill);
      canvas.drawText(mLabels.get(i), x + mSquare + mSquareGap, mid - (fm.ascent + fm.descent) / 2, mText);
      x += w + mItemGap;
    }
  }
}
