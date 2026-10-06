package app.trackzero.screens;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import app.organicmaps.R;
import app.trackzero.TzNavigator;
import app.trackzero.TzPrefs;
import app.trackzero.base.TzActivity;
import app.trackzero.routes.Profile;
import app.trackzero.tutorial.TutorialFlow;
import app.trackzero.ui.TzHoldButton;
import app.trackzero.ui.TzMotion;
import app.trackzero.ui.TzPageHandle;
import app.trackzero.ui.TzProfiles;
import app.trackzero.ui.TzTextSize;
import app.trackzero.ui.TzTutorialMapView;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * T1–T4 · the first-launch tutorial (Phase 37): four steps in a pager, SKIP on each, NEXT and START. Shown once
 * before B1 (TutorialFlow); More › Show the tutorial replays it ({@link #ARG_REPLAY}) and comes back to More. The
 * visuals are views and vector drawables. Each step's motion runs when the step is shown (T1's arrows three
 * times, T2's sheet and T4's pin and line once), then rests on its final frame; none with animations off, none
 * while the step is not the current one or the screen is not shown.
 */
public class TzTutorialScreen extends TzScreen
{
  public static final String ARG_REPLAY = "replay";

  private static final long NUDGE_MS = 800;
  private static final int NUDGES = 3;
  private static final long RISE_MS = 1200;
  private static final long PLAN_MS = 1400;

  private static final int[] TITLES = {R.string.tz_tut_swipe_title, R.string.tz_tut_pull_title,
                                       R.string.tz_tut_listen_title, R.string.tz_tut_plan_title};
  private static final int[] TEXTS = {R.string.tz_tut_swipe_text, R.string.tz_tut_pull_text,
                                      R.string.tz_tut_listen_text, R.string.tz_tut_plan_text};
  private static final int[] VISUALS = {R.layout.tz_tut_swipe, R.layout.tz_tut_pull, R.layout.tz_tone_grid,
                                        R.layout.tz_tut_plan};

  @Nullable
  private View mRoot;
  @Nullable
  private ViewPager2 mPager;
  /** Each step's page once bound (the pager keeps all four). */
  private final View[] mPages = new View[TutorialFlow.STEPS];
  private final List<Animator> mRunning = new ArrayList<>();
  private boolean mShown;

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.tz_screen_tutorial, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    mRoot = view;
    final TzPageHandle dots = view.findViewById(R.id.tz_tut_dots);
    dots.showDotsOnly(TutorialFlow.STEPS);
    final MaterialButton next = view.findViewById(R.id.tz_tut_next);
    view.findViewById(R.id.tz_tut_skip).setOnClickListener(v -> finish());
    mPager = view.findViewById(R.id.tz_tut_pager);
    mPager.setOffscreenPageLimit(TutorialFlow.STEPS - 1);
    mPager.setAdapter(new Steps());
    mPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
      @Override
      public void onPageSelected(int position)
      {
        dots.setPage(position, true);
        next.setText(TutorialFlow.isLast(position) ? R.string.tz_tut_start : R.string.tz_tut_next);
        play();
      }
    });
    next.setOnClickListener(v -> {
      final int at = mPager.getCurrentItem();
      if (TutorialFlow.isLast(at))
        finish();
      else
        mPager.setCurrentItem(at + 1);
    });
    super.onViewCreated(view, savedInstanceState);
  }

  @Override
  public void onResume()
  {
    super.onResume();
    mShown = true;
    play();
  }

  @Override
  public void onPause()
  {
    super.onPause();
    mShown = false;
    rest();
  }

  @Override
  public void onDestroyView()
  {
    super.onDestroyView();
    rest();
    mRoot = null;
    mPager = null;
    Arrays.fill(mPages, null);
  }

  @Override
  public boolean onBackPressed()
  {
    finish();
    return true;
  }

  @Override
  public void onInsets(int top, int bottom)
  {
    if (mRoot == null)
      return;
    mRoot.setPadding(mRoot.getPaddingLeft(), top + getResources().getDimensionPixelSize(R.dimen.tz_tut_pad_top),
                     mRoot.getPaddingRight(), bottom + getResources().getDimensionPixelSize(R.dimen.tz_tut_pad_bottom));
  }

  /** SKIP, START and Back: done, to B1 (or back to More after a replay). The held location question follows. */
  private void finish()
  {
    final boolean replay = getArguments() != null && getArguments().getBoolean(ARG_REPLAY);
    final TutorialFlow.Place to = TutorialFlow.finish(replay, TzPrefs.tutorial(requireContext()));
    TzNavigator.show(to == TutorialFlow.Place.MORE ? TzNavigator.Screen.MORE : TzNavigator.Screen.MAP, null);
    if (requireActivity() instanceof TzActivity a)
      a.onTutorialDone();
  }

  // ---- Motion ----

  /** Every step on its final frame, nothing running. */
  private void rest()
  {
    for (Animator a : new ArrayList<>(mRunning))
      a.cancel();
    mRunning.clear();
    for (int i = 0; i < mPages.length; i++)
      if (mPages[i] != null)
        restStep(i, mPages[i]);
  }

  /** The current step's motion, if it is shown and animations are on. */
  private void play()
  {
    rest();
    if (!mShown || mPager == null || !TzMotion.enabled(requireContext()))
      return;
    final int at = mPager.getCurrentItem();
    final View page = mPages[at];
    if (page == null)
      return;
    final ValueAnimator a = switch (at)
    {
      case 0 -> nudge(page);
      case 1 -> rise(page);
      case 3 -> draw(page);
      default -> null;
    };
    if (a == null)
      return;
    mRunning.add(a);
    a.start();
  }

  private static void restStep(int step, @NonNull View page)
  {
    switch (step)
    {
    case 0 ->
    {
      page.findViewById(R.id.tz_tut_arrow_left).setTranslationX(0);
      page.findViewById(R.id.tz_tut_arrow_right).setTranslationX(0);
    }
    case 1 -> page.findViewById(R.id.tz_tut_sheet).setTranslationY(0);
    case 3 -> ((TzTutorialMapView) page.findViewById(R.id.tz_tut_map)).setProgress(1f);
    default -> {}
    }
  }

  /** T1: the arrows move 8dp out and back, three times. */
  @NonNull
  private ValueAnimator nudge(@NonNull View page)
  {
    final View left = page.findViewById(R.id.tz_tut_arrow_left);
    final View right = page.findViewById(R.id.tz_tut_arrow_right);
    final float d = getResources().getDimension(R.dimen.tz_tut_nudge);
    final ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
    a.setDuration(NUDGE_MS);
    a.setRepeatCount(NUDGES - 1);
    a.addUpdateListener(v -> {
      final float x = (float) Math.sin(Math.PI * (float) v.getAnimatedValue()) * d;
      left.setTranslationX(-x);
      right.setTranslationX(x);
    });
    return a;
  }

  /** T2: the sheet rises 40dp and comes back, once. */
  @NonNull
  private ValueAnimator rise(@NonNull View page)
  {
    final View sheet = page.findViewById(R.id.tz_tut_sheet);
    final float d = getResources().getDimension(R.dimen.tz_tut_sheet_rise);
    final ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
    a.setDuration(RISE_MS);
    a.setInterpolator(TzMotion.EASE);
    a.addUpdateListener(v -> sheet.setTranslationY(-(float) Math.sin(Math.PI * (float) v.getAnimatedValue()) * d));
    return a;
  }

  /** T4: the pin drops and the line draws, once. */
  @NonNull
  private static ValueAnimator draw(@NonNull View page)
  {
    final TzTutorialMapView map = page.findViewById(R.id.tz_tut_map);
    final ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
    a.setDuration(PLAN_MS);
    a.setInterpolator(TzMotion.EASE);
    a.addUpdateListener(v -> map.setProgress((float) v.getAnimatedValue()));
    return a;
  }

  // ---- Steps ----

  private final class Steps extends RecyclerView.Adapter<RecyclerView.ViewHolder>
  {
    @Override
    public int getItemCount()
    {
      return TutorialFlow.STEPS;
    }

    @Override
    public int getItemViewType(int position)
    {
      return position;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int step)
    {
      final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
      final View page = inflater.inflate(R.layout.tz_tut_page, parent, false);
      final FrameLayout slot = page.findViewById(R.id.tz_tut_visual);
      inflater.inflate(VISUALS[step], slot, true);
      final TextView title = page.findViewById(R.id.tz_tut_title);
      title.setText(TITLES[step]);
      TzTextSize.capCurrent(title);
      final TextView text = page.findViewById(R.id.tz_tut_text);
      text.setText(TEXTS[step]);
      setUp(step, page, text);
      return new RecyclerView.ViewHolder(page) {};
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position)
    {
      mPages[position] = holder.itemView;
      restStep(position, holder.itemView);
      if (mPager != null && mPager.getCurrentItem() == position)
        play();
    }
  }

  private void setUp(int step, @NonNull View page, @NonNull TextView text)
  {
    switch (step)
    {
    // The visual says the sentence to a screen reader (it is the step's picture): the sentence is not read twice.
    case 0, 1 -> text.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    // T3: the tiles speak for themselves; the sentence stays.
    case 2 -> new ToneGrid(page);
    case 3 ->
    {
      text.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      profiles(page.findViewById(R.id.tz_tut_profiles));
    }
    default -> {}
    }
    if (step == 1)
      ((TzHoldButton) page.findViewById(R.id.tz_tut_hold)).setMini();
  }

  /** T4's four profile pills as a picture: three on the first row, Hike under them, as on the board. */
  private void profiles(@NonNull LinearLayout rows)
  {
    final int gap = getResources().getDimensionPixelSize(R.dimen.tz_tut_profile_gap);
    LinearLayout row = null;
    int i = 0;
    for (Profile p : Profile.values())
    {
      if (i % 3 == 0)
      {
        row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                                                                           ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        if (i > 0)
          lp.topMargin = gap;
        rows.addView(row, lp);
      }
      final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                                                                         getResources().getDimensionPixelSize(
                                                                             R.dimen.tz_tut_profile_h));
      if (i % 3 > 0)
        lp.setMarginStart(gap);
      row.addView(pill(p), lp);
      i++;
    }
  }

  @NonNull
  private View pill(@NonNull Profile p)
  {
    final LinearLayout pill = new LinearLayout(requireContext());
    pill.setOrientation(LinearLayout.HORIZONTAL);
    pill.setGravity(Gravity.CENTER_VERTICAL);
    pill.setBackgroundResource(R.drawable.tz_bg_surface3_r22);
    pill.setPaddingRelative(getResources().getDimensionPixelSize(R.dimen.tz_tut_profile_pad_start), 0,
                            getResources().getDimensionPixelSize(R.dimen.tz_tut_profile_pad_end), 0);
    final ImageView icon = new ImageView(requireContext());
    icon.setImageResource(TzProfiles.icon(p));
    ImageViewCompat.setImageTintList(icon, ContextCompat.getColorStateList(requireContext(), R.color.tz_text1));
    final int size = getResources().getDimensionPixelSize(R.dimen.tz_tut_profile_icon);
    pill.addView(icon, new LinearLayout.LayoutParams(size, size));
    final TextView label = new TextView(requireContext(), null, 0, R.style.TzText_ProfilePill);
    label.setText(TzProfiles.label(p));
    label.setTextColor(ContextCompat.getColor(requireContext(), R.color.tz_text1));
    final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                                                                       ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.setMarginStart(getResources().getDimensionPixelSize(R.dimen.tz_tut_profile_gap));
    pill.addView(label, lp);
    return pill;
  }
}
