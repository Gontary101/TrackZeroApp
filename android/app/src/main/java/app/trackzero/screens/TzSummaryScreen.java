package app.trackzero.screens;

import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.R;
import app.trackzero.TzNavigator;
import app.trackzero.TzPrefs;
import app.trackzero.base.TzBase;
import app.trackzero.format.TzFormat;
import app.trackzero.ride.Ride;
import app.trackzero.ride.RideStore;
import app.trackzero.ui.TzMetricView;
import app.trackzero.ui.TzSilhouetteView;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** R8 · Ride saved. */
public class TzSummaryScreen extends TzScreen
{
  public static final String ARG_RIDE_ID = "rideId";

  @Nullable
  private Ride mRide;

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.tz_screen_summary, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    mRide = null;
    view.findViewById(R.id.tz_summary_export).setOnClickListener(v -> exportGpx());
    view.findViewById(R.id.tz_summary_done).setOnClickListener(
        v -> TzNavigator.show(TzNavigator.Screen.MAP, null));
    final TzSilhouetteView track = view.findViewById(R.id.tz_summary_track);
    track.setStrokeWidthPx(getResources().getDimension(R.dimen.tz_silhouette_stroke_large));
    track.setBoxSizePx(getResources().getDimension(R.dimen.tz_summary_track_box));
    track.setStartMarker(getResources().getDimension(R.dimen.tz_silhouette_start_r),
                         getResources().getDimension(R.dimen.tz_silhouette_start_stroke));
    super.onViewCreated(view, savedInstanceState);

    final String id = requireArguments().getString(ARG_RIDE_ID);
    RideStore.get(requireContext()).find(id, ride -> {
      if (getView() == null)
        return;
      if (ride == null)
      {
        TzNavigator.show(TzNavigator.Screen.MAP, null);
        return;
      }
      mRide = ride;
      bindRide(ride);
    });
  }

  @Override
  public void onInsets(int top, int bottom)
  {
    final View view = getView();
    if (view == null)
      return;
    final int space = getResources().getDimensionPixelSize(R.dimen.tz_space_16);
    view.setPadding(space, top + space, space, bottom + space);
  }

  private void bindRide(@NonNull Ride ride)
  {
    final View view = requireView();
    final TzFormat.Units units = TzPrefs.units(requireContext());
    final Locale locale = Locale.getDefault();
    final TimeZone timeZone = TimeZone.getDefault();
    final boolean use24h = DateFormat.is24HourFormat(requireContext());
    final SimpleDateFormat date = new SimpleDateFormat(
        DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale);
    date.setTimeZone(timeZone);
    ((TextView) view.findViewById(R.id.tz_summary_title)).setText(ride.name);
    ((TextView) view.findViewById(R.id.tz_summary_date)).setText(
        date.format(new Date(ride.startedAt)) + " · "
        + TzFormat.clock(ride.startedAt, timeZone, use24h) + " – "
        + TzFormat.clock(ride.endedAt, timeZone, use24h));
    ((TzSilhouetteView) view.findViewById(R.id.tz_summary_track)).setPoints(ride.silLat, ride.silLon);
    ((TzMetricView) view.findViewById(R.id.tz_summary_distance))
        .setValue(TzFormat.distance(ride.distanceM, units, locale));
    ((TzMetricView) view.findViewById(R.id.tz_summary_moving))
        .setValue(TzFormat.duration(ride.movingS), "");
    ((TzMetricView) view.findViewById(R.id.tz_summary_gain))
        .setValue(TzFormat.elevation(ride.gainM, units, locale));
    ((TzMetricView) view.findViewById(R.id.tz_summary_avg_speed))
        .setValue(TzFormat.speed(ride.avgKmh, units, locale));
    ((TzMetricView) view.findViewById(R.id.tz_summary_max_speed))
        .setValue(TzFormat.speed(ride.maxKmh, units, locale));
    ((TzMetricView) view.findViewById(R.id.tz_summary_max_grade))
        .setValue(TzFormat.grade(ride.maxGrade, locale));
  }

  private void exportGpx()
  {
    final Ride ride = mRide;
    if (ride == null)
      return;
    final File file = RideStore.get(requireContext()).gpxFile(ride);
    if (!file.exists())
    {
      Toast.makeText(requireContext(), R.string.tz_gpx_missing, Toast.LENGTH_SHORT).show();
      return;
    }
    TzBase.shareGpx(requireActivity(), file, ride.name, getString(R.string.tz_export_gpx));
  }
}
