package app.trackzero;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import app.organicmaps.R;
import app.trackzero.base.TzMap;
import app.trackzero.screens.TzMapScreen;
import app.trackzero.screens.TzMoreScreen;
import app.trackzero.screens.TzPreviewScreen;
import app.trackzero.screens.TzRoutesScreen;
import app.trackzero.screens.TzScreen;
import app.trackzero.ui.TzBottomIsland;

/**
 * Shows TrackZero screens inside {@code tz_root}, above the map, which is never removed.
 * The bottom island belongs to the navigator, so switching tabs crossfades only the content ("island stays").
 */
public final class TzNavigator
{
  public enum Screen
  {
    MAP(false, TzBottomIsland.Tab.MAP),
    SEARCH(true, null),
    ROUTES(true, TzBottomIsland.Tab.ROUTES),
    PREVIEW(false, null),
    MORE(true, TzBottomIsland.Tab.MORE),
    HISTORY(true, TzBottomIsland.Tab.MORE),
    RIDE(false, null),
    SUMMARY(true, null);

    /** Covers the map completely: map drawing is paused while shown. */
    final boolean opaque;
    /** Island tab to highlight, or null when the island is hidden on this screen. */
    @Nullable
    final TzBottomIsland.Tab islandTab;

    Screen(boolean opaque, @Nullable TzBottomIsland.Tab islandTab)
    {
      this.opaque = opaque;
      this.islandTab = islandTab;
    }
  }

  @Nullable
  private static FragmentActivity sActivity;
  @Nullable
  private static TzBottomIsland sIsland;
  @NonNull
  private static Screen sCurrent = Screen.MAP;
  private static int sTopInset;
  private static int sBottomInset;

  private static final OnBackPressedCallback sBack = new OnBackPressedCallback(false) {
    @Override
    public void handleOnBackPressed()
    {
      onBack();
    }
  };

  private TzNavigator() {}

  public static void attach(@NonNull FragmentActivity activity, @NonNull FrameLayout root)
  {
    sActivity = activity;

    final FrameLayout container = new FrameLayout(activity);
    container.setId(R.id.tz_screen_container);
    root.addView(container, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                                                          FrameLayout.LayoutParams.MATCH_PARENT));

    final TzBottomIsland island = new TzBottomIsland(activity, null);
    island.setId(R.id.tz_island);
    island.setListener(new TzBottomIsland.Listener() {
      @Override
      public void onTabSelected(@NonNull TzBottomIsland.Tab tab)
      {
        show(switch (tab)
        {
          case MAP -> Screen.MAP;
          case ROUTES -> Screen.ROUTES;
          case SEARCH -> Screen.SEARCH;
          case MORE -> Screen.MORE;
        }, null);
      }

      @Override
      public void onRideClicked()
      {
        Toast.makeText(activity, "Coming in phase 9", Toast.LENGTH_SHORT).show();
      }
    });
    final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT, activity.getResources().getDimensionPixelSize(R.dimen.tz_island_h),
        Gravity.BOTTOM);
    final int gutter = activity.getResources().getDimensionPixelSize(R.dimen.tz_gutter);
    lp.leftMargin = gutter;
    lp.rightMargin = gutter;
    root.addView(island, lp);
    sIsland = island;

    ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
      final Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
      sTopInset = bars.top;
      sBottomInset = bars.bottom;
      applyInsets();
      return windowInsets;
    });

    activity.getOnBackPressedDispatcher().addCallback(activity, sBack);

    // After recreation FragmentManager restores the visible screen by itself; otherwise start on the map.
    final Fragment restored = fm().findFragmentById(R.id.tz_screen_container);
    if (restored != null && restored.getTag() != null)
      onShown(Screen.valueOf(restored.getTag()));
    else
      show(Screen.MAP, null);
  }

  public static void detach(@NonNull FragmentActivity activity)
  {
    if (sActivity != activity)
      return;
    sBack.remove();
    sActivity = null;
    sIsland = null;
  }

  @NonNull
  public static Screen current()
  {
    return sCurrent;
  }

  /** Replaces the current screen (crossfade 150 ms). */
  public static void show(@NonNull Screen screen, @Nullable Bundle args)
  {
    if (sActivity == null || fm().isStateSaved())
      return;
    final TzScreen fragment = create(screen);
    if (fragment == null)
    {
      Toast.makeText(sActivity, "Coming in a later phase", Toast.LENGTH_SHORT).show();
      return;
    }
    fragment.setArguments(args);
    fm().beginTransaction()
        .setReorderingAllowed(true)
        .setCustomAnimations(R.animator.tz_fade_in, R.animator.tz_fade_out)
        .replace(R.id.tz_screen_container, fragment, screen.name())
        .commit();
    onShown(screen);
  }

  /** Status bar height in px; screens place top content at this + {@code tz_top_inset_extra}. */
  public static int topInset()
  {
    return sTopInset;
  }

  /** Navigation bar / gesture area height in px; screens place bottom content above it. */
  public static int bottomInset()
  {
    return sBottomInset;
  }

  @Nullable
  private static TzScreen create(@NonNull Screen screen)
  {
    return switch (screen)
    {
      case MAP -> new TzMapScreen();
      case ROUTES -> new TzRoutesScreen();
      case PREVIEW -> new TzPreviewScreen();
      case MORE -> new TzMoreScreen();
      default -> null;
    };
  }

  private static void onShown(@NonNull Screen screen)
  {
    sCurrent = screen;
    sBack.setEnabled(screen != Screen.MAP);
    // The ride screen pauses the map per carousel page itself.
    if (screen != Screen.RIDE)
      TzMap.setRenderingEnabled(!screen.opaque);
    if (sIsland != null)
    {
      sIsland.setVisibility(screen.islandTab != null ? View.VISIBLE : View.GONE);
      if (screen.islandTab != null)
        sIsland.setActive(screen.islandTab);
    }
  }

  private static void applyInsets()
  {
    if (sIsland != null && sActivity != null)
    {
      final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) sIsland.getLayoutParams();
      lp.bottomMargin = sBottomInset + sActivity.getResources().getDimensionPixelSize(R.dimen.tz_bottom_inset_extra);
      sIsland.setLayoutParams(lp);
    }
    final Fragment f = sActivity == null ? null : fm().findFragmentById(R.id.tz_screen_container);
    if (f instanceof TzScreen screen && f.getView() != null)
      screen.onInsets(sTopInset, sBottomInset);
  }

  private static void onBack()
  {
    final Fragment f = fm().findFragmentById(R.id.tz_screen_container);
    if (f instanceof TzScreen screen && screen.onBackPressed())
      return;
    switch (sCurrent)
    {
    case SEARCH, ROUTES, MORE, SUMMARY -> show(Screen.MAP, null);
    case PREVIEW -> show(Screen.ROUTES, null);
    case HISTORY -> show(Screen.MORE, null);
    // Back never ends a ride: the ride screen opens its controls sheet in onBackPressed().
    case RIDE, MAP -> {}
    }
  }

  @NonNull
  private static FragmentManager fm()
  {
    return sActivity.getSupportFragmentManager();
  }
}
