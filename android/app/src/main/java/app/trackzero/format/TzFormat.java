package app.trackzero.format;

import androidx.annotation.NonNull;
import java.text.NumberFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/** Number formatting rules of plan §X3. Numbers and units are separate strings (different text sizes). */
public final class TzFormat
{
  public enum Units
  {
    METRIC,
    IMPERIAL
  }

  /** A number and its unit, rendered in two TextViews. */
  public static final class Value
  {
    @NonNull
    public final String number;
    @NonNull
    public final String unit;

    Value(@NonNull String number, @NonNull String unit)
    {
      this.number = number;
      this.unit = unit;
    }

    @NonNull
    @Override
    public String toString()
    {
      return unit.isEmpty() ? number : number + " " + unit;
    }
  }

  /** Missing data. */
  public static final String UNKNOWN = "—";
  /** ETA / remaining while off route. */
  public static final String OFF_ROUTE = "--";

  private static final double M_PER_MILE = 1609.344;
  private static final double FT_PER_M = 3.280839895;
  private static final double KMH_PER_MPH = 1.609344;

  private TzFormat() {}

  /** 1 decimal; integer from 100 up. */
  @NonNull
  public static Value speed(double kmh, @NonNull Units units, @NonNull Locale locale)
  {
    if (units == Units.IMPERIAL)
      return new Value(oneDecimalOrInteger(kmh / KMH_PER_MPH, locale), "mph");
    return new Value(oneDecimalOrInteger(kmh, locale), "km/h");
  }

  /**
   * Metric: below 1 km in meters (rounded to 5 below 100 m, else to 10), else km with 1 decimal (integer from 100).
   * Imperial: below 0.1 mi in feet rounded to 10, else miles with the same rules.
   */
  @NonNull
  public static Value distance(double meters, @NonNull Units units, @NonNull Locale locale)
  {
    if (units == Units.IMPERIAL)
    {
      final double miles = meters / M_PER_MILE;
      if (miles < 0.1)
        return new Value(integer(Math.round(meters * FT_PER_M / 10) * 10, locale), "ft");
      return new Value(oneDecimalOrInteger(miles, locale), "mi");
    }
    final long rounded = meters < 100 ? Math.round(meters / 5) * 5 : Math.round(meters / 10) * 10;
    if (rounded < 1000)
      return new Value(integer(rounded, locale), "m");
    return new Value(oneDecimalOrInteger(meters / 1000, locale), "km");
  }

  /** Integer with locale grouping: 1,491 m / ft. */
  @NonNull
  public static Value elevation(double meters, @NonNull Units units, @NonNull Locale locale)
  {
    if (units == Units.IMPERIAL)
      return new Value(integer(Math.round(meters * FT_PER_M), locale), "ft");
    return new Value(integer(Math.round(meters), locale), "m");
  }

  /** 1 decimal %, {@link #UNKNOWN} when NaN. */
  @NonNull
  public static Value grade(double percent, @NonNull Locale locale)
  {
    if (Double.isNaN(percent))
      return new Value(UNKNOWN, "");
    return new Value(decimal(percent, 1, locale), "%");
  }

  /** Always H:MM:SS (0:24:10). */
  @NonNull
  public static String duration(long seconds)
  {
    final long s = Math.max(0, seconds);
    return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
  }

  /** Wall-clock time: 14:52 / 09:05 (HH:mm), or 2:52 PM when {@code use24h} is false. */
  @NonNull
  public static String clock(long epochMs, @NonNull TimeZone tz, boolean use24h)
  {
    final Calendar c = Calendar.getInstance(tz, Locale.ROOT);
    c.setTimeInMillis(epochMs);
    final int h = c.get(Calendar.HOUR_OF_DAY);
    final int m = c.get(Calendar.MINUTE);
    if (use24h)
      return String.format(Locale.ROOT, "%02d:%02d", h, m);
    final int h12 = h % 12 == 0 ? 12 : h % 12;
    return String.format(Locale.ROOT, "%d:%02d %s", h12, m, h < 12 ? "AM" : "PM");
  }

  @NonNull
  private static String oneDecimalOrInteger(double v, @NonNull Locale locale)
  {
    // Decide on the rounded value so 99.96 shows "100", not "100.0".
    if (Math.round(v * 10) >= 1000)
      return integer(Math.round(v), locale);
    return decimal(v, 1, locale);
  }

  @NonNull
  private static String decimal(double v, int digits, @NonNull Locale locale)
  {
    // Round first so values like -0.04 read "0.0", not "-0.0".
    final double scale = Math.pow(10, digits);
    v = Math.round(v * scale) / scale;
    if (v == 0)
      v = 0;
    final NumberFormat f = NumberFormat.getNumberInstance(locale);
    f.setMinimumFractionDigits(digits);
    f.setMaximumFractionDigits(digits);
    f.setGroupingUsed(true);
    return f.format(v);
  }

  @NonNull
  private static String integer(long v, @NonNull Locale locale)
  {
    final NumberFormat f = NumberFormat.getIntegerInstance(locale);
    f.setGroupingUsed(true);
    return f.format(v);
  }
}
