package app.trackzero.ride;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * E1's message (FEAT-014, Phase 34): the rider's last good position as text and a link, with its time, accuracy and the
 * place when known, in one of three wordings. Nothing is sent by TrackZero: the rider picks the app. Pure; the words
 * come from the app's strings.
 */
public final class ShareText
{
  /** A fix older than this is said to be out of date. */
  public static final long STALE_MS = 2 * 60_000;
  /** Up to this old, the line under the box says "GPS fix n s ago". */
  static final long FRESH_MS = 30_000;

  public enum Wording
  {
    CHECK_IN,
    LOW_BATTERY,
    HELP
  }

  /** The strings, with their {@code %} arguments (String.format). */
  public static final class Words
  {
    /** %1$s time */
    public String checkIn;
    /** %1$d battery %, %2$s time */
    public String lowBattery;
    /** %1$s time */
    public String help;
    /** %1$s lat, %2$s lon, %3$d accuracy m */
    public String coordinates;
    /** %1$s place */
    public String near;
    /** %1$s time */
    public String stale;
    public String noFix;
    /** First lines with no position: %1$d battery % (low battery) */
    public String checkInNoFix;
    public String lowBatteryNoFix;
    public String helpNoFix;
  }

  /** The position: the ride's last accepted fix. */
  public static final class Fix
  {
    public final double lat;
    public final double lon;
    final double accuracyM;
    /** UTC time of the fix. */
    final long timeMs;

    public Fix(double lat, double lon, double accuracyM, long timeMs)
    {
      this.lat = lat;
      this.lon = lon;
      this.accuracyM = accuracyM;
      this.timeMs = timeMs;
    }
  }

  private ShareText() {}

  /**
   * @param fix   null: no position at all
   * @param ageMs how old the fix is
   * @param place "Route du Puy de Dôme, Orcines", or null / "" when unknown
   */
  @NonNull
  public static String message(@NonNull Words w, @NonNull Wording wording, @Nullable Fix fix, long ageMs, int batteryPct,
                               @Nullable String place, @NonNull TimeZone tz, boolean use24h)
  {
    if (fix == null)
    {
      return switch (wording)
      {
        case CHECK_IN -> w.checkInNoFix;
        case LOW_BATTERY -> String.format(Locale.US, w.lowBatteryNoFix, batteryPct);
        case HELP -> w.helpNoFix;
      };
    }
    final String time = clock(fix.timeMs, tz, use24h);
    final StringBuilder out = new StringBuilder();
    out.append(switch (wording)
    {
      case CHECK_IN -> String.format(Locale.US, w.checkIn, time);
      case LOW_BATTERY -> String.format(Locale.US, w.lowBattery, batteryPct, time);
      case HELP -> String.format(Locale.US, w.help, time);
    });
    final String lat = coordinate(fix.lat);
    final String lon = coordinate(fix.lon);
    out.append('\n').append(String.format(Locale.US, w.coordinates, lat, lon, Math.round(fix.accuracyM)));
    if (ageMs > STALE_MS)
      out.append(' ').append(String.format(Locale.US, w.stale, time));
    if (place != null && !place.isEmpty())
      out.append('\n').append(String.format(Locale.US, w.near, place));
    out.append('\n').append(link(lat, lon));
    return out.toString();
  }

  /** 5 decimals with a dot, whatever the phone's language: what maps and people read. */
  @NonNull
  static String coordinate(double v)
  {
    return String.format(Locale.US, "%.5f", v);
  }

  @NonNull
  static String link(@NonNull String lat, @NonNull String lon)
  {
    return "https://www.openstreetmap.org/?mlat=" + lat + "&mlon=" + lon + "#map=17/" + lat + "/" + lon;
  }

  @NonNull
  static String clock(long utcMs, @NonNull TimeZone tz, boolean use24h)
  {
    final SimpleDateFormat f = new SimpleDateFormat(use24h ? "HH:mm" : "h:mm a", Locale.US);
    f.setTimeZone(tz);
    return f.format(new Date(utcMs));
  }

  /** Fresh (≤ 30 s), neutral (≤ 2 min) or stale: how the line under the box reads and which dot it has. */
  public enum Age
  {
    FRESH,
    RECENT,
    STALE,
    NONE
  }

  @NonNull
  public static Age age(boolean hasFix, long ageMs)
  {
    if (!hasFix)
      return Age.NONE;
    if (ageMs <= FRESH_MS)
      return Age.FRESH;
    return ageMs <= STALE_MS ? Age.RECENT : Age.STALE;
  }
}
