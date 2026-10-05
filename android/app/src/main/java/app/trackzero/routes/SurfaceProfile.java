package app.trackzero.routes;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What a route is made of (FEAT-006, Phase 23): the surface of the road under samples taken along it, as runs in route
 * order for the bar and as percentages for the legend. Pure.
 *
 * Sample codes are those of {@code Framework.nativeRoadSurfaces}: -1 no map at the point, 0 no surface information, 1
 * paved (good), 2 paved (bad), 3 unpaved (good), 4 unpaved (bad). Sample {@code i} is at {@code i * step}, the last one
 * at the end; each stands for the stretch halfway to its neighbours.
 */
public final class SurfaceProfile
{
  /** The classes shown, in legend order. A missing map is "no data" too, never another class. */
  public enum Surface
  {
    PAVED,
    POOR,
    GRAVEL,
    ROUGH,
    NONE
  }

  /** A stretch of one surface, in metres along the route. */
  public static final class Run
  {
    @NonNull
    public final Surface surface;
    public final double fromM;
    public final double toM;

    Run(@NonNull Surface surface, double fromM, double toM)
    {
      this.surface = surface;
      this.fromM = fromM;
      this.toM = toM;
    }

    public double lengthM()
    {
      return toM - fromM;
    }
  }

  /** Runs shorter than this share of the route join their longer neighbour on the bar. */
  static final double MIN_RUN_SHARE = 0.01;
  static final double MIN_STEP_M = 50;
  static final int MAX_SAMPLES = 3000;

  /** The bar: in route order, covering the whole route. */
  @NonNull
  public final List<Run> runs;
  /** Whole percent of the route per {@link Surface} ordinal, summing to 100 (all 0 for an empty route). */
  @NonNull
  public final int[] percent;
  /** No sample had a map: there is nothing to show but a hint to download it. */
  public final boolean noMap;
  /** Some sample had no map (it may be downloaded since). */
  public final boolean someNoMap;

  private SurfaceProfile(@NonNull List<Run> runs, @NonNull int[] percent, boolean noMap, boolean someNoMap)
  {
    this.runs = runs;
    this.percent = percent;
    this.noMap = noMap;
    this.someNoMap = someNoMap;
  }

  /**
   * Cached surfaces read with the maps {@code readWith} still stand with the maps {@code now}: unless some samples had
   * no map and the maps on the phone have changed since (one may have been downloaded).
   */
  public boolean upToDate(long readWith, long now)
  {
    return !someNoMap || readWith == now;
  }

  /** The sample step for a route this long: every 50 m, or fewer samples on a very long one (at most 3000). */
  public static double step(double lengthM)
  {
    return Math.max(MIN_STEP_M, lengthM / MAX_SAMPLES);
  }

  /** How many samples a route this long takes at {@code stepM}: one every step from the start, one at the end. */
  public static int samples(double lengthM, double stepM)
  {
    return lengthM <= 0 ? 1 : (int) Math.ceil(lengthM / stepM) + 1;
  }

  /** Where sample {@code i} is, metres along. */
  public static double at(int i, double lengthM, double stepM)
  {
    return Math.min(lengthM, i * stepM);
  }

  @NonNull
  public static Surface surface(int code)
  {
    return switch (code)
    {
      case 1 -> Surface.PAVED;
      case 2 -> Surface.POOR;
      case 3 -> Surface.GRAVEL;
      case 4 -> Surface.ROUGH;
      default -> Surface.NONE;
    };
  }

  @NonNull
  public static SurfaceProfile of(@NonNull int[] codes, double lengthM, double stepM)
  {
    final int n = codes.length;
    boolean anyMap = false;
    boolean anyMissing = false;
    for (int c : codes)
    {
      if (c < 0)
        anyMissing = true;
      else
        anyMap = true;
    }
    if (n == 0 || lengthM <= 0)
      return new SurfaceProfile(Collections.emptyList(), new int[Surface.values().length], !anyMap, anyMissing);

    // Each sample stands for the stretch halfway to its neighbours.
    final List<Run> raw = new ArrayList<>();
    final double[] metres = new double[Surface.values().length];
    for (int i = 0; i < n; i++)
    {
      final double from = i == 0 ? 0 : (at(i - 1, lengthM, stepM) + at(i, lengthM, stepM)) / 2;
      final double to = i == n - 1 ? lengthM : (at(i, lengthM, stepM) + at(i + 1, lengthM, stepM)) / 2;
      if (to <= from)
        continue;
      final Surface s = surface(codes[i]);
      metres[s.ordinal()] += to - from;
      final int last = raw.size() - 1;
      if (last >= 0 && raw.get(last).surface == s)
        raw.set(last, new Run(s, raw.get(last).fromM, to));
      else
        raw.add(new Run(s, from, to));
    }
    return new SurfaceProfile(Collections.unmodifiableList(merge(raw, lengthM * MIN_RUN_SHARE)), percent(metres),
                              !anyMap, anyMissing);
  }

  /** The shortest run under {@code minM} joins its longer neighbour, again until none is left (or one run). */
  @NonNull
  static List<Run> merge(@NonNull List<Run> in, double minM)
  {
    final List<Run> runs = new ArrayList<>(in);
    while (runs.size() > 1)
    {
      int shortest = -1;
      for (int i = 0; i < runs.size(); i++)
        if (runs.get(i).lengthM() < minM && (shortest < 0 || runs.get(i).lengthM() < runs.get(shortest).lengthM()))
          shortest = i;
      if (shortest < 0)
        break;
      final Run r = runs.get(shortest);
      final Run before = shortest > 0 ? runs.get(shortest - 1) : null;
      final Run after = shortest + 1 < runs.size() ? runs.get(shortest + 1) : null;
      final boolean intoBefore = after == null || (before != null && before.lengthM() >= after.lengthM());
      if (intoBefore)
      {
        runs.set(shortest - 1, new Run(before.surface, before.fromM, r.toM));
        runs.remove(shortest);
      }
      else
      {
        runs.set(shortest + 1, new Run(after.surface, r.fromM, after.toM));
        runs.remove(shortest);
      }
      // Neighbours of one surface left side by side become one run.
      for (int i = runs.size() - 1; i > 0; i--)
      {
        if (runs.get(i).surface == runs.get(i - 1).surface)
        {
          runs.set(i - 1, new Run(runs.get(i).surface, runs.get(i - 1).fromM, runs.get(i).toM));
          runs.remove(i);
        }
      }
    }
    return runs;
  }

  /**
   * Whole percentages summing to 100: floors, then the largest remainders get the points left. A class on the route
   * shows at least 1 (taken from the largest): the legend never says 0 % of something that is there.
   */
  @NonNull
  static int[] percent(@NonNull double[] metres)
  {
    double total = 0;
    for (double m : metres)
      total += m;
    final int[] out = new int[metres.length];
    if (total <= 0)
      return out;
    final double[] rest = new double[metres.length];
    int sum = 0;
    for (int i = 0; i < metres.length; i++)
    {
      final double exact = metres[i] / total * 100;
      out[i] = (int) Math.floor(exact);
      rest[i] = exact - out[i];
      sum += out[i];
    }
    for (int left = 100 - sum; left > 0; left--)
    {
      int best = -1;
      for (int i = 0; i < rest.length; i++)
        if (metres[i] > 0 && (best < 0 || rest[i] > rest[best]))
          best = i;
      if (best < 0)
        break;
      out[best]++;
      rest[best] = -1;
    }
    for (int i = 0; i < out.length; i++)
    {
      if (metres[i] <= 0 || out[i] > 0)
        continue;
      int largest = 0;
      for (int j = 1; j < out.length; j++)
        if (out[j] > out[largest])
          largest = j;
      out[largest]--;
      out[i] = 1;
    }
    return out;
  }
}
