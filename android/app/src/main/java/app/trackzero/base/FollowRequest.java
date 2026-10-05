package app.trackzero.base;

import app.organicmaps.sdk.location.LocationState;

/**
 * The follow mode a screen asked for, and what a mode change from the engine means for it (BUG-004, D42). Each request
 * is one engine command that sets the mode whatever it was, so asking again is always safe; the engine's answers come
 * later and may be about older commands.
 */
final class FollowRequest
{
  static final int NONE = -1;

  /** What to do after a mode change. */
  enum Action
  {
    NOTHING,
    /** The rider moved the map away: the request is dropped. */
    CANCELLED,
    /** The engine is not following as asked although nobody touched the map: ask again. */
    ASK_AGAIN
  }

  private int mMode = NONE;

  void request(boolean headingUp)
  {
    mMode = headingUp ? LocationState.FOLLOW_AND_ROTATE : LocationState.FOLLOW;
  }

  void forget()
  {
    mMode = NONE;
  }

  int mode()
  {
    return mMode;
  }

  /**
   * @param mode the mode reported
   * @param touching a finger is on the screen, or left it a moment ago (a fling)
   * @param engineMode the engine's mode now (the report may be about an older command)
   */
  Action onModeChanged(int mode, boolean touching, int engineMode)
  {
    if (mMode == NONE)
      return Action.NOTHING;
    if (mode == LocationState.NOT_FOLLOW && touching)
    {
      mMode = NONE;
      return Action.CANCELLED;
    }
    // Without a finger on the screen the change answers an older command: a screen that stopped following before this
    // request, applied after it (follow requests jump the engine's queue), or the engine finding the position again
    // north-up after losing it. Only the engine's mode now counts.
    if ((mode == LocationState.NOT_FOLLOW && engineMode == LocationState.NOT_FOLLOW)
        || (mode == LocationState.FOLLOW && mMode == LocationState.FOLLOW_AND_ROTATE
            && engineMode == LocationState.FOLLOW))
      return Action.ASK_AGAIN;
    return Action.NOTHING;
  }
}
