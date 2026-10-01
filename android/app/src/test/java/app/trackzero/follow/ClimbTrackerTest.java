package app.trackzero.follow;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class ClimbTrackerTest
{
  private static final double STEP_M = ClimbDetectorTest.STEP_M;
  private static final double SPEED_MPS = 4;
  private static final long SECOND_MS = 1000;
  private static final int MINIMUM_MOVING_SECONDS = 60;
  private static final double ETA_RELATIVE_TOLERANCE = 0.02; // Allow 2% ETA error.

  private float[] mEle;
  private List<Climb> mClimbs;
  private ClimbTracker mTracker;

  @Before
  public void setUpTwoClimbProfile()
  {
    // A 15 m descent and 500 m flat separate the two 2 km climbs at 6%.
    mEle = ClimbDetectorTest.profile(1000, 0, 2000, 6, 500, -3, 500, 0, 2000, 6, 500, -6);
    mClimbs = ClimbDetector.detect(mEle, STEP_M, STEP_M * (mEle.length - 1));
    assertEquals(2, mClimbs.size());
    mTracker = new ClimbTracker(mClimbs, mEle, STEP_M);
  }

  @Test
  public void beforeFirstClimbShowsUpcomingClimbWithoutGainOrEta()
  {
    double along = mClimbs.get(0).startAt - STEP_M;
    mTracker.update(along, 0);
    assertSame(mClimbs.get(0), mTracker.shown());
    assertEquals(1, mTracker.shown().index);
    assertFalse(mTracker.onClimb());
    assertEquals(0, mTracker.gainedM(), 0);
    assertEquals(mClimbs.get(0).summitAt - along, mTracker.toSummitM(), 0);
    assertTrue(Double.isNaN(mTracker.summitInSeconds()));
  }

  @Test
  public void onFirstClimbTracksGainAndEstimatesSummitAfterOneMovingMinute()
  {
    Climb climb = mClimbs.get(0);
    // Approach moving time must not count toward the minute spent on the climb.
    long entryMovingMs = 120 * SECOND_MS;
    mTracker.update(climb.startAt - STEP_M, entryMovingMs - SECOND_MS);
    for (int seconds = 0; seconds <= 2 * MINIMUM_MOVING_SECONDS; seconds++)
    {
      double along = climb.startAt + seconds * SPEED_MPS;
      mTracker.update(along, entryMovingMs + seconds * SECOND_MS);
      assertSame(climb, mTracker.shown());
      assertTrue(mTracker.onClimb());
      // The climb rises 3 m per 50 m sample; interpolate independently of profileAlt().
      int sample = (int) (along / STEP_M);
      double fraction = (along - sample * STEP_M) / STEP_M;
      double expectedAltitude = mEle[sample] + fraction * (mEle[sample + 1] - mEle[sample]);
      assertEquals(expectedAltitude - climb.startAlt, mTracker.gainedM(), 0.01); // Float rounding.
      double remaining = climb.summitAt - along;
      assertEquals(remaining, mTracker.toSummitM(), 0);
      if (seconds < MINIMUM_MOVING_SECONDS)
        assertTrue(Double.isNaN(mTracker.summitInSeconds()));
      else
      {
        double expectedSeconds = remaining / SPEED_MPS;
        assertEquals(expectedSeconds, mTracker.summitInSeconds(),
                     expectedSeconds * ETA_RELATIVE_TOLERANCE);
      }
    }
  }

  @Test
  public void betweenClimbsShowsSecondClimbAndResetsItsEntryMovingTime()
  {
    Climb first = mClimbs.get(0);
    mTracker.update(first.startAt, 0);
    for (int seconds = 1; seconds <= MINIMUM_MOVING_SECONDS; seconds++)
      mTracker.update(first.startAt + seconds * SPEED_MPS, seconds * SECOND_MS);
    assertFalse(Double.isNaN(mTracker.summitInSeconds()));

    Climb second = mClimbs.get(1);
    double between = (first.summitAt + second.startAt) / 2;
    long betweenMovingMs = (long) ((between - first.startAt) / SPEED_MPS * SECOND_MS);
    mTracker.update(between, betweenMovingMs);
    assertSame(second, mTracker.shown());
    assertEquals(2, mTracker.shown().index);
    assertFalse(mTracker.onClimb());
    assertEquals(0, mTracker.gainedM(), 0);
    assertEquals(second.summitAt - between, mTracker.toSummitM(), 0);
    assertTrue(Double.isNaN(mTracker.summitInSeconds()));

    long entryMovingMs = (long) ((second.startAt - first.startAt) / SPEED_MPS * SECOND_MS);
    for (int seconds = 0; seconds <= MINIMUM_MOVING_SECONDS; seconds++)
    {
      double along = second.startAt + seconds * SPEED_MPS;
      mTracker.update(along, entryMovingMs + seconds * SECOND_MS);
      assertSame(second, mTracker.shown());
      assertTrue(mTracker.onClimb());
      if (seconds < MINIMUM_MOVING_SECONDS)
        assertTrue(Double.isNaN(mTracker.summitInSeconds()));
      else
      {
        double expectedSeconds = (second.summitAt - along) / SPEED_MPS;
        assertEquals(expectedSeconds, mTracker.summitInSeconds(),
                     expectedSeconds * ETA_RELATIVE_TOLERANCE);
      }
    }
  }

  @Test
  public void afterLastSummitShowsNoClimbOrRemainingDistance()
  {
    Climb last = mClimbs.get(1);
    mTracker.update(last.startAt, 0);
    double along = last.summitAt + STEP_M;
    long movingMs = (long) ((along - last.startAt) / SPEED_MPS * SECOND_MS);
    mTracker.update(along, movingMs);
    assertNull(mTracker.shown());
    assertFalse(mTracker.onClimb());
    assertTrue(Double.isNaN(mTracker.toSummitM()));
    assertTrue(Double.isNaN(mTracker.summitInSeconds()));
  }

  @Test
  public void profileAltitudeInterpolatesHalfwayBetweenSamples()
  {
    int sample = (int) (mClimbs.get(0).startAt / STEP_M);
    double halfway = (sample + 0.5) * STEP_M;
    double expectedAltitude = (mEle[sample] + mEle[sample + 1]) / 2.0;
    assertTrue(mEle[sample + 1] > mEle[sample]);
    assertEquals(expectedAltitude, mTracker.profileAlt(halfway), 0.0001); // Interpolation rounding.
  }
}
