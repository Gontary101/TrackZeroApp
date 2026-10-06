package app.trackzero.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import app.organicmaps.R;

/**
 * T4's mini map (Phase 37): four streets on the well, a pin that drops where a finger pressed and the route drawn to
 * it from the start dot. {@link #setProgress}: 0 the empty map, 1 the board's picture (the resting frame). The
 * geometry is the board's, in its 190 × 300 units, scaled to the view's width and clipped to its rounded frame.
 */
public class TzTutorialMapView extends View
{
  private static final float UNITS_W = 190f;
  /** The pin drops over the first part, then the line draws. */
  private static final float DROP_PART = 0.3f;
  private static final float[][] ROADS = {{-10, 90, 200, 70}, {-10, 190, 200, 170}, {60, -10, 80, 310},
                                          {140, -10, 150, 310}};
  private static final float[] LINE = {40, 260, 72, 182, 146, 172, 138, 86};

  private final Paint mBg = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint mEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint mRoad = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint mLine = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint mRing = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint mHalo = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path mClip = new Path();
  private final Path mRoute = new Path();
  private final Path mDrawn = new Path();
  private final PathMeasure mMeasure = new PathMeasure();
  private final RectF mRect = new RectF();
  private final float mRadius;
  private final float mEdgeW;
  private final float mStartR;
  private final float mPinR;
  private final float mHaloR;
  private final float mDrop;
  private float mScale = 1f;
  private float mProgress = 1f;

  public TzTutorialMapView(@NonNull Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
    mRadius = getResources().getDimension(R.dimen.tz_radius_feature);
    mEdgeW = getResources().getDimension(R.dimen.tz_tut_frame_stroke);
    mStartR = getResources().getDimension(R.dimen.tz_tut_start_r);
    mPinR = getResources().getDimension(R.dimen.tz_tut_pin_r);
    mHaloR = getResources().getDimension(R.dimen.tz_tut_pin_halo_r);
    mDrop = getResources().getDimension(R.dimen.tz_tut_pin_drop);
    final int well = ContextCompat.getColor(context, R.color.tz_well);
    final int mint = ContextCompat.getColor(context, R.color.tz_mint);
    mBg.setColor(well);
    mEdge.setColor(ContextCompat.getColor(context, R.color.tz_surface3));
    mEdge.setStyle(Paint.Style.STROKE);
    mEdge.setStrokeWidth(mEdgeW);
    mRoad.setColor(ContextCompat.getColor(context, R.color.tz_surface3));
    mRoad.setStyle(Paint.Style.STROKE);
    mRoad.setStrokeWidth(getResources().getDimension(R.dimen.tz_tut_road_w));
    mLine.setColor(mint);
    mLine.setStyle(Paint.Style.STROKE);
    mLine.setStrokeWidth(getResources().getDimension(R.dimen.tz_tut_line_w));
    mLine.setStrokeCap(Paint.Cap.ROUND);
    mLine.setStrokeJoin(Paint.Join.ROUND);
    mFill.setColor(mint);
    mRing.setStyle(Paint.Style.STROKE);
    mRing.setStrokeWidth(getResources().getDimension(R.dimen.tz_tut_pin_ring));
    mHalo.setColor(ContextCompat.getColor(context, R.color.tz_mint_16));
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
  }

  /** 0: the empty map; up to {@link #DROP_PART} the pin drops; then the line draws to it; 1: the full picture. */
  public void setProgress(float progress)
  {
    mProgress = Math.max(0f, Math.min(1f, progress));
    invalidate();
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh)
  {
    mScale = w / UNITS_W;
    mRect.set(0, 0, w, h);
    mClip.rewind();
    mClip.addRoundRect(mRect, mRadius, mRadius, Path.Direction.CW);
    mRoute.rewind();
    mRoute.moveTo(LINE[0] * mScale, LINE[1] * mScale);
    for (int i = 2; i < LINE.length; i += 2)
      mRoute.lineTo(LINE[i] * mScale, LINE[i + 1] * mScale);
  }

  @Override
  protected void onDraw(@NonNull Canvas canvas)
  {
    final int save = canvas.save();
    canvas.clipPath(mClip);
    canvas.drawRect(mRect, mBg);
    for (float[] r : ROADS)
      canvas.drawLine(r[0] * mScale, r[1] * mScale, r[2] * mScale, r[3] * mScale, mRoad);

    // The line, as far as it has come.
    final float lineT = mProgress <= DROP_PART ? 0f : (mProgress - DROP_PART) / (1f - DROP_PART);
    if (lineT > 0f)
    {
      mMeasure.setPath(mRoute, false);
      mDrawn.rewind();
      mMeasure.getSegment(0, mMeasure.getLength() * lineT, mDrawn, true);
      canvas.drawPath(mDrawn, mLine);
    }
    // The start dot, ringed in the well.
    final float sx = LINE[0] * mScale;
    final float sy = LINE[1] * mScale;
    mRing.setColor(mBg.getColor());
    canvas.drawCircle(sx, sy, mStartR, mFill);
    canvas.drawCircle(sx, sy, mStartR, mRing);
    // The pin: falls from above and fades in, then its halo.
    final float dropT = Math.min(1f, mProgress / DROP_PART);
    final float px = LINE[LINE.length - 2] * mScale;
    final float py = LINE[LINE.length - 1] * mScale - mDrop * (1f - dropT) * (1f - dropT);
    if (dropT >= 1f)
      canvas.drawCircle(px, py, mHaloR, mHalo);
    if (dropT > 0f)
    {
      final int alpha = Math.round(255 * dropT);
      mFill.setColor(mBg.getColor());
      mFill.setAlpha(alpha);
      canvas.drawCircle(px, py, mPinR, mFill);
      mRing.setColor(mLine.getColor());
      mRing.setAlpha(alpha);
      canvas.drawCircle(px, py, mPinR, mRing);
      mFill.setColor(mLine.getColor());
      mRing.setAlpha(255);
    }
    canvas.restoreToCount(save);
    // The frame's edge over everything, inside the bounds.
    final float half = mEdgeW / 2f;
    mRect.inset(half, half);
    canvas.drawRoundRect(mRect, mRadius - half, mRadius - half, mEdge);
    mRect.inset(-half, -half);
  }
}
