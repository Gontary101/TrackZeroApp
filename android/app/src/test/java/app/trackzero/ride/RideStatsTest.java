package app.trackzero.ride;

import static app.trackzero.ride.RideFixes.at;
import static org.junit.Assert.*;

import org.junit.Test;

public class RideStatsTest
{
  /** Feeds a fix the way RideSession does while recording. */
  private static void ride(RideStats s, TzFix f)
  {
    assertTrue(s.update(f));
    s.record(f);
    s.advance(f, true);
  }

  @Test
  public void speedIsTheAverageOfTheLastThreeFixes()
  {
    final RideStats s = new RideStats();
    s.startRecording(0);
    ride(s, at(0, 0, Double.NaN, 10));
    assertEquals(10, s.speedKmh(), 1e-4);
    ride(s, at(1, 3, Double.NaN, 20));
    ride(s, at(2, 9, Double.NaN, 30));
    assertEquals(20, s.speedKmh(), 1e-4);
    ride(s, at(3, 20, Double.NaN, 40));
    assertEquals(30, s.speedKmh(), 1e-4);
    assertEquals(30, s.maxKmh(), 1e-4);
  }

  @Test
  public void slowSpeedsDisplayAsZero()
  {
    final RideStats s = new RideStats();
    s.startRecording(0);
    for (int t = 0; t < 3; t++)
      ride(s, at(t, 0, Double.NaN, 1.4));
    assertEquals(0, s.speedKmh(), 0);
  }

  @Test
  public void speedFromDistanceWhenTheFixHasNone()
  {
    final RideStats s = new RideStats();
    s.startRecording(0);
    ride(s, at(0, 0, Double.NaN, -1));
    ride(s, at(1, 10, Double.NaN, -1));
    // (0 + 36) / 2: the first fix has nothing to measure from.
    assertEquals(18, s.speedKmh(), 0.01);
  }

  @Test
  public void distanceAndTimeOnlyWhileRecording()
  {
    final RideStats s = new RideStats();
    s.startRecording(0);
    for (int t = 0; t <= 100; t++)
      ride(s, at(t, 5 * t, Double.NaN, 18));
    s.stopRecording(100_000);
    // 40 s pushing the bike 200 m: position updates, nothing counted.
    for (int t = 101; t <= 140; t++)
    {
      final TzFix f = at(t, 500 + 5 * (t - 100), Double.NaN, 18);
      assertTrue(s.update(f));
      s.advance(f, false);
    }
    s.startRecording(140_000);
    for (int t = 141; t <= 240; t++)
      ride(s, at(t, 700 + 5 * (t - 140), Double.NaN, 18));
    // The first fix after the resume starts a new stretch: 500 m + 495 m.
    assertEquals(995, s.distanceM(), 0.5);
    assertEquals(200_000, s.movingMs(240_000));
    assertEquals(995 / 200.0 * 3.6, s.avgKmh(240_000), 0.01);
  }

  @Test
  public void movingTimeRunsBetweenFixes()
  {
    final RideStats s = new RideStats();
    s.startRecording(0);
    ride(s, at(0, 0, Double.NaN, 18));
    assertEquals(2_500, s.movingMs(2_500));
  }

  @Test
  public void inaccurateFixesAreNotMeasured()
  {
    final RideStats s = new RideStats();
    s.startRecording(0);
    ride(s, at(0, 0, Double.NaN, 18));
    final TzFix bad = at(1, 400, Double.NaN, 90);
    bad.accuracyM = 50;
    assertFalse(s.update(bad));
    ride(s, at(2, 10, Double.NaN, 18));
    assertEquals(10, s.distanceM(), 0.01);
    assertEquals(18, s.maxKmh(), 1e-4);
  }

  @Test
  public void gainIgnoresAltitudeNoise()
  {
    final RideStats s = new RideStats();
    s.startRecording(0);
    for (int t = 0; t < 200; t++)
      ride(s, at(t, 5 * t, 400 + (t % 2 == 0 ? 2 : -2), 18));
    assertEquals(0, s.gainM(), 0);
  }

  @Test
  public void gainCountsAClimb()
  {
    final RideStats s = new RideStats();
    s.startRecording(0);
    int t = 0;
    // 2 km at 5 % (0.25 m per fix), then flat while the smoothing settles.
    for (; t <= 400; t++)
      ride(s, at(t, 5 * t, 100 + 0.25 * t, 18));
    for (; t <= 440; t++)
      ride(s, at(t, 5 * t, 200, 18));
    assertEquals(100, s.gainM(), 3);
  }

  @Test
  public void gradeOnAnEightPercentSlope()
  {
    final RideStats s = new RideStats();
    s.startRecording(0);
    final double v = 25 / 3.6;
    for (int t = 0; t <= 60; t++)
      ride(s, at(t, v * t, 200 + 0.08 * v * t, 25));
    assertEquals(8.0, s.gradePct(), 0.3);
    assertEquals(8.0, s.maxGradePct(), 0.3);
  }

  @Test
  public void gradeNeedsFiftyMetresAndFourKmh()
  {
    final RideStats s = new RideStats();
    s.startRecording(0);
    for (int t = 0; t <= 8; t++)
      ride(s, at(t, 5 * t, 200 + 0.4 * t, 18));
    assertTrue(Double.isNaN(s.gradePct())); // 40 m so far
    for (int t = 9; t <= 30; t++)
      ride(s, at(t, 5 * t, 200 + 0.4 * t, 18));
    assertFalse(Double.isNaN(s.gradePct()));
    for (int t = 31; t <= 34; t++)
      ride(s, at(t, 150 + 0.5 * (t - 30), 212, 3));
    assertTrue(Double.isNaN(s.gradePct()));
  }

  @Test
  public void barometerAltitudeIsCalibratedWithGps()
  {
    final RideStats s = new RideStats();
    s.startRecording(0);
    // Standard-atmosphere altitude reads 30 m low; GPS says 430 m with ±4 m noise.
    s.onBarometer(400);
    for (int t = 0; t <= 60; t++)
      ride(s, at(t, 5 * t, 430 + (t % 2 == 0 ? 4 : -4), 18));
    assertEquals(430, s.altitudeM(), 0.2);
    // After calibration the barometer drives the altitude; GPS noise no longer moves it.
    s.onBarometer(410);
    ride(s, at(61, 305, 300, 18));
    assertEquals(440, s.altitudeM(), 0.2);
  }

  @Test
  public void calibrationIsNotClimbing()
  {
    final RideStats s = new RideStats();
    s.startRecording(0);
    // Flat road, constant pressure; the first GPS altitude is 15 m low (a cold start), then right.
    s.onBarometer(400);
    for (int t = 0; t <= 60; t++)
      ride(s, at(t, 5 * t, t == 0 ? 415 : 430, 18));
    assertEquals(0, s.gainM(), 0.01);
    assertEquals(0, s.maxGradePct(), 0.01);
    assertEquals(430, s.altitudeM(), 0.3);
  }

  @Test
  public void gpsJumpIsNotRidden()
  {
    final RideStats s = new RideStats();
    s.startRecording(0);
    ride(s, at(0, 0, 400, 18));
    ride(s, at(1, 5, 400, 18));
    // 5 km in one second.
    ride(s, at(2, 5005, 400, 18));
    ride(s, at(3, 5010, 400, 18));
    assertEquals(10, s.distanceM(), 0.1);
  }

  @Test
  public void speedDropsToZeroWhenFixesStop()
  {
    final RideStats s = new RideStats();
    s.startRecording(0);
    for (int t = 0; t < 3; t++)
      ride(s, at(t, 5 * t, 400, 18));
    assertEquals(18, s.speedKmh(2_000), 0.01);
    assertEquals(18, s.speedKmh(7_000), 0.01);
    assertEquals(0, s.speedKmh(7_001), 0.01);
  }

  @Test
  public void fixesEveryThreeSecondsInTheSaver()
  {
    // 40 km/h, a fix every 3 s without GPS speed (33 m apart): all of it counts, nothing is taken for a jump.
    final RideStats s = new RideStats();
    s.startRecording(0);
    final double mps = 40 / 3.6;
    for (int t = 0; t <= 300; t += 3)
      ride(s, at(t, mps * t, Double.NaN, -1));
    assertEquals(mps * 300, s.distanceM(), 1);
    assertEquals(300_000, s.movingMs(300_000));
    assertEquals(40, s.speedKmh(), 0.5);
    // A batch of 9 s comes in late but each fix keeps its own time: the same.
    assertEquals(40, s.maxKmh(), 0.5);
  }
}
