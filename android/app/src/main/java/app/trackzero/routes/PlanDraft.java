package app.trackzero.routes;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * A route being planned on the map (FEAT-002, Phase 28): where it starts (the rider, or a picked point), up to eight
 * waypoints and a destination, with an undo of whole states. Pure.
 *
 * The planned points are the waypoints then the destination, in riding order; the card lists them and a row index is an
 * index into {@link #points()}.
 */
public final class PlanDraft
{
  public static final int MAX_WAYPOINTS = 8;
  static final int UNDO_DEPTH = 20;

  /** A point of the plan. Immutable; the name is looked up after it was placed. */
  public static final class Point
  {
    public final double lat;
    public final double lon;
    /** The street or place there; null until known. */
    @Nullable
    public final String name;

    public Point(double lat, double lon, @Nullable String name)
    {
      this.lat = lat;
      this.lon = lon;
      this.name = name;
    }

    @NonNull
    Point named(@Nullable String n)
    {
      return new Point(lat, lon, n);
    }
  }

  /** A whole state, for undo. */
  private static final class State
  {
    @Nullable
    final Point start;
    @NonNull
    final List<Point> points;

    State(@Nullable Point start, @NonNull List<Point> points)
    {
      this.start = start;
      this.points = points;
    }
  }

  /** Null: from the rider's location. */
  @Nullable
  private Point mStart;
  /** The waypoints, then the destination (the last one). */
  @NonNull
  private List<Point> mPoints = new ArrayList<>();
  private final Deque<State> mUndo = new ArrayDeque<>();

  /** Null: the plan starts where the rider is. */
  @Nullable
  public Point start()
  {
    return mStart;
  }

  /** The waypoints then the destination, in riding order. */
  @NonNull
  public List<Point> points()
  {
    return Collections.unmodifiableList(mPoints);
  }

  @Nullable
  public Point destination()
  {
    return mPoints.isEmpty() ? null : mPoints.get(mPoints.size() - 1);
  }

  public int waypointCount()
  {
    return Math.max(0, mPoints.size() - 1);
  }

  public boolean isEmpty()
  {
    return mPoints.isEmpty();
  }

  /**
   * A tap on the map: the destination; with one already, the old destination becomes the last waypoint and the tap the
   * new destination. False (nothing changes) when the plan has its eight waypoints already.
   */
  public boolean tap(double lat, double lon)
  {
    if (waypointCount() >= MAX_WAYPOINTS)
      return false;
    remember();
    mPoints.add(new Point(lat, lon, null));
    return true;
  }

  /** Moves point {@code i} (a dragged pin). */
  public void move(int i, double lat, double lon)
  {
    remember();
    mPoints.set(i, new Point(lat, lon, null));
  }

  /** Removes point {@code i}; removing the destination makes the last waypoint the destination. */
  public void remove(int i)
  {
    remember();
    mPoints.remove(i);
  }

  /** Moves point {@code from} to position {@code to} (a dragged row). */
  public void reorder(int from, int to)
  {
    if (from == to)
      return;
    remember();
    final Point p = mPoints.remove(from);
    mPoints.add(to, p);
  }

  /** The plan starts at a picked point instead of the rider ({@code null}: the rider again). */
  public void setStart(@Nullable Point start)
  {
    remember();
    mStart = start;
  }

  /** A name found for point {@code i} placed at ({@code lat}, {@code lon}): kept only if the point is still there. */
  public void name(int i, double lat, double lon, @NonNull String name)
  {
    if (i < mPoints.size() && mPoints.get(i).lat == lat && mPoints.get(i).lon == lon)
      mPoints.set(i, mPoints.get(i).named(name));
  }

  /** The start's name, if it is still where it was. */
  public void nameStart(double lat, double lon, @NonNull String name)
  {
    if (mStart != null && mStart.lat == lat && mStart.lon == lon)
      mStart = mStart.named(name);
  }

  public boolean canUndo()
  {
    return !mUndo.isEmpty();
  }

  /** Back to the state before the last change. */
  public void undo()
  {
    final State s = mUndo.pollFirst();
    if (s == null)
      return;
    mStart = s.start;
    mPoints = new ArrayList<>(s.points);
  }

  private void remember()
  {
    mUndo.addFirst(new State(mStart, new ArrayList<>(mPoints)));
    while (mUndo.size() > UNDO_DEPTH)
      mUndo.removeLast();
  }
}
