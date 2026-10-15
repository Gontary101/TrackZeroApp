package app.trackzero.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Region;
import android.os.Build;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The appearance switch (More › Appearance) as a circle opening from the row that was tapped (Phase 38).
 * {@link #capture} puts a picture of the screen over it; the activity changes its colours in place underneath
 * (TzActivity handles the night mode change itself, so the window and the map's surface stay); then {@link #reveal}
 * opens a circle in the picture onto the new appearance. Should the activity be recreated after all, {@link #play}
 * covers the new one with the same picture from its first frame. With animations off the picture just goes.
 */
public final class TzThemeReveal
{
  private static final long REVEAL_MS = 520;
  /** The reveal starts by then even if the main thread never rests. */
  private static final long MAX_WAIT_MS = 1500;

  @Nullable
  private static Bitmap sShot;
  private static float sCx;
  private static float sCy;
  @Nullable
  private static Cover sCover;

  private TzThemeReveal() {}

  /** Covers the activity with a picture of itself; the reveal is to start at (cx, cy), window pixels. */
  public static void capture(@NonNull Activity activity, float cx, float cy)
  {
    final View root = activity.getWindow().getDecorView();
    if (root.getWidth() == 0 || root.getHeight() == 0)
      return;
    final Bitmap shot;
    try
    {
      shot = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
    }
    catch (OutOfMemoryError e)
    {
      // No picture: the colours just change.
      return;
    }
    root.draw(new Canvas(shot));
    drop();
    sShot = shot;
    sCx = cx;
    sCy = cy;
    sCover = cover(activity);
  }

  /** The colours changed underneath (in place): open the circle once the new screen has been drawn. */
  public static void reveal(@NonNull Activity activity)
  {
    final Cover cover = sCover;
    if (cover != null && cover.getContext() == activity && cover.isAttachedToWindow())
      startWhenDrawn(activity, cover);
  }

  /** In a recreated activity's onCreate: the old screen over it from its first frame, then the reveal. */
  public static void play(@NonNull Activity activity)
  {
    if (sShot == null)
      return;
    final Cover cover = cover(activity);
    sCover = cover;
    startWhenDrawn(activity, cover);
  }

  @NonNull
  private static Cover cover(@NonNull Activity activity)
  {
    final ViewGroup root = (ViewGroup) activity.getWindow().getDecorView();
    final Cover cover = new Cover(activity, sShot, sCx, sCy);
    root.addView(cover, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                   ViewGroup.LayoutParams.MATCH_PARENT));
    return cover;
  }

  /**
   * After two frames and once the main thread rests: right after the switch the map reloads its style there
   * (about half a second on the emulator), which would skip most of the reveal.
   */
  private static void startWhenDrawn(@NonNull Activity activity, @NonNull Cover cover)
  {
    final boolean[] started = {false};
    final Runnable start = () -> {
      if (started[0] || !cover.isAttachedToWindow() || sCover != cover)
        return;
      started[0] = true;
      animate(activity, cover);
    };
    cover.postOnAnimation(() -> cover.postOnAnimation(() -> Looper.myQueue().addIdleHandler(() -> {
      start.run();
      return false;
    })));
    cover.postDelayed(start, MAX_WAIT_MS);
  }

  private static void animate(@NonNull Activity activity, @NonNull Cover cover)
  {
    if (!TzMotion.enabled(activity))
    {
      done(cover);
      return;
    }
    final float maxR = (float) Math.hypot(Math.max(cover.mCx, cover.getWidth() - cover.mCx),
                                          Math.max(cover.mCy, cover.getHeight() - cover.mCy));
    final ValueAnimator a = ValueAnimator.ofFloat(0f, maxR);
    a.setDuration(REVEAL_MS);
    a.setInterpolator(TzMotion.EASE);
    a.addUpdateListener(v -> cover.setRadius((float) v.getAnimatedValue()));
    a.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd(Animator animation)
      {
        done(cover);
      }
    });
    a.start();
  }

  private static void done(@NonNull Cover cover)
  {
    if (cover.getParent() instanceof ViewGroup parent)
      parent.removeView(cover);
    if (sCover == cover)
      drop();
  }

  private static void drop()
  {
    if (sCover != null && sCover.getParent() instanceof ViewGroup parent)
      parent.removeView(sCover);
    sCover = null;
    if (sShot != null)
      sShot.recycle();
    sShot = null;
  }

  /** The old screen with a growing hole: drawn everywhere outside the circle. */
  private static final class Cover extends View
  {
    @NonNull
    private final Bitmap mShot;
    private final Path mHole = new Path();
    private final float mCx;
    private final float mCy;
    private float mRadius;

    Cover(@NonNull Context context, @NonNull Bitmap shot, float cx, float cy)
    {
      super(context);
      mShot = shot;
      mCx = cx;
      mCy = cy;
      // Touches go to the screen underneath at once.
      setClickable(false);
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }

    void setRadius(float r)
    {
      mRadius = r;
      invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas)
    {
      if (mShot.isRecycled())
        return;
      // The picture clipped outside the circle: a clip is cheap for the renderer, where a full-screen path filled
      // through a shader is rasterised as a mask every frame (half a second each on the emulator).
      if (mRadius <= 0)
      {
        canvas.drawBitmap(mShot, 0, 0, null);
        return;
      }
      final int save = canvas.save();
      mHole.rewind();
      mHole.addCircle(mCx, mCy, mRadius, Path.Direction.CW);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        canvas.clipOutPath(mHole);
      else
        canvas.clipPath(mHole, Region.Op.DIFFERENCE);
      canvas.drawBitmap(mShot, 0, 0, null);
      canvas.restoreToCount(save);
    }
  }
}
