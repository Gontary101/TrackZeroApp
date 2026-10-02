package app.trackzero.follow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** A path from the bicycle router, ready to follow: its line, its turns (ending with ARRIVE) and its altitudes. */
public final class RoutedPath
{
  @NonNull
  public final FollowRoute line;
  @NonNull
  final List<RouteManeuver> maneuvers;
  /** Metres at each point of {@link #line}; null without altitudes. */
  @Nullable
  public final double[] ele;

  public RoutedPath(@NonNull FollowRoute line, @NonNull List<RouteManeuver> maneuvers, @Nullable double[] ele)
  {
    this.line = line;
    this.maneuvers = maneuvers;
    this.ele = ele;
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
