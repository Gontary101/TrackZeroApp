package app.trackzero.screens;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import app.organicmaps.R;
import app.organicmaps.sdk.Framework;
import app.trackzero.TzIo;
import app.trackzero.TzTrace;
import app.trackzero.base.TzRoads;
import app.trackzero.ride.ShareText;
import app.trackzero.ride.TzFix;
import app.trackzero.ui.TzSegmented;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import java.util.TimeZone;

/**
 * E1 · Share location (FEAT-014, Phase 34): the ride's last good position as a short message the rider can edit, then
 * copy or hand to an app they choose with Android's share sheet. TrackZero sends nothing, reads no contacts, makes no
 * network request and never asks for a new fix; nothing of it exists until the sheet opens.
 */
public class TzShareSheet extends BottomSheetDialogFragment
{
  private static final String TAG = "TzShareSheet";
  private static final String ARG_HAS_FIX = "hasFix";
  private static final String ARG_LAT = "lat";
  private static final String ARG_LON = "lon";
  private static final String ARG_ACCURACY = "accuracy";
  private static final String ARG_UTC = "utc";
  private static final String ARG_ELAPSED = "elapsed";
  /**
   * The place is looked up once; if it takes longer, the message goes without it. The sheet never waits for it (the
   * text is there at once and gains the line if it comes in time, unless the rider has edited it).
   */
  private static final long PLACE_WAIT_MS = 2000;
  private static final double STREET_RADIUS_M = 50;

  @Nullable
  private ShareText.Fix mFix;
  private long mFixElapsedMs;
  private int mBattery;
  @Nullable
  private String mPlace;
  @NonNull
  private ShareText.Wording mWording = ShareText.Wording.CHECK_IN;
  /** The rider changed the text: it is not rewritten under them. */
  private boolean mEdited;
  private boolean mSettingText;
  private long mOpenedMs;

  /** Opens E1 over {@code parent} for the fix (null: no position yet). */
  public static void show(@NonNull Fragment parent, @Nullable TzFix fix)
  {
    final FragmentManager fm = parent.getChildFragmentManager();
    if (fm.findFragmentByTag(TAG) != null)
      return;
    final Bundle args = new Bundle();
    args.putBoolean(ARG_HAS_FIX, fix != null);
    if (fix != null)
    {
      args.putDouble(ARG_LAT, fix.lat);
      args.putDouble(ARG_LON, fix.lon);
      args.putDouble(ARG_ACCURACY, fix.accuracyM);
      args.putLong(ARG_UTC, fix.timeMs);
      args.putLong(ARG_ELAPSED, fix.elapsedMs);
    }
    final TzShareSheet sheet = new TzShareSheet();
    sheet.setArguments(args);
    sheet.show(fm, TAG);
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
    return inflater.inflate(R.layout.tz_sheet_share, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    final Bundle a = requireArguments();
    if (a.getBoolean(ARG_HAS_FIX))
    {
      mFix = new ShareText.Fix(a.getDouble(ARG_LAT), a.getDouble(ARG_LON), a.getDouble(ARG_ACCURACY),
                               a.getLong(ARG_UTC));
      mFixElapsedMs = a.getLong(ARG_ELAPSED);
    }
    mOpenedMs = SystemClock.elapsedRealtime();
    final BatteryManager battery = (BatteryManager) requireContext().getSystemService(Context.BATTERY_SERVICE);
    mBattery = battery == null ? 0 : battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);

    final TzSegmented wording = view.findViewById(R.id.tz_share_wording);
    wording.setFill(true);
    wording.setOptions(getString(R.string.tz_share_check_in), getString(R.string.tz_share_low_battery));
    wording.setListener(i -> setWording(i == 0 ? ShareText.Wording.CHECK_IN : ShareText.Wording.LOW_BATTERY));
    // A second tap on the second option switches between low battery and "I need help".
    wording.setReselectListener(i -> {
      if (i == 0)
        return;
      final boolean help = mWording != ShareText.Wording.HELP;
      wording.setOptionText(1, getString(help ? R.string.tz_share_need_help : R.string.tz_share_low_battery));
      setWording(help ? ShareText.Wording.HELP : ShareText.Wording.LOW_BATTERY);
    });

    final EditText text = view.findViewById(R.id.tz_share_text);
    text.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {}

      @Override
      public void afterTextChanged(Editable s)
      {
        if (!mSettingText)
          mEdited = true;
      }
    });
    view.findViewById(R.id.tz_share_copy).setOnClickListener(v -> copy(text.getText().toString()));
    final View send = view.findViewById(R.id.tz_share_send);
    send.setOnClickListener(v -> share(text.getText().toString()));
    // No app to share to: COPY alone.
    if (shareIntent("").resolveActivity(requireContext().getPackageManager()) == null)
      send.setVisibility(View.GONE);
    bindStatus(view);
    writeText();
    lookUpPlace();
  }

  private void setWording(@NonNull ShareText.Wording w)
  {
    mWording = w;
    // Choosing a wording rewrites the message, edits included: the rider asked for it.
    mEdited = false;
    writeText();
  }

  private long ageMs()
  {
    return SystemClock.elapsedRealtime() - mFixElapsedMs;
  }

  private void writeText()
  {
    final View v = getView();
    if (v == null || mEdited)
      return;
    final ShareText.Words w = new ShareText.Words();
    w.checkIn = getString(R.string.tz_share_msg_check_in);
    w.lowBattery = getString(R.string.tz_share_msg_low_battery);
    w.help = getString(R.string.tz_share_msg_help);
    w.coordinates = getString(R.string.tz_share_msg_coordinates);
    w.near = getString(R.string.tz_share_msg_near);
    w.stale = getString(R.string.tz_share_msg_stale);
    w.checkInNoFix = getString(R.string.tz_share_msg_check_in_no_fix);
    w.lowBatteryNoFix = getString(R.string.tz_share_msg_low_battery_no_fix);
    w.helpNoFix = getString(R.string.tz_share_msg_help_no_fix);
    final String message = ShareText.message(w, mWording, mFix, ageMs(), mBattery, mPlace, TimeZone.getDefault(),
                                             DateFormat.is24HourFormat(requireContext()));
    mSettingText = true;
    ((EditText) v.findViewById(R.id.tz_share_text)).setText(message);
    mSettingText = false;
  }

  /** The fix's age and the battery, with a dot: mint when fresh, amber when out of date. */
  private void bindStatus(@NonNull View v)
  {
    final TextView status = v.findViewById(R.id.tz_share_status);
    final ShareText.Age age = ShareText.age(mFix != null, ageMs());
    final int color;
    switch (age)
    {
    case NONE ->
    {
      status.setText(R.string.tz_share_no_fix);
      color = R.color.tz_amber;
    }
    case STALE ->
    {
      status.setText(getString(R.string.tz_share_fix_stale, ageMs() / 60_000));
      color = R.color.tz_amber;
    }
    default ->
    {
      status.setText(getString(R.string.tz_share_fix_fresh, ageMs() / 1000, mBattery));
      color = age == ShareText.Age.FRESH ? R.color.tz_mint : R.color.tz_text3;
    }
    }
    status.setTextColor(ContextCompat.getColor(requireContext(),
                                               age == ShareText.Age.STALE || age == ShareText.Age.NONE
                                                   ? R.color.tz_amber : R.color.tz_text2));
    final Drawable dot = DrawableCompat.wrap(ContextCompat.getDrawable(requireContext(), R.drawable.tz_dot)).mutate();
    final int size = getResources().getDimensionPixelSize(R.dimen.tz_space_8);
    dot.setBounds(0, 0, size, size);
    DrawableCompat.setTint(dot, ContextCompat.getColor(requireContext(), color));
    status.setCompoundDrawablesRelative(dot, null, null, null);
  }

  /** The street and the town near the fix, once, off the main thread; too late after 2 s. */
  private void lookUpPlace()
  {
    final ShareText.Fix fix = mFix;
    if (fix == null || !TzRoads.ready(requireContext()))
      return;
    final double lat = fix.lat;
    final double lon = fix.lon;
    TzIo.submit(() -> {
      final String street = Framework.nativeGetStreetName(lat, lon, STREET_RADIUS_M);
      final String town = Framework.nativeGetPlaceName(lat, lon);
      if (street.isEmpty())
        return town;
      return town.isEmpty() || town.equals(street) ? street : street + ", " + town;
    }, place -> {
      final long took = SystemClock.elapsedRealtime() - mOpenedMs;
      if (TzTrace.on())
        TzTrace.log("share", "place \"" + place + "\" after " + took + " ms");
      if (getView() == null || place.isEmpty() || took > PLACE_WAIT_MS)
        return;
      mPlace = place;
      writeText();
    });
  }

  private void copy(@NonNull String text)
  {
    final ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
    if (clipboard == null)
      return;
    clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.tz_share_title), text));
    Toast.makeText(requireContext(), R.string.tz_share_copied, Toast.LENGTH_SHORT).show();
  }

  private void share(@NonNull String text)
  {
    startActivity(Intent.createChooser(shareIntent(text), getString(R.string.tz_share_chooser)));
  }

  @NonNull
  private static Intent shareIntent(@NonNull String text)
  {
    return new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
  }
}
