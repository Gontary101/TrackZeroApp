package app.trackzero.base;

import android.content.Context;
import android.location.Location;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.search.SearchEngine;
import app.organicmaps.sdk.search.SearchListener;
import app.organicmaps.sdk.search.SearchResult;
import app.trackzero.TzTrace;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline place search with CoMaps' search engine (B2). One search at a time; results arrive on the main thread,
 * growing until the engine is done.
 */
public final class TzSearch
{
  /** What a result is, for its icon. */
  public enum Kind
  {
    SUMMIT,
    WATER,
    CAFE,
    BIKE,
    PLACE
  }

  /** A place found. Immutable. */
  public static final class Place
  {
    @NonNull
    public final String name;
    /** "Summit · 1,465 m", "Square · Clermont-Ferrand"; may be empty. */
    @NonNull
    public final String subtitle;
    public final double lat;
    public final double lon;
    @NonNull
    public final Kind kind;
    /** The engine's index, for {@link #show}; valid until the next search. */
    final int index;

    Place(@NonNull String name, @NonNull String subtitle, double lat, double lon, @NonNull Kind kind, int index)
    {
      this.name = name;
      this.subtitle = subtitle;
      this.lat = lat;
      this.lon = lon;
      this.kind = kind;
      this.index = index;
    }
  }

  public interface Callback
  {
    /** All results so far; {@code done} when the engine has finished. */
    void onResults(@NonNull List<Place> places, boolean done);
  }

  @Nullable
  private static Callback sCallback;
  private static long sTimestamp;
  private static boolean sListening;
  /** A result is being shown: the map keeps its selection (the "pin") instead of dropping it like a POI tap. */
  private static boolean sShowing;
  /** The result pinned on the map, until the selection goes; for its card (B1, NAVIGATE HERE). */
  @Nullable
  private static Place sShown;
  @Nullable
  private static Runnable sShownListener;
  @NonNull
  private static List<Place> sLast = Collections.emptyList();

  /** Two results of one name nearer than this are one place. */
  private static final float SAME_PLACE_M = 300;

  private static final SearchListener LISTENER = new SearchListener() {
    @Override
    public void onResultsUpdate(@NonNull SearchResult[] results, long timestamp)
    {
      if (timestamp != sTimestamp || sCallback == null)
        return;
      final List<Place> places = new ArrayList<>(results.length);
      for (int i = 0; i < results.length; i++)
      {
        final SearchResult r = results[i];
        if (r.type != SearchResult.TYPE_RESULT || r.description == null)
          continue;
        // An unnamed place is called by its type ("Charging Station").
        final String name = r.name.isEmpty() ? r.description.localizedFeatureType : r.name;
        if (sameAsAbove(places, name, r.lat, r.lon))
          continue;
        places.add(new Place(name, subtitle(name, r.description.localizedFeatureType, r.description.description,
                                            r.description.region),
                             r.lat, r.lon, kind(SearchEngine.nativeGetResultType(i)), i));
      }
      sLast = places;
      sCallback.onResults(places, false);
    }

    @Override
    public void onResultsEnd(long timestamp)
    {
      if (timestamp == sTimestamp && sCallback != null)
        sCallback.onResults(sLast, true);
    }
  };

  private TzSearch() {}

  /** The map often has a place twice, a point and its building (a station): the better ranked one stays. */
  private static boolean sameAsAbove(@NonNull List<Place> places, @NonNull String name, double lat, double lon)
  {
    final float[] metres = new float[1];
    for (Place p : places)
    {
      if (!p.name.equals(name))
        continue;
      Location.distanceBetween(p.lat, p.lon, lat, lon, metres);
      if (metres[0] < SAME_PLACE_M)
        return true;
    }
    return false;
  }

  /**
   * Searches the downloaded maps around the rider (anywhere without a location).
   *
   * @param category {@code text} is a category name ("Water"): finds the places of that kind
   */
  public static void query(@NonNull Context context, @NonNull String text, boolean category,
                           @NonNull Callback callback)
  {
    if (!sListening)
    {
      SearchEngine.INSTANCE.addListener(LISTENER);
      sListening = true;
    }
    sCallback = callback;
    sLast = Collections.emptyList();
    sTimestamp = System.nanoTime();
    final Location here = TzLocation.last(context);
    // The engine recognises a category by its name followed by a space (CoMaps' category search).
    final String q = category ? text.trim() + " " : text;
    SearchEngine.INSTANCE.search(context, q, category, sTimestamp, here != null,
                                 here == null ? 0 : here.getLatitude(), here == null ? 0 : here.getLongitude());
  }

  /** Stops delivering results (the screen is closing). */
  public static void cancel()
  {
    sCallback = null;
    sTimestamp = 0;
    if (sListening)
    {
      SearchEngine.INSTANCE.removeListener(LISTENER);
      sListening = false;
    }
    SearchEngine.INSTANCE.cancel();
  }

  /** Centres the map on a place of the last search and marks it (the map's selection). */
  public static void show(@NonNull Place place)
  {
    if (TzTrace.on())
      TzTrace.log("search", "show " + place.name);
    // The result, not the rider: following would pull the camera back on the next fix.
    TzMap.stopFollowing();
    sShowing = true;
    sShown = place;
    SearchEngine.INSTANCE.showResult(place.index);
    if (sShownListener != null)
      sShownListener.run();
  }

  /** The result pinned on the map, or null. */
  @Nullable
  public static Place shown()
  {
    return sShown;
  }

  /** Told when the pinned result changes (one listener: the map screen); null to stop. */
  public static void setShownListener(@Nullable Runnable listener)
  {
    sShownListener = listener;
  }

  /** The map's selection went (a tap elsewhere): no pinned result any more. */
  static void selectionCleared()
  {
    // Showing a result first drops the selection before: that is not the result going.
    if (sShown == null || sShowing)
      return;
    sShown = null;
    if (sShownListener != null)
      sShownListener.run();
  }

  /** Drops the pinned result and its pin. */
  public static void clearShown()
  {
    if (TzTrace.on())
      TzTrace.log("search", "clearShown");
    sShown = null;
    Framework.nativeDeactivatePopup();
  }

  /** True once after {@link #show}: the place selection that follows is the search result's, keep it. */
  static boolean takeShowing()
  {
    final boolean showing = sShowing;
    sShowing = false;
    return showing;
  }

  /**
   * "Volcano · 940 m", "Square · Clermont-Ferrand". CoMaps' description repeats the type and joins details with
   * " • " ("Volcano • ▲ 940 m"): keep the details that are not the type, else say where the place is.
   */
  @NonNull
  static String subtitle(@NonNull String name, @Nullable String type, @Nullable String description,
                         @Nullable String region)
  {
    final List<String> parts = new ArrayList<>();
    final String kind = type == null ? "" : type.trim();
    if (!kind.isEmpty() && !kind.equals(name))
      parts.add(kind);
    boolean detail = false;
    if (description != null)
    {
      for (String part : description.split("•"))
      {
        final String p = grouped(part.replace("▲", "").trim());
        if (p.isEmpty() || p.equalsIgnoreCase(kind) || p.equals(name))
          continue;
        parts.add(p);
        detail = true;
      }
    }
    // The region is "Ceyssat, Auvergne-Rhône-Alpes, France": the town says where.
    final String town = region == null ? "" : region.split(",")[0].trim();
    if (!detail && !town.isEmpty())
      parts.add(town);
    return String.join(" · ", parts);
  }

  private static final Pattern BIG_NUMBER = Pattern.compile("\\b\\d{4,}\\b");

  /** "1691 m" → "1,691 m" (the locale's grouping, as elsewhere in the app). */
  @NonNull
  static String grouped(@NonNull String text)
  {
    final Matcher m = BIG_NUMBER.matcher(text);
    final StringBuilder out = new StringBuilder();
    while (m.find())
      m.appendReplacement(out, String.format(Locale.getDefault(), "%,d", Long.parseLong(m.group())));
    m.appendTail(out);
    return out.toString();
  }

  @NonNull
  private static Kind kind(@NonNull String type)
  {
    if (type.startsWith("natural-peak") || type.startsWith("natural-volcano") || type.startsWith("mountain_pass")
        || type.startsWith("natural-saddle"))
      return Kind.SUMMIT;
    if (type.startsWith("amenity-drinking_water") || type.startsWith("amenity-water_point")
        || type.startsWith("man_made-water_tap") || type.startsWith("natural-spring"))
      return Kind.WATER;
    if (type.startsWith("amenity-cafe"))
      return Kind.CAFE;
    if (type.startsWith("amenity-bicycle") || type.startsWith("shop-bicycle"))
      return Kind.BIKE;
    return Kind.PLACE;
  }
}
