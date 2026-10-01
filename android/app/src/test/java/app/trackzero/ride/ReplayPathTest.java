package app.trackzero.ride;

import org.junit.Test;

import app.trackzero.geo.Geo;

import static org.junit.Assert.*;

public class ReplayPathTest
{
  private static final double LATITUDE_1000_M = Math.toDegrees(1000.0 / 6371000.0);

  private ReplayPath straightPath(double startElevation, double endElevation, double speed)
  {
    return new ReplayPath(new double[] {0, LATITUDE_1000_M}, new double[] {0, 0},
        new double[] {startElevation, endElevation}, speed);
  }

  @Test
  public void travelsNorthAtConstantSpeed()
  {
    ReplayPath path = straightPath(100, 200, 6.944);
    TzFix fix = path.fixAt(60_000, 123456);

    assertEquals(1000, path.lengthM(), 0.01);
    assertEquals(416.7, Geo.distance(0, 0, fix.lat, fix.lon), 0.5);
    assertEquals(0, fix.bearingDeg, 0.5);
    assertEquals(6.944, fix.speedMps, 0.000001);
    assertEquals(5, fix.accuracyM, 0);
    assertTrue(fix.hasSpeed);
    assertTrue(fix.hasBearing);
    assertEquals(123456L, fix.timeMs);
    assertFalse(path.isFinished(60_000));
  }

  @Test
  public void interpolatesElevationHalfway()
  {
    TzFix fix = straightPath(100, 200, 10).fixAt(50_000, 0);

    assertTrue(fix.hasAlt);
    assertEquals(150, fix.altM, 0.01);
  }

  @Test
  public void usesKnownElevationWhenOtherEndpointIsUnknown()
  {
    TzFix knownStart = straightPath(100, Double.NaN, 10).fixAt(50_000, 0);
    TzFix knownEnd = straightPath(Double.NaN, 200, 10).fixAt(50_000, 0);
    TzFix unknown = straightPath(Double.NaN, Double.NaN, 10).fixAt(50_000, 0);

    assertTrue(knownStart.hasAlt);
    assertEquals(100, knownStart.altM, 0);
    assertTrue(knownEnd.hasAlt);
    assertEquals(200, knownEnd.altM, 0);
    assertFalse(unknown.hasAlt);
  }

  @Test
  public void clampsToLastVertexAndStops()
  {
    ReplayPath path = straightPath(100, 200, 10);
    TzFix fix = path.fixAt(200_000, 987654);

    assertEquals(LATITUDE_1000_M, fix.lat, 0);
    assertEquals(0, fix.lon, 0);
    assertEquals(200, fix.altM, 0);
    assertTrue(fix.hasAlt);
    assertEquals(0, fix.speedMps, 0);
    assertTrue(fix.hasSpeed);
    assertEquals(0, fix.bearingDeg, 0.5);
    assertEquals(987654L, fix.timeMs);
    assertTrue(path.isFinished(200_000));
  }

  @Test
  public void skipsDuplicateVertexWithoutNonFiniteFixes()
  {
    ReplayPath path = new ReplayPath(new double[] {0, LATITUDE_1000_M,
        LATITUDE_1000_M, LATITUDE_1000_M}, new double[] {0, 0, 0, LATITUDE_1000_M},
        new double[] {100, 200, 200, 300}, 10);
    long endMs = (long) Math.ceil(path.lengthM() / 10) * 1000;

    for (long elapsedMs = 0; elapsedMs <= endMs; elapsedMs += 1000)
    {
      TzFix fix = path.fixAt(elapsedMs, elapsedMs);
      assertFalse(Double.isNaN(fix.lat) || Double.isInfinite(fix.lat));
      assertFalse(Double.isNaN(fix.lon) || Double.isInfinite(fix.lon));
      assertFalse(Float.isNaN(fix.bearingDeg) || Float.isInfinite(fix.bearingDeg));
    }
    TzFix corner = path.fixAt(100_000, 0);
    assertEquals(90, corner.bearingDeg, 0.5);
  }

  @Test
  public void oneHzFixesCoverRightAnglePathWithinOnePercent()
  {
    ReplayPath path = new ReplayPath(new double[] {0, LATITUDE_1000_M, LATITUDE_1000_M},
        new double[] {0, 0, LATITUDE_1000_M}, new double[] {0, 0, 0}, 6.944);
    TzFix previous = path.fixAt(0, 0);
    double travelledM = 0;
    long endMs = (long) Math.ceil(path.lengthM() / 6.944) * 1000;

    for (long elapsedMs = 1000; elapsedMs <= endMs; elapsedMs += 1000)
    {
      TzFix fix = path.fixAt(elapsedMs, elapsedMs);
      travelledM += Geo.distance(previous.lat, previous.lon, fix.lat, fix.lon);
      previous = fix;
    }

    assertEquals(path.lengthM(), travelledM, path.lengthM() * 0.01);
  }

  @Test
  public void findsCorrectSegmentWhenElapsedTimeGoesBackwards()
  {
    ReplayPath path = new ReplayPath(new double[] {0, LATITUDE_1000_M,
        LATITUDE_1000_M, LATITUDE_1000_M}, new double[] {0, 0, 0, LATITUDE_1000_M},
        new double[] {0, 100, 100, 200}, 10);
    path.fixAt(150_000, 0);
    TzFix earlier = path.fixAt(25_000, 0);
    assertEquals(250, Geo.distance(0, 0, earlier.lat, earlier.lon), 0.01);
    assertEquals(0, earlier.bearingDeg, 0.5);

    TzFix corner = path.fixAt(100_000, 0);
    assertEquals(90, corner.bearingDeg, 0.5);
    path.fixAt(300_000, 0);
    TzFix rewound = path.fixAt(50_000, 0);
    assertEquals(500, Geo.distance(0, 0, rewound.lat, rewound.lon), 0.01);
    assertEquals(10, rewound.speedMps, 0);
  }

  @Test
  public void endpointUsesLastNonZeroSegmentBearing()
  {
    ReplayPath path = new ReplayPath(new double[] {0, 0, 0},
        new double[] {0, LATITUDE_1000_M, LATITUDE_1000_M},
        new double[] {100, 200, Double.NaN}, 10);
    TzFix fix = path.fixAt(200_000, 0);

    assertEquals(0, fix.lat, 0);
    assertEquals(LATITUDE_1000_M, fix.lon, 0);
    assertEquals(90, fix.bearingDeg, 0.5);
    assertTrue(fix.hasAlt);
    assertEquals(200, fix.altM, 0);
  }

  @Test
  public void entirelyDuplicatePathIsAlreadyFinished()
  {
    ReplayPath path = new ReplayPath(new double[] {1, 1, 1}, new double[] {2, 2, 2},
        new double[] {100, 200, 300}, 10);
    TzFix fix = path.fixAt(0, 0);

    assertEquals(0, path.lengthM(), 0);
    assertTrue(path.isFinished(0));
    assertEquals(1, fix.lat, 0);
    assertEquals(2, fix.lon, 0);
    assertEquals(300, fix.altM, 0);
    assertEquals(0, fix.speedMps, 0);
    assertEquals(0, fix.bearingDeg, 0);
  }
}
