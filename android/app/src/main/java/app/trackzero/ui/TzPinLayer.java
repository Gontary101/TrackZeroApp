package app.trackzero.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import app.organicmaps.R;
import app.trackzero.base.TzMap;
import app.trackzero.routes.PlanDraft;
import java.util.ArrayList;
import java.util.List;

/**
 * The planner's pins over the map (Phase 28, Components v2 "Waypoint pins"): numbered waypoints (26dp surface2 disc,
 * 3dp mint ring), the destination (the route's ring) and a picked start (a mint dot). They are views placed where the
 * map shows their points. They are moved on every frame only while the map can be moving (a finger on it, or just
 * lifted, or after {@link #follow}); otherwise this layer does no work. Press a pin 300 ms and move it to drag it;
 * tap it to remove it.
 */
public class TzPinLayer extends FrameLayout
{
  /** The map may still move this long after the finger lifts (a fling, an animation). */
  static final long SETTLE_MS = 600;
  static final long DRAG_PRESS_MS = 300;

  public interface Listener
  {
    /** Point {@code index} of the plan's points was tapped. */
    void onPinTapped(int index);

    /** Point {@code index} was dragged to there. */
    void onPinMoved(int index, double lat, double lon);
  }

  private final List<View> mPins = new ArrayList<>();
  private final List<double[]> mAt = new ArrayList<>();
  @Nullable
  private View mStartPin;
  @Nullable
  private double[] mStartAt;
  @Nullable
  private Listener mListener;
  private final int[] mOrigin = new int[2];
  private long mFollowUntilMs;
  private boolean mFrameScheduled;
  /** A pin being dragged: it is not placed from its point. */
  @Nullable
  private View mDragged;
  private final Handler mHandler = new Handler(Looper.getMainLooper());
  private final Choreographer.FrameCallback mFrame = t -> {
    mFrameScheduled = false;
    reposition();
    if (TzMap.recentlyTouched(SETTLE_MS) || SystemClock.uptimeMillis() < mFollowUntilMs)
      scheduleFrame();
  };

  public TzPinLayer(@NonNull Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
    setClipChildren(false);
  }

  public void setListener(@Nullable Listener listener)
  {
    mListener = listener;
  }

  /** The plan's points (waypoints, then the destination) and a picked start (null: the rider). */
  public void setPoints(@NonNull List<PlanDraft.Point> points, @Nullable PlanDraft.Point start)
  {
    for (View v : mPins)
      removeView(v);
    mPins.clear();
    mAt.clear();
    for (int i = 0; i < points.size(); i++)
    {
      final boolean destination = i == points.size() - 1;
      final View pin = destination ? ring() : numbered(i + 1);
      final int index = i;
      pin.setOnTouchListener(new PinTouch(index));
      pin.setContentDescription(destination ? getResources().getString(R.string.tz_plan_cd_destination)
                                            : getResources().getString(R.string.tz_plan_cd_point, i + 1));
      addView(pin);
      mPins.add(pin);
      mAt.add(new double[] {points.get(i).lat, points.get(i).lon});
    }
    if (mStartPin != null)
      removeView(mStartPin);
    mStartPin = null;
    mStartAt = null;
    if (start != null)
    {
      mStartPin = dot();
      addView(mStartPin);
      mStartAt = new double[] {start.lat, start.lon};
    }
    reposition();
  }

  /** Keeps the pins on their points for {@code ms} more (the camera animates without a finger). */
  public void follow(long ms)
  {
    mFollowUntilMs = Math.max(mFollowUntilMs, SystemClock.uptimeMillis() + ms);
    scheduleFrame();
  }

  /** A finger touched the map: follow it until the map settles. */
  public void onMapTouched()
  {
    scheduleFrame();
  }

  private void scheduleFrame()
  {
    if (mFrameScheduled || !isAttachedToWindow())
      return;
    mFrameScheduled = true;
    Choreographer.getInstance().postFrameCallback(mFrame);
  }

  @Override
  protected void onDetachedFromWindow()
  {
    super.onDetachedFromWindow();
    Choreographer.getInstance().removeFrameCallback(mFrame);
    mFrameScheduled = false;
    mHandler.removeCallbacksAndMessages(null);
  }

  /** Places every pin on its point now. */
  public void reposition()
  {
    getLocationInWindow(mOrigin);
    for (int i = 0; i < mPins.size(); i++)
      if (mPins.get(i) != mDragged)
        place(mPins.get(i), mAt.get(i));
    if (mStartPin != null && mStartAt != null)
      place(mStartPin, mStartAt);
  }

  private void place(@NonNull View pin, @NonNull double[] at)
  {
    final float[] p = TzMap.latLonToScreen(at[0], at[1]);
    final int w = pin.getLayoutParams().width;
    final int h = pin.getLayoutParams().height;
    pin.setTranslationX(p[0] - mOrigin[0] - w / 2f);
    pin.setTranslationY(p[1] - mOrigin[1] - h / 2f);
  }

  /** Whether a touch (activity coordinates) starts on a pin. */
  public boolean hit(@NonNull MotionEvent e)
  {
    getLocationInWindow(mOrigin);
    final float x = e.getX() - mOrigin[0];
    final float y = e.getY() - mOrigin[1];
    for (View pin : mPins)
    {
      final float l = pin.getTranslationX();
      final float t = pin.getTranslationY();
      if (x >= l && x <= l + pin.getLayoutParams().width && y >= t && y <= t + pin.getLayoutParams().height)
        return true;
    }
    return false;
  }

  // ---- Pins ----

  @NonNull
  private View numbered(int n)
  {
    final int size = getResources().getDimensionPixelSize(R.dimen.tz_pin_waypoint);
    final TextView v = new TextView(getContext());
    v.setLayoutParams(new LayoutParams(size, size, Gravity.TOP | Gravity.START));
    v.setGravity(Gravity.CENTER);
    v.setText(String.valueOf(n));
    v.setTextColor(ContextCompat.getColor(getContext(), R.color.tz_text1));
    v.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.tz_text_14));
    v.setTypeface(ResourcesCompat.getFont(getContext(), R.font.barlow_condensed_bold));
    v.setIncludeFontPadding(false);
    final GradientDrawable bg = new GradientDrawable();
    bg.setShape(GradientDrawable.OVAL);
    bg.setColor(ContextCompat.getColor(getContext(), R.color.tz_surface2));
    bg.setStroke(getResources().getDimensionPixelSize(R.dimen.tz_pin_ring), ContextCompat.getColor(getContext(),
                                                                                                   R.color.tz_mint));
    v.setBackground(bg);
    v.setElevation(getResources().getDimension(R.dimen.tz_elev2));
    return v;
  }

  /** The destination: the route's ring (black disc, mint ring, mint dot), the size of the one the engine draws. */
  @NonNull
  private View ring()
  {
    final int size = getResources().getDimensionPixelSize(R.dimen.tz_pin_ring_size);
    final View v = new View(getContext());
    v.setLayoutParams(new LayoutParams(size, size, Gravity.TOP | Gravity.START));
    v.setBackgroundResource(R.drawable.tz_pin_destination);
    return v;
  }

  /** A picked start: a mint dot. */
  @NonNull
  private View dot()
  {
    final int size = getResources().getDimensionPixelSize(R.dimen.tz_pin_start);
    final View v = new View(getContext());
    v.setLayoutParams(new LayoutParams(size, size, Gravity.TOP | Gravity.START));
    v.setBackgroundResource(R.drawable.tz_pin_start);
    v.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    return v;
  }

  /** Press and hold to drag, tap to remove. */
  private final class PinTouch implements OnTouchListener
  {
    private final int mIndex;
    private float mDownX;
    private float mDownY;
    private float mStartTx;
    private float mStartTy;
    private boolean mMoved;
    private boolean mDragging;
    private final Runnable mStartDrag = () -> mDragging = true;

    PinTouch(int index)
    {
      mIndex = index;
    }

    @Override
    public boolean onTouch(View pin, MotionEvent e)
    {
      final float slop = getResources().getDimension(R.dimen.tz_space_8);
      switch (e.getActionMasked())
      {
      case MotionEvent.ACTION_DOWN ->
      {
        mDownX = e.getRawX();
        mDownY = e.getRawY();
        mStartTx = pin.getTranslationX();
        mStartTy = pin.getTranslationY();
        mMoved = false;
        mDragging = false;
        mHandler.postDelayed(mStartDrag, DRAG_PRESS_MS);
        getParent().requestDisallowInterceptTouchEvent(true);
      }
      case MotionEvent.ACTION_MOVE ->
      {
        final float dx = e.getRawX() - mDownX;
        final float dy = e.getRawY() - mDownY;
        if (!mDragging)
        {
          if (Math.hypot(dx, dy) > slop)
          {
            mMoved = true;
            mHandler.removeCallbacks(mStartDrag);
          }
          return true;
        }
        mDragged = pin;
        pin.setTranslationX(mStartTx + dx);
        pin.setTranslationY(mStartTy + dy);
      }
      case MotionEvent.ACTION_UP ->
      {
        mHandler.removeCallbacks(mStartDrag);
        if (mDragging && mDragged == pin)
        {
          mDragged = null;
          getLocationInWindow(mOrigin);
          final float cx = mOrigin[0] + pin.getTranslationX() + pin.getLayoutParams().width / 2f;
          final float cy = mOrigin[1] + pin.getTranslationY() + pin.getLayoutParams().height / 2f;
          final double[] ll = TzMap.screenToLatLon(cx, cy);
          if (mListener != null)
            mListener.onPinMoved(mIndex, ll[0], ll[1]);
        }
        else if (!mMoved && !mDragging && mListener != null)
        {
          mListener.onPinTapped(mIndex);
        }
        mDragging = false;
      }
      case MotionEvent.ACTION_CANCEL ->
      {
        mHandler.removeCallbacks(mStartDrag);
        mDragged = null;
        mDragging = false;
        reposition();
      }
      default -> {}
      }
      return true;
    }
  }
}
