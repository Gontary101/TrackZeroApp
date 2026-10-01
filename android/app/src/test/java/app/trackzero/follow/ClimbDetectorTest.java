package app.trackzero.follow;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

public class ClimbDetectorTest
{
  static final double STEP_M = 50;
  private static final double GRADE_TOLERANCE = 0.3; // Percentage points, as required for average grade.

  /** Each pair is a segment's length in metres and grade in percent; lengths are multiples of STEP_M. */
  static float[] profile(double... segments)
  {
    int samples = 1;
    for (int i = 0; i < segments.length; i += 2)
      samples += (int) Math.round(segments[i] / STEP_M);
    float[] ele = new float[samples];
    int index = 0;
    double altitude = 0;
    for (int i = 0; i < segments.length; i += 2)
    {
      int count = (int) Math.round(segments[i] / STEP_M);
      for (int j = 0; j < count; j++)
      {
        altitude += STEP_M * segments[i + 1] / 100;
        ele[++index] = (float) altitude;
      }
    }
    return ele;
  }

  private static List<Climb> detect(float[] ele)
  {
    return ClimbDetector.detect(ele, STEP_M, STEP_M * (ele.length - 1));
  }

  @Test
  public void singleClimbAfterFlatApproachHasExpectedSummitGainAndGrade()
  {
    float[] ele = profile(1000, 0, 2000, 6, 1000, -6);
    List<Climb> climbs = detect(ele);
    assertEquals(1, climbs.size());
    Climb climb = climbs.get(0);
    assertEquals(1, climb.index);
    assertEquals(1000, climb.startAt, STEP_M);
    assertEquals(3000, climb.summitAt, STEP_M);
    assertEquals(120, climb.summitAlt - climb.startAlt, 0.01); // Float elevation rounding.
    assertEquals(6, climb.avgGradePct, GRADE_TOLERANCE);
  }

  @Test
  public void longShallowApproachIsTrimmedToStartOfSteepClimb()
  {
    List<Climb> climbs = detect(profile(5000, 0.5, 2000, 8));
    assertEquals(1, climbs.size());
    Climb climb = climbs.get(0);
    assertEquals(5000, climb.startAt, STEP_M);
    assertTrue(climb.startAt > 0);
    assertEquals(8, climb.avgGradePct, GRADE_TOLERANCE);
  }

  @Test
  public void climbShorterThanMinimumLengthIsRejected()
  {
    // 40 m gain and 10% grade pass those thresholds, but 400 m is below 500 m.
    assertTrue(detect(profile(400, 10)).isEmpty());
  }

  @Test
  public void climbBelowMinimumGradeIsRejected()
  {
    assertTrue(detect(profile(1000, 2)).isEmpty());
    // Isolate grade rejection with 40 m gain, above the 30 m minimum.
    assertTrue(detect(profile(2000, 2)).isEmpty());
  }

  @Test
  public void climbBelowMinimumGainIsRejected()
  {
    // 800 m at 3.5% gains only 28 m, below the 30 m minimum.
    assertTrue(detect(profile(800, 3.5)).isEmpty());
  }

  @Test
  public void fifteenMetreDescentSeparatesTwoNumberedClimbs()
  {
    // A 500 m descent at -3% drops 15 m, exceeding the 10 m separation threshold.
    List<Climb> climbs = detect(profile(1000, 0, 2000, 6, 500, -3, 2000, 6));
    assertEquals(2, climbs.size());
    assertEquals(1, climbs.get(0).index);
    assertEquals(2, climbs.get(1).index);
    assertEquals(3000, climbs.get(0).summitAt, 0);
    assertEquals(3500, climbs.get(1).startAt, 0);
    assertEquals(5500, climbs.get(1).summitAt, 0);
  }

  @Test
  public void fiveMetreDipKeepsBothAscentsInOneClimb()
  {
    // A 500 m descent at -1% drops only 5 m, below the 10 m separation threshold.
    List<Climb> climbs = detect(profile(1000, 0, 2000, 6, 500, -1, 2000, 6));
    assertEquals(1, climbs.size());
    assertEquals(1, climbs.get(0).index);
    assertEquals(1000, climbs.get(0).startAt, STEP_M);
    assertEquals(5500, climbs.get(0).summitAt, 0);
  }

  @Test
  public void climbStillAscendingAtRouteEndClosesAtTotalDistance()
  {
    float[] ele = profile(1000, 0, 2000, 6);
    double totalM = STEP_M * (ele.length - 1);
    List<Climb> climbs = ClimbDetector.detect(ele, STEP_M, totalM);
    assertEquals(1, climbs.size());
    assertEquals(totalM, climbs.get(0).summitAt, 0);
  }

  @Test
  public void finalSampleBeforeNextStepDoesNotPutSummitBeyondRouteEnd()
  {
    float[] ele = profile(1000, 0, 2000, 6);
    // The final interval is 30 m instead of 50 m.
    double totalM = STEP_M * (ele.length - 1) - 20;
    ele[ele.length - 1] -= 20 * 6 / 100.0f;
    List<Climb> climbs = ClimbDetector.detect(ele, STEP_M, totalM);
    assertEquals(1, climbs.size());
    assertTrue(climbs.get(0).summitAt <= totalM);
    assertEquals(totalM, climbs.get(0).summitAt, 0);
  }
}
