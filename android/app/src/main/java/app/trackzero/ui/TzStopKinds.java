package app.trackzero.ui;

import android.content.Context;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import app.organicmaps.R;
import app.trackzero.follow.Stop;

/** A stop's kind → its label and icon (S1, S2; Phase 30). */
public final class TzStopKinds
{
  private TzStopKinds() {}

  @StringRes
  public static int label(int kind)
  {
    return switch (kind)
    {
      case Stop.WATER_POINT -> R.string.tz_stop_water_point;
      case Stop.WATER_TAP -> R.string.tz_stop_water_tap;
      case Stop.SPRING -> R.string.tz_stop_spring;
      case Stop.REPAIR_STATION -> R.string.tz_stop_repair_station;
      case Stop.BIKE_SHOP -> R.string.tz_stop_bike_shop;
      default -> R.string.tz_stop_drinking_water;
    };
  }

  @DrawableRes
  public static int icon(int kind)
  {
    return kind >= Stop.REPAIR_STATION ? R.drawable.ic_tz_wrench : R.drawable.ic_tz_water;
  }

  /** The map's name, or the kind when the map has none. */
  @NonNull
  public static String name(@NonNull Context c, @NonNull Stop s)
  {
    return s.name.isEmpty() ? c.getString(label(s.kind)) : s.name;
  }
}
