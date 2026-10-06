package app.trackzero.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import app.organicmaps.R;
import app.trackzero.follow.Bivouac;
import app.trackzero.follow.Stop;
import java.util.ArrayList;
import java.util.List;

/** A stop's kind → its label and icon (S1, S2; Phase 30); a place to sleep's badge and note (Phase 31). */
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
      case Stop.CAMPSITE -> R.string.tz_stop_campsite;
      case Stop.HUT -> R.string.tz_stop_hut;
      case Stop.SHELTER -> R.string.tz_stop_shelter;
      default -> R.string.tz_stop_drinking_water;
    };
  }

  @DrawableRes
  public static int icon(int kind)
  {
    if (kind >= Stop.CAMPSITE)
      return R.drawable.ic_tz_tent;
    return kind >= Stop.REPAIR_STATION ? R.drawable.ic_tz_wrench : R.drawable.ic_tz_water;
  }

  /**
   * A place to sleep (Phase 31): its badge on {@code badge} (VERIFIED: surface3, text1, check; NOT CONFIRMED: amberBg,
   * amber, warning), hidden for other places.
   */
  public static void bindBadge(@NonNull TextView badge, @NonNull Stop s)
  {
    if (!Bivouac.isBivouac(s))
    {
      badge.setVisibility(View.GONE);
      return;
    }
    final Context c = badge.getContext();
    final boolean verified = Bivouac.verified(s);
    badge.setVisibility(View.VISIBLE);
    badge.setText(verified ? R.string.tz_badge_verified : R.string.tz_badge_not_confirmed);
    badge.setBackgroundResource(verified ? R.drawable.tz_bg_badge_verified : R.drawable.tz_bg_badge_unconfirmed);
    final int color = ContextCompat.getColor(c, verified ? R.color.tz_text1 : R.color.tz_amber);
    badge.setTextColor(color);
    final Drawable icon =
        DrawableCompat.wrap(ContextCompat.getDrawable(c, verified ? R.drawable.ic_tz_check : R.drawable.ic_tz_warn))
            .mutate();
    final int size = c.getResources().getDimensionPixelSize(R.dimen.tz_badge_icon);
    icon.setBounds(0, 0, size, size);
    DrawableCompat.setTint(icon, color);
    badge.setCompoundDrawablesRelative(icon, null, null, null);
    badge.setCompoundDrawablePadding(c.getResources().getDimensionPixelSize(R.dimen.tz_badge_icon_gap));
    final int pad = c.getResources().getDimensionPixelSize(R.dimen.tz_badge_pad);
    badge.setPaddingRelative(pad, 0, pad + c.getResources().getDimensionPixelSize(R.dimen.tz_badge_icon_gap), 0);
  }

  /**
   * What the map says limits a stay ("Open May-Oct · Fee"), and for a place not confirmed the line that says so; ""
   * for a place that is not one to sleep at.
   */
  @NonNull
  public static String bivouacNote(@NonNull Context c, @NonNull Stop s)
  {
    if (!Bivouac.isBivouac(s))
      return "";
    final List<String> parts = new ArrayList<>();
    for (Bivouac.Restriction r : Bivouac.restrictions(s))
    {
      parts.add(switch (r)
      {
        case HOURS -> c.getString(R.string.tz_bivouac_hours, s.hours);
        case FEE -> c.getString(R.string.tz_bivouac_fee);
        case NO_FEE -> c.getString(R.string.tz_bivouac_no_fee);
        case PRIVATE -> c.getString(R.string.tz_bivouac_private);
      });
    }
    final String limits = TextUtils.join(" · ", parts);
    if (Bivouac.verified(s))
      return limits;
    final String unconfirmed = c.getString(R.string.tz_bivouac_unconfirmed);
    return limits.isEmpty() ? unconfirmed : limits + "\n" + unconfirmed;
  }

  /** The map's name, or the kind when the map has none. */
  @NonNull
  public static String name(@NonNull Context c, @NonNull Stop s)
  {
    return s.name.isEmpty() ? c.getString(label(s.kind)) : s.name;
  }
}
