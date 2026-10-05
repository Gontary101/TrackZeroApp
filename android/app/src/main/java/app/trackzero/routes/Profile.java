package app.trackzero.routes;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * How the rider gets about (plan v2 Phase 22): it decides the paths the router works out (to the start of a track,
 * back onto it, to a place) and the time estimates. A ride's profile is fixed at START. Labels, descriptions and icons
 * are resources ({@code ui/TzProfiles}). The ordinal is what the native router is given.
 */
public enum Profile
{
  ROAD("road", false),
  GRAVEL("gravel", false),
  WALK("walk", true),
  HIKE("hike", true);

  /** What is stored (prefs, journal, rides.json, routes.json). */
  @NonNull
  public final String id;
  /** On foot rather than on a bike. */
  public final boolean foot;

  Profile(@NonNull String id, boolean foot)
  {
    this.id = id;
    this.foot = foot;
  }

  /** The profile stored as {@code id}; ROAD when unknown or missing (data written before profiles existed). */
  @NonNull
  public static Profile of(@Nullable String id)
  {
    for (Profile p : values())
      if (p.id.equals(id))
        return p;
    return ROAD;
  }

  /** The ones switched on so far, in the order the sheet lists them. */
  @NonNull
  public static List<Profile> available()
  {
    final List<Profile> on = new ArrayList<>();
    on.add(ROAD);
    on.add(GRAVEL);
    return Collections.unmodifiableList(on);
  }
}
