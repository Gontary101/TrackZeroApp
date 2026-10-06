package app.trackzero.follow;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.List;

/**
 * How a place to spend the night is labelled (FEAT-010, Phase 31). Offline, the map is the only source: VERIFIED only
 * for what the map marks as made for staying overnight (a campsite, a hut), and never when its access is private.
 * Everything else (a shelter) is NOT CONFIRMED. Nothing is invented. Pure.
 */
public final class Bivouac
{
  /** What the map says that limits a stay. */
  public enum Restriction
  {
    /** Opening hours as the map gives them ({@link Stop#hours}). */
    HOURS,
    FEE,
    NO_FEE,
    PRIVATE
  }

  private Bivouac() {}

  public static boolean isBivouac(@NonNull Stop s)
  {
    return s.kind == Stop.CAMPSITE || s.kind == Stop.HUT || s.kind == Stop.SHELTER;
  }

  /** A campsite or hut that is not private: the map says staying overnight is what it is for. */
  public static boolean verified(@NonNull Stop s)
  {
    return (s.kind == Stop.CAMPSITE || s.kind == Stop.HUT) && (s.flags & Stop.PRIVATE) == 0;
  }

  /** The limits the map knows, in this order: private, hours, fee. */
  @NonNull
  public static List<Restriction> restrictions(@NonNull Stop s)
  {
    final List<Restriction> out = new ArrayList<>();
    if ((s.flags & Stop.PRIVATE) != 0)
      out.add(Restriction.PRIVATE);
    if (!s.hours.isEmpty())
      out.add(Restriction.HOURS);
    if ((s.flags & Stop.FEE) != 0)
      out.add(Restriction.FEE);
    else if ((s.flags & Stop.NO_FEE) != 0)
      out.add(Restriction.NO_FEE);
    return out;
  }
}
