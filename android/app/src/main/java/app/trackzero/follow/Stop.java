package app.trackzero.follow;

import androidx.annotation.NonNull;

/** A place to stop at on a ride (FEAT-007, Phase 30): water, a repair point, a place to sleep (Phase 31). Immutable. */
public final class Stop
{
  /** Kinds, as the map's types read them (see {@code Framework.nativePoisAlong}). */
  public static final int DRINKING_WATER = 1;
  public static final int WATER_POINT = 2;
  public static final int WATER_TAP = 3;
  public static final int SPRING = 4;
  public static final int REPAIR_STATION = 5;
  public static final int BIKE_SHOP = 6;
  public static final int CAMPSITE = 7;
  public static final int HUT = 8;
  public static final int SHELTER = 9;

  /** What the map says (flags): a fee, no fee, private access. */
  public static final int FEE = 1;
  public static final int NO_FEE = 2;
  public static final int PRIVATE = 4;

  /** The map's name, or "" when it has none. */
  @NonNull
  public final String name;
  public final int kind;
  public final double lat;
  public final double lon;
  /** Opening hours as the map gives them ("May-Oct"), or "". */
  @NonNull
  public final String hours;
  public final int flags;

  public Stop(@NonNull String name, int kind, double lat, double lon)
  {
    this(name, kind, lat, lon, "", 0);
  }

  public Stop(@NonNull String name, int kind, double lat, double lon, @NonNull String hours, int flags)
  {
    this.name = name;
    this.kind = kind;
    this.lat = lat;
    this.lon = lon;
    this.hours = hours;
    this.flags = flags;
  }

  /** The same place (a list row and an added stop). */
  public boolean same(@NonNull Stop o)
  {
    return Math.abs(lat - o.lat) < 1e-7 && Math.abs(lon - o.lon) < 1e-7;
  }

  @NonNull
  @Override
  public String toString()
  {
    return "Stop(" + kind + " " + name + ")";
  }
}
