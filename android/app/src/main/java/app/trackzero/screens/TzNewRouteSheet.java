package app.trackzero.screens;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import app.organicmaps.R;
import app.trackzero.ui.TzBigRow;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * N1 · New route sheet (Phase 28), from B3's NEW and B4's NEW ROUTE: import a GPX file, plan on the map, or propose a
 * loop (Phase 29). A tap closes the sheet and tells the screen that opened it.
 */
public class TzNewRouteSheet extends BottomSheetDialogFragment
{
  private static final String TAG = "TzNewRouteSheet";

  /** Implemented by the fragment that opens the sheet (its parent). */
  public interface Listener
  {
    void onImportChosen();

    void onPlanChosen();

    void onLoopChosen();
  }

  public static <T extends Fragment & Listener> void show(@NonNull T parent)
  {
    final FragmentManager fm = parent.getChildFragmentManager();
    if (fm.findFragmentByTag(TAG) == null)
      new TzNewRouteSheet().show(fm, TAG);
  }

  @Override
  public int getTheme()
  {
    return R.style.ThemeOverlay_TrackZero_BottomSheet;
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(@Nullable Bundle savedInstanceState)
  {
    final BottomSheetDialog d = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
    d.getBehavior().setSkipCollapsed(true);
    d.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
    return d;
  }

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.tz_sheet_new_route, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    final LinearLayout rows = view.findViewById(R.id.tz_new_route_rows);
    addRow(rows, R.drawable.ic_tz_import, R.string.tz_new_import, R.string.tz_new_import_desc, () -> {
      if (getParentFragment() instanceof Listener l)
        l.onImportChosen();
    });
    addRow(rows, R.drawable.ic_tz_pin, R.string.tz_new_plan, R.string.tz_new_plan_desc, () -> {
      if (getParentFragment() instanceof Listener l)
        l.onPlanChosen();
    });
    addRow(rows, R.drawable.ic_tz_loop, R.string.tz_new_loop, R.string.tz_new_loop_desc, () -> {
      if (getParentFragment() instanceof Listener l)
        l.onLoopChosen();
    });
    final int bottom = view.getPaddingBottom();
    ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
      final Insets bars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
      v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bottom + bars.bottom);
      return insets;
    });
  }

  private void addRow(@NonNull LinearLayout rows, @DrawableRes int icon, @StringRes int title, @StringRes int desc,
                      @NonNull Runnable action)
  {
    final TzBigRow row = new TzBigRow(requireContext(), null);
    row.bind(icon, getString(title), getString(desc));
    row.showChevron();
    row.setOnClickListener(v -> {
      dismiss();
      action.run();
    });
    final LinearLayout.LayoutParams lp =
        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.topMargin = getResources().getDimensionPixelSize(R.dimen.tz_space_12);
    rows.addView(row, lp);
  }
}
