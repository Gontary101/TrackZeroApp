package app.trackzero.tutorial;

import androidx.annotation.NonNull;

/**
 * When the first-launch tutorial (Phase 37, T1–T4) shows and where it leaves to. Shown once, after the first-run map
 * screens and before B1, unless a ride is running; SKIP, START and Back all end it and mark it done. More › Show the
 * tutorial replays it and returns to More. Pure.
 */
public final class TutorialFlow
{
  public static final int STEPS = 4;

  /** Where the app opens, or where the tutorial leaves to. */
  public enum Place
  {
    MAP,
    RIDE,
    TUTORIAL,
    MORE
  }

  /** The tutorial's one setting. */
  public interface Store
  {
    boolean tutorialDone();

    void setTutorialDone();
  }

  private TutorialFlow() {}

  /** The first screen when the app opens with no screen to restore. */
  @NonNull
  public static Place start(boolean rideInProgress, @NonNull Store store)
  {
    if (rideInProgress)
      return Place.RIDE;
    return store.tutorialDone() ? Place.MAP : Place.TUTORIAL;
  }

  /** SKIP, START or Back on any step: done, and where to go. A replay goes back to More. */
  @NonNull
  public static Place finish(boolean replay, @NonNull Store store)
  {
    store.setTutorialDone();
    return replay ? Place.MORE : Place.MAP;
  }

  /** The button under step {@code index} (0-based): NEXT, or START on the last. */
  public static boolean isLast(int index)
  {
    return index >= STEPS - 1;
  }
}
