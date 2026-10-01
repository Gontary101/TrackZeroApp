package app.trackzero.follow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.regex.Pattern;

/** Climb names from GPX waypoints, which are often numbered ("1 — Col de Ceyssat") or routing markers ("via12"). */
public final class ClimbNames
{
  /** Points a route planner adds to shape the track: not names. */
  private static final Pattern MARKER =
      Pattern.compile("(?i)(via|from|to|start|end|finish|wp|waypoint|point)\\s*\\d*");
  /** A list number ("1 — ", "2. ", "03) ") or letters before a spaced dash ("A/B — ", not "D-Day"). */
  private static final Pattern NUMBERING =
      Pattern.compile("^(?:[0-9]+\\s*[.)\\-–—:]|[A-Z](?:/[A-Z])?\\s+[-–—])\\s*");

  private ClimbNames() {}

  /** The name a waypoint gives a climb, or null when it is a routing marker or empty. */
  @Nullable
  public static String fromWaypoint(@Nullable String name)
  {
    if (name == null)
      return null;
    final String trimmed = name.trim();
    if (trimmed.isEmpty() || MARKER.matcher(trimmed).matches())
      return null;
    final String clean = NUMBERING.matcher(trimmed).replaceFirst("").trim();
    return clean.isEmpty() ? null : clean;
  }
}
