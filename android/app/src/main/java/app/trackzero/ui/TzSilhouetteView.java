package app.trackzero.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import app.organicmaps.R;
import app.trackzero.geo.DouglasPeucker;

public final class TzSilhouetteView extends View
{
  private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint mStartFill = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint mStartRing = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path mPath = new Path();
  /** Start ring radius; 0 = no start marker (route cards). */
  private float mStartRadius;
  private float mStartX;
  private float mStartY;
  @Nullable private double[] mX;
  @Nullable private double[] mY;
  private float mBoxSize;

  public TzSilhouetteView(@NonNull Context context)
  {
    this(context, null);
  }

  public TzSilhouetteView(@NonNull Context context, @Nullable AttributeSet attrs)
  {
    this(context, attrs, 0);
  }

  public TzSilhouetteView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr)
  {
    super(context, attrs, defStyleAttr);
    mBoxSize = getResources().getDimension(R.dimen.tz_silhouette_box);
    mPaint.setColor(ContextCompat.getColor(context, R.color.tz_mint));
    mPaint.setStrokeWidth(getResources().getDimension(R.dimen.tz_silhouette_stroke));
    mPaint.setStyle(Paint.Style.STROKE);
    mPaint.setStrokeCap(Paint.Cap.ROUND);
    mPaint.setStrokeJoin(Paint.Join.ROUND);
    mStartFill.setColor(ContextCompat.getColor(context, R.color.tz_bg));
    mStartRing.setColor(ContextCompat.getColor(context, R.color.tz_mint));
    mStartRing.setStyle(Paint.Style.STROKE);
  }

  public void setPoints(@Nullable double[] lat, @Nullable double[] lon)
  {
    mX = null;
    mY = null;
    if (lat != null && lon != null && lat.length >= 2 && lat.length == lon.length)
    {
      int[] kept = DouglasPeucker.simplifyToMaxPoints(lat, lon, 200);
      double meanLat = 0;
      for (double value : lat)
      {
        meanLat += value;
      }
      double lonScale = Math.cos(Math.toRadians(meanLat / lat.length));
      mX = new double[kept.length];
      mY = new double[kept.length];
      for (int i = 0; i < kept.length; i++)
      {
        mX[i] = lon[kept[i]] * lonScale;
        mY[i] = -lat[kept[i]];
      }
    }
    rebuildPath();
  }

  public void setStrokeWidthPx(float width)
  {
    mPaint.setStrokeWidth(width);
    rebuildPath();
  }

  /** A black disc with a mint ring at the first point (R8 ride thumbnail). */
  public void setStartMarker(float radiusPx, float strokePx)
  {
    mStartRadius = radiusPx;
    mStartRing.setStrokeWidth(strokePx);
    invalidate();
  }

  public void setBoxSizePx(float size)
  {
    mBoxSize = size;
    rebuildPath();
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh)
  {
    super.onSizeChanged(w, h, oldw, oldh);
    rebuildPath();
  }

  private void rebuildPath()
  {
    mPath.rewind();
    if (mX != null && mY != null && getWidth() > 0 && getHeight() > 0)
    {
      double minX = mX[0];
      double maxX = mX[0];
      double minY = mY[0];
      double maxY = mY[0];
      for (int i = 1; i < mX.length; i++)
      {
        minX = Math.min(minX, mX[i]);
        maxX = Math.max(maxX, mX[i]);
        minY = Math.min(minY, mY[i]);
        maxY = Math.max(maxY, mY[i]);
      }
      double span = Math.max(maxX - minX, maxY - minY);
      float box = Math.max(0, Math.min(mBoxSize,
          Math.min(getWidth(), getHeight()) - mPaint.getStrokeWidth()));
      double scale = span == 0 ? 0 : box / span;
      double centerX = (minX + maxX) / 2;
      double centerY = (minY + maxY) / 2;
      for (int i = 0; i < mX.length; i++)
      {
        float x = (float) ((mX[i] - centerX) * scale + getWidth() / 2f);
        float y = (float) ((mY[i] - centerY) * scale + getHeight() / 2f);
        if (i == 0)
        {
          mPath.moveTo(x, y);
          mStartX = x;
          mStartY = y;
        }
        else
        {
          mPath.lineTo(x, y);
        }
      }
    }
    invalidate();
  }

  @Override
  protected void onDraw(@NonNull Canvas canvas)
  {
    super.onDraw(canvas);
    canvas.drawPath(mPath, mPaint);
    if (mStartRadius > 0 && !mPath.isEmpty())
    {
      canvas.drawCircle(mStartX, mStartY, mStartRadius, mStartFill);
      canvas.drawCircle(mStartX, mStartY, mStartRadius, mStartRing);
    }
  }
}
