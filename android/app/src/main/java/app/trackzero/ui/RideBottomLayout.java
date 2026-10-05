package app.trackzero.ui;

/**
 * The ride's bottom layout from one value (BUG-003, Phase 19): the handle sits at {@code base} (the bottom edge, or
 * above the system's gesture strip, D43) and everything else stacks on it. Pure arithmetic, in pixels.
 */
public final class RideBottomLayout
{
  /** Collapsed sheet: the handle. */
  public final int peek;
  /** Stats island (map page) and climb island (R5): just above the handle. */
  public final int islandMargin;
  /** The floating RESUME (R7 on the map page). */
  public final int resumeMargin;
  /** The speed page's grid ends on the handle. */
  public final int pagePadding;
  /** The controls sheet's content ends above the gesture strip. */
  public final int controlsPadding;

  public RideBottomLayout(int base, int handle, int aboveHandle, int resumeAboveHandle, int sheetGap)
  {
    peek = base + handle;
    islandMargin = base + handle + aboveHandle;
    resumeMargin = base + handle + resumeAboveHandle;
    pagePadding = base + handle;
    controlsPadding = base + sheetGap;
  }
}
