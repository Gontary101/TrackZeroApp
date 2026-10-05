package app.trackzero.ui;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import app.organicmaps.R;
import app.trackzero.routes.Profile;

/** A profile's label, description and icon (board N2). */
public final class TzProfiles
{
  private TzProfiles() {}

  @StringRes
  public static int label(@NonNull Profile p)
  {
    return switch (p)
    {
      case ROAD -> R.string.tz_profile_road;
      case GRAVEL -> R.string.tz_profile_gravel;
      case WALK -> R.string.tz_profile_walk;
      case HIKE -> R.string.tz_profile_hike;
    };
  }

  @StringRes
  public static int description(@NonNull Profile p)
  {
    return switch (p)
    {
      case ROAD -> R.string.tz_profile_road_desc;
      case GRAVEL -> R.string.tz_profile_gravel_desc;
      case WALK -> R.string.tz_profile_walk_desc;
      case HIKE -> R.string.tz_profile_hike_desc;
    };
  }

  @DrawableRes
  public static int icon(@NonNull Profile p)
  {
    return switch (p)
    {
      case ROAD -> R.drawable.ic_tz_road;
      case GRAVEL -> R.drawable.ic_tz_gravel;
      case WALK -> R.drawable.ic_tz_walk;
      case HIKE -> R.drawable.ic_tz_hike;
    };
  }
}
