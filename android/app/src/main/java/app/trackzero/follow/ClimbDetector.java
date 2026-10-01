package app.trackzero.follow;

import androidx.annotation.NonNull;
import app.trackzero.TzConfig;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds the climbs of a route profile (plan §11.1): from the lowest point since the last climb up to a peak, closed
 * when the road drops 10 m below the peak. Kept with 30 m gain, 500 m length and 3 % average.
 *
 * The lowest point can lie kilometres before the climb proper, at the end of a long flat approach that would pull
 * the average under 3 % and hide a real climb; so the start moves up to where the road starts rising at 3 %.
 */
public final class ClimbDetector
{
  /**
   * The start must rise at {@link TzConfig#CLIMB_MIN_GRADE} over this much road to count as climbing: short, so a
   * window reaching from the flat into the steep part does not start the climb early (on a smoothed profile).
   */
  static final double START_WINDOW_M = 100;

  private ClimbDetector() {}

  /**
   * @param ele    elevation every {@code step} metres from the start (the last sample at {@code totalM})
   * @param totalM route length
   */
  @NonNull
  public static List<Climb> detect(@NonNull float[] ele, double step, double totalM)
  {
    final List<Climb> out = new ArrayList<>();
    int low = 0;
    int peak = -1;
    for (int i = 1; i < ele.length; i++)
    {
      if (peak < 0)
      {
        if (ele[i] <= ele[low])
          low = i;
        else
          peak = i;
      }
      else if (ele[i] >= ele[peak])
      {
        peak = i;
      }
      else if (ele[peak] - ele[i] >= TzConfig.CLIMB_DROP_END_M || ele[i] < ele[low])
      {
        add(out, ele, step, totalM, low, peak);
        low = i;
        peak = -1;
      }
    }
    if (peak >= 0)
      add(out, ele, step, totalM, low, peak);
    return out;
  }

  private static void add(@NonNull List<Climb> out, @NonNull float[] ele, double step, double totalM, int low,
                          int peak)
  {
    final int window = Math.max(1, (int) Math.round(START_WINDOW_M / step));
    int start = low;
    while (start < peak)
    {
      final int to = Math.min(start + window, peak);
      if (ele[to] - ele[start] >= TzConfig.CLIMB_MIN_GRADE / 100 * (at(to, step, totalM) - at(start, step, totalM)))
        break;
      start++;
    }
    final double startAt = at(start, step, totalM);
    final double summitAt = at(peak, step, totalM);
    final double gain = ele[peak] - ele[start];
    final double length = summitAt - startAt;
    if (gain >= TzConfig.CLIMB_MIN_GAIN_M && length >= TzConfig.CLIMB_MIN_LEN_M
        && gain / length * 100 >= TzConfig.CLIMB_MIN_GRADE)
      out.add(new Climb(out.size() + 1, startAt, summitAt, ele[start], ele[peak]));
  }

  private static double at(int i, double step, double totalM)
  {
    return Math.min(i * step, totalM);
  }
}
