package app.trackzero.geo;

import java.util.ArrayDeque;

public final class DouglasPeucker
{
  private DouglasPeucker()
  {
  }

  public static boolean[] simplify(double[] x, double[] y, double eps)
  {
    int n = x.length;
    boolean[] keep = new boolean[n];
    if (n <= 2)
    {
      java.util.Arrays.fill(keep, true);
      return keep;
    }
    keep[0] = keep[n - 1] = true;
    ArrayDeque<int[]> stack = new ArrayDeque<>();
    stack.push(new int[] {0, n - 1});
    while (!stack.isEmpty())
    {
      int[] segment = stack.pop();
      int a = segment[0];
      int b = segment[1];
      double dmax = 0;
      int index = -1;
      for (int i = a + 1; i < b; i++)
      {
        double d = segDist(x[i], y[i], x[a], y[a], x[b], y[b]);
        if (d > dmax)
        {
          dmax = d;
          index = i;
        }
      }
      if (dmax > eps)
      {
        keep[index] = true;
        stack.push(new int[] {a, index});
        stack.push(new int[] {index, b});
      }
    }
    return keep;
  }

  static double segDist(double px, double py, double ax, double ay, double bx, double by)
  {
    double dx = bx - ax;
    double dy = by - ay;
    double len2 = dx * dx + dy * dy;
    double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / len2));
    double qx = ax + t * dx - px;
    double qy = ay + t * dy - py;
    return Math.sqrt(qx * qx + qy * qy);
  }

  public static int[] simplifyLatLon(double[] lat, double[] lon, double epsM)
  {
    if (lat.length <= 2)
    {
      return allIndices(lat.length);
    }
    double[] x = new double[lat.length];
    double[] y = new double[lat.length];
    project(lat, lon, x, y);
    return indices(simplify(x, y, epsM));
  }

  public static int[] simplifyToMaxPoints(double[] lat, double[] lon, int maxPoints)
  {
    if (lat.length <= 2 || lat.length <= maxPoints)
    {
      return allIndices(lat.length);
    }
    double[] x = new double[lat.length];
    double[] y = new double[lat.length];
    project(lat, lon, x, y);
    double eps = 1;
    int[] kept;
    do
    {
      kept = indices(simplify(x, y, eps));
      eps *= 2;
    }
    while (kept.length > maxPoints);
    return kept;
  }

  private static void project(double[] lat, double[] lon, double[] x, double[] y)
  {
    double lonScale = Math.cos(Math.toRadians(lat[0])) * 111320;
    for (int i = 0; i < lat.length; i++)
    {
      x[i] = (lon[i] - lon[0]) * lonScale;
      y[i] = (lat[i] - lat[0]) * 110540;
    }
  }

  private static int[] allIndices(int n)
  {
    int[] result = new int[n];
    for (int i = 0; i < n; i++)
    {
      result[i] = i;
    }
    return result;
  }

  private static int[] indices(boolean[] keep)
  {
    int count = 0;
    for (boolean kept : keep)
    {
      if (kept)
      {
        count++;
      }
    }
    int[] result = new int[count];
    int next = 0;
    for (int i = 0; i < keep.length; i++)
    {
      if (keep[i])
      {
        result[next++] = i;
      }
    }
    return result;
  }
}
