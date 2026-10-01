package app.trackzero.ride;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The recorded points of a ride: one segment per RECORDING stretch. */
public final class RideTrack
{
  public static final class Point
  {
    public final long timeMs;
    public final double lat;
    public final double lon;
    /** NaN when unknown. */
    public final double altM;

    public Point(long timeMs, double lat, double lon, double altM)
    {
      this.timeMs = timeMs;
      this.lat = lat;
      this.lon = lon;
      this.altM = altM;
    }
  }

  private final List<List<Point>> mSegments = new ArrayList<>();
  private boolean mNewSegment = true;

  /** The next point starts a new segment (no-op if the current one is still empty). */
  public void breakSegment()
  {
    mNewSegment = true;
  }

  /** Returns true when the point started a new segment. */
  public boolean add(@NonNull Point p)
  {
    final boolean started = mNewSegment;
    if (started)
    {
      mSegments.add(new ArrayList<>());
      mNewSegment = false;
    }
    mSegments.get(mSegments.size() - 1).add(p);
    return started;
  }

  /** Read-only view; segments are never empty. */
  @NonNull
  public List<List<Point>> segments()
  {
    return Collections.unmodifiableList(mSegments);
  }

  public int pointCount()
  {
    int n = 0;
    for (List<Point> s : mSegments)
      n += s.size();
    return n;
  }
}
