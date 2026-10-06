package app.trackzero.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import androidx.annotation.NonNull;
import app.organicmaps.R;
import app.trackzero.base.TzMap;

/**
 * Taps and long-presses on the map itself (Phase 28), from the map's touch stream: one finger down and up within 8dp
 * and 300 ms is a tap; held 500 ms within 8dp, a long-press. Touches that start on a TrackZero view ({@link Chrome})
 * are not the map's.
 */
public final class TzMapTap implements TzMap.TouchObserver
{
  static final long TAP_MS = 300;
  static final long LONG_PRESS_MS = 500;

  public interface Listener
  {
    default void onTap(float x, float y) {}

    default void onLongPress(float x, float y) {}
  }

  /** Whether a touch starts on the screen's own views (buttons, cards, pins) rather than on the map. */
  public interface Chrome
  {
    boolean covers(@NonNull MotionEvent e);
  }

  @NonNull
  private final Listener mListener;
  @NonNull
  private final Chrome mChrome;
  private final float mSlop;
  private final Handler mHandler = new Handler(Looper.getMainLooper());
  /** The gesture so far can still be a tap or a long-press. */
  private boolean mCandidate;
  private boolean mLongPressed;
  private float mDownX;
  private float mDownY;
  private long mDownMs;
  @NonNull
  private final Runnable mLongPress;

  public TzMapTap(@NonNull Context context, @NonNull Listener listener, @NonNull Chrome chrome)
  {
    mListener = listener;
    mChrome = chrome;
    mLongPress = () -> {
      if (!mCandidate)
        return;
      mLongPressed = true;
      mCandidate = false;
      mListener.onLongPress(mDownX, mDownY);
    };
    mSlop = context.getResources().getDimension(R.dimen.tz_space_8);
  }

  @Override
  public void onTouch(@NonNull MotionEvent e)
  {
    switch (e.getActionMasked())
    {
    case MotionEvent.ACTION_DOWN ->
    {
      mCandidate = !mChrome.covers(e);
      mLongPressed = false;
      mDownX = e.getX();
      mDownY = e.getY();
      mDownMs = e.getEventTime();
      mHandler.removeCallbacks(mLongPress);
      if (mCandidate)
        mHandler.postDelayed(mLongPress, LONG_PRESS_MS);
    }
    case MotionEvent.ACTION_POINTER_DOWN -> cancel();
    case MotionEvent.ACTION_MOVE ->
    {
      if (mCandidate && Math.hypot(e.getX() - mDownX, e.getY() - mDownY) > mSlop)
        cancel();
    }
    case MotionEvent.ACTION_UP ->
    {
      final boolean tap = mCandidate && !mLongPressed && e.getEventTime() - mDownMs <= TAP_MS;
      cancel();
      if (tap)
        mListener.onTap(mDownX, mDownY);
    }
    case MotionEvent.ACTION_CANCEL -> cancel();
    default -> {}
    }
  }

  /** Not a tap any more (or the screen goes). */
  public void cancel()
  {
    mCandidate = false;
    mHandler.removeCallbacks(mLongPress);
  }
}
