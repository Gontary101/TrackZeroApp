package app.trackzero.ride;

import androidx.annotation.NonNull;

/**
 * The ride's estimate is worked out once, when its route is first known (Phase 33): reroutes, fixes and restores do
 * not ask again. Pure.
 */
final class EstimateOnce
{
  private boolean mAsked;

  /** Runs {@code work} the first time only; true if it ran. */
  boolean routeKnown(@NonNull Runnable work)
  {
    if (mAsked)
      return false;
    mAsked = true;
    work.run();
    return true;
  }

  /** A restored ride has its estimate already. */
  void restored()
  {
    mAsked = true;
  }
}
