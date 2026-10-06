package app.trackzero.follow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * A path from the router, ready to follow: its line, its turns (ending with ARRIVE) and its altitudes. A path to a
 * place a bike cannot reach (Phase 27) is ridden to where the bike is left, then walked: {@link #walkFromM}.
 */
public final class RoutedPath
{
  @NonNull
  public final FollowRoute line;
  @NonNull
  final List<RouteManeuver> maneuvers;
  /** Metres at each point of {@link #line}; null without altitudes. */
  @Nullable
  public final double[] ele;
  /** Where the walking starts, metres along {@link #line} (the bike is left there); -1: no walking. */
  public final double walkFromM;

  public RoutedPath(@NonNull FollowRoute line, @NonNull List<RouteManeuver> maneuvers, @Nullable double[] ele)
  {
    this(line, maneuvers, ele, -1);
  }

  public RoutedPath(@NonNull FollowRoute line, @NonNull List<RouteManeuver> maneuvers, @Nullable double[] ele,
                    double walkFromM)
  {
    this.line = line;
    this.maneuvers = maneuvers;
    this.ele = ele;
    this.walkFromM = walkFromM;
  }

  /** Part bike, part on foot. */
  public boolean bikeAndWalk()
  {
    return walkFromM >= 0;
  }

  /** The ridden part's {lat[], lon[]}, to where the bike is left (the whole line without walking). */
  @NonNull
  public double[][] ridePart()
  {
    return part(0, walkIndex());
  }

  /** The walked part's {lat[], lon[]}, from where the bike is left; empty arrays without walking. */
  @NonNull
  public double[][] walkPart()
  {
    if (!bikeAndWalk())
      return new double[][] {new double[0], new double[0]};
    return part(walkIndex(), line.size() - 1);
  }

  /**
   * The walked part as a path of its own, starting at 0 (its turns shifted): what is joined to a new ride to where the
   * bike is left after the rider leaves the path before it. Null without walking.
   */
  @Nullable
  RoutedPath walkTail()
  {
    if (!bikeAndWalk())
      return null;
    final int from = walkIndex();
    final double[][] walk = walkPart();
    if (walk[0].length < 2)
      return null;
    final List<RouteManeuver> tail = new ArrayList<>();
    for (RouteManeuver m : maneuvers)
      if (m.at > walkFromM)
        tail.add(new RouteManeuver(m.type, m.at - walkFromM, m.exit, m.street == null ? "" : m.street));
    double[] tailEle = null;
    if (ele != null)
    {
      tailEle = new double[walk[0].length];
      System.arraycopy(ele, from, tailEle, 0, tailEle.length);
    }
    return new RoutedPath(new FollowRoute(walk[0], walk[1]), tail, tailEle);
  }

  /** The point where the bike is left (a point of the line: the planner puts one there). */
  private int walkIndex()
  {
    if (!bikeAndWalk())
      return line.size() - 1;
    final double[] d = line.distances();
    int i = 0;
    while (i < d.length - 1 && d[i] < walkFromM - 0.01)
      i++;
    return i;
  }

  @NonNull
  private double[][] part(int from, int to)
  {
    final double[] lat = line.lats(), lon = line.lons();
    final int n = Math.max(0, to - from + 1);
    final double[] la = new double[n], lo = new double[n];
    System.arraycopy(lat, from, la, 0, n);
    System.arraycopy(lon, from, lo, 0, n);
    return new double[][] {la, lo};
  }

  /**
   * Fresh maneuvers for a new engine (they carry announcement flags), the last one as {@code end}: ARRIVE at a
   * place, JOIN where a leg reaches the track.
   */
  @NonNull
  List<RouteManeuver> maneuvers(@NonNull Maneuver end)
  {
    final List<RouteManeuver> out = new ArrayList<>(maneuvers.size());
    for (int i = 0; i < maneuvers.size(); i++)
    {
      final RouteManeuver m = maneuvers.get(i);
      final Maneuver type = i == maneuvers.size() - 1 ? end : m.type;
      out.add(new RouteManeuver(type, m.at, m.exit, m.street == null ? "" : m.street));
    }
    return out;
  }
}
