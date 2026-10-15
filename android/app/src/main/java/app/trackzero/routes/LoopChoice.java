package app.trackzero.routes;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A loop size the rider types in L1 (beyond the chips): a distance, and a total climb. Values are in the rider's
 * units (km or mi; m or ft). Pure.
 */
public final class LoopChoice
{
  /** Shortest and longest loops asked for, in km or mi: a bike can ask for days; on foot, a long day. */
  public static final double MIN_DISTANCE = 2;
  public static final double MAX_DISTANCE_BIKE = 1000;
  public static final double MAX_DISTANCE_FOOT = 100;
  /** Total climb asked for, m or ft. */
  public static final double MAX_CLIMB = 30_000;

  private LoopChoice() {}

  /** A typed distance ("400", "12,5", "12.5"), or null when it is not a number in range. */
  @Nullable
  public static Double distance(@NonNull String text, boolean foot)
  {
    final Double v = number(text);
    if (v == null || v < MIN_DISTANCE || v > (foot ? MAX_DISTANCE_FOOT : MAX_DISTANCE_BIKE))
      return null;
    return v;
  }

  /** A typed total climb, or null when it is not a number in range (0 is flat). */
  @Nullable
  public static Double climb(@NonNull String text)
  {
    final Double v = number(text);
    if (v == null || v < 0 || v > MAX_CLIMB)
      return null;
    return v;
  }

  /** Metres up per kilometre for a total climb over a distance, both in metres. */
  public static double perKm(double climbM, double distanceM)
  {
    return distanceM <= 0 ? 0 : climbM / (distanceM / 1000);
  }

  /** How a typed value shows on its chip: whole numbers without decimals, else one decimal. */
  @NonNull
  public static String label(double v)
  {
    if (Math.abs(v - Math.rint(v)) < 1e-9)
      return String.valueOf((long) Math.rint(v));
    return String.valueOf(Math.round(v * 10) / 10.0);
  }

  @Nullable
  private static Double number(@NonNull String text)
  {
    final String t = text.trim().replace(',', '.').replace(" ", "").replace(" ", "");
    if (t.isEmpty())
      return null;
    try
    {
      final double v = Double.parseDouble(t);
      return Double.isFinite(v) ? v : null;
    }
    catch (NumberFormatException e)
    {
      return null;
    }
  }
}
