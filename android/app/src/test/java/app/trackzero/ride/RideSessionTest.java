package app.trackzero.ride;

import static app.trackzero.ride.RideFixes.at;
import static org.junit.Assert.*;

import app.trackzero.routes.Profile;
import org.junit.Test;

public class RideSessionTest
{
  private static RideSession started(boolean autoPause)
  {
    final RideSession s = new RideSession(autoPause);
    s.start(1_780_000_000_000L, 0, "route-1", "Clermont, Loop", Profile.GRAVEL);
    return s;
  }

  @Test
  public void stateTransitions()
  {
    final RideSession s = new RideSession(true);
    assertEquals(RideSession.State.IDLE, s.state());
    s.onFix(at(0, 0, 400, 18));
    assertNull(s.lastFix());

    s.start(1_780_000_000_000L, 0, null, "Free ride", Profile.ROAD);
    assertEquals(RideSession.State.RECORDING, s.state());
    s.pause(10_000);
    assertEquals(RideSession.State.PAUSED, s.state());
    s.resume(20_000);
    assertEquals(RideSession.State.RECORDING, s.state());
    s.end(30_000);
    assertEquals(RideSession.State.SAVED, s.state());
    assertEquals(20_000, s.stats().movingMs(99_000));

    s.onFix(at(31, 0, 400, 18));
    assertNull(s.lastFix());
  }

  @Test
  public void manualPauseNeverResumesByItself()
  {
    final RideSession s = started(true);
    s.onFix(at(0, 0, 400, 18));
    s.pause(1_000);
    for (int t = 2; t < 30; t++)
      s.onFix(at(t, 5 * t, 400, 30));
    assertEquals(RideSession.State.PAUSED, s.state());
    assertEquals(1, s.track().pointCount());
  }

  @Test
  public void autoPauseAfterFiveSlowSecondsAndResumeAboveFiveKmh()
  {
    final RideSession s = started(true);
    int t = 0;
    for (; t < 20; t++)
      s.onFix(at(t, 5 * t, 400, 18));
    // Stopped: the 3-fix average reads 12, 6, then 0 km/h at t = 22.
    for (; t < 27; t++)
    {
      s.onFix(at(t, 100, 400, 0));
      assertEquals("t=" + t, RideSession.State.RECORDING, s.state());
    }
    s.onFix(at(27, 100, 400, 0));
    assertEquals(RideSession.State.AUTO_PAUSED, s.state());
    // Moving time ends when the rider slowed down, not when the pause was detected.
    assertEquals(22_000, s.stats().movingMs(60_000));

    // Walking pace (4 km/h) does not resume.
    for (t = 28; t < 40; t++)
      s.onFix(at(t, 100 + 1.1 * (t - 27), 400, 4));
    assertEquals(RideSession.State.AUTO_PAUSED, s.state());

    // Riding again: 4, 4, 10 → 6 km/h at t = 40.
    s.onFix(at(40, 120, 400, 10));
    assertEquals(RideSession.State.RECORDING, s.state());
    s.onFix(at(41, 123, 400, 10));
    assertEquals(2, s.track().segments().size());
    assertEquals(23_000, s.stats().movingMs(41_000));
  }

  @Test
  public void noAutoPauseWhenDisabled()
  {
    final RideSession s = started(false);
    for (int t = 0; t < 60; t++)
      s.onFix(at(t, 0, 400, 0));
    assertEquals(RideSession.State.RECORDING, s.state());
    assertEquals(59_000, s.stats().movingMs(59_000));
  }

  @Test
  public void turningAutoPauseOffResumes()
  {
    final RideSession s = started(true);
    for (int t = 0; t < 10; t++)
      s.onFix(at(t, 0, 400, 0));
    assertEquals(RideSession.State.AUTO_PAUSED, s.state());
    s.setAutoPause(false, 10_000);
    assertEquals(RideSession.State.RECORDING, s.state());
  }

  @Test
  public void journalRestoresTheRidePaused()
  {
    final RideSession s = started(true);
    for (int t = 0; t <= 100; t++)
      s.onFix(at(t, 5 * t, 400 + 0.2 * t, 18));
    final StringBuilder file = new StringBuilder(s.drainJournal(100_000));
    s.pause(100_000);
    s.resume(110_000);
    for (int t = 111; t <= 150; t++)
      s.onFix(at(t, 500 + 5 * (t - 110), 420, 18));
    file.append(s.drainJournal(150_000));
    // A crash in the middle of the next write.
    file.append("P,1780000151000,45.0");

    final RideJournal.Content c = RideJournal.parse(file.toString());
    assertNotNull(c);
    assertEquals("route-1", c.routeId);
    assertEquals("Clermont, Loop", c.name);
    assertEquals(1_780_000_000_000L, c.startedAt);
    assertEquals(Profile.GRAVEL, c.profile);

    final RideSession r = RideSession.restore(c, true);
    assertEquals(RideSession.State.PAUSED, r.state());
    assertEquals("A restored ride keeps its profile", Profile.GRAVEL, r.profile());
    assertEquals(s.stats().distanceM(), r.stats().distanceM(), 0.1);
    assertEquals(s.stats().movingMs(150_000), r.stats().movingMs(500_000));
    assertEquals(s.stats().gainM(), r.stats().gainM(), 0.1);
    assertEquals(s.stats().maxKmh(), r.stats().maxKmh(), 0.01);
    assertEquals(2, r.track().segments().size());
    assertEquals(s.track().pointCount(), r.track().pointCount());

    // Riding on after the restore starts a third segment and adds to the restored totals.
    r.resume(600_000);
    r.onFix(at(600, 800, 420, 18));
    r.onFix(at(601, 805, 420, 18));
    assertEquals(3, r.track().segments().size());
    assertEquals(s.stats().distanceM() + 5, r.stats().distanceM(), 0.1);
  }

  /** On foot (Phase 25): a slow walk is not a stop; a stop needs 10 s under 1 km/h; walking again resumes. */
  @Test
  public void onFootAutoPauseIsForWalkingSpeeds()
  {
    final RideSession walk = new RideSession(true);
    walk.start(1_780_000_000_000L, 0, null, "Walk", Profile.WALK);
    final RideSession bike = new RideSession(true);
    bike.start(1_780_000_000_000L, 0, null, "Ride", Profile.ROAD);
    // 2 km/h: 0.556 m a second, for 30 s.
    double north = 0;
    int t = 0;
    for (; t < 30; t++, north += 2 / 3.6)
    {
      walk.onFix(at(t, north, 400, 2));
      bike.onFix(at(t, north, 400, 2));
    }
    assertEquals(RideSession.State.RECORDING, walk.state());
    assertEquals("Under 3 km/h a bike stops", RideSession.State.AUTO_PAUSED, bike.state());

    // Standing: 10 s from the first fix under 1 km/h.
    final int stopped = t;
    for (; t < stopped + 10; t++)
    {
      walk.onFix(at(t, north, 400, 0));
      assertEquals("t=" + t, RideSession.State.RECORDING, walk.state());
    }
    for (; t < stopped + 12; t++)
      walk.onFix(at(t, north, 400, 0));
    assertEquals(RideSession.State.AUTO_PAUSED, walk.state());

    // Walking on at 2.5 km/h resumes (a bike would need 5).
    for (int k = 0; k < 4; k++, t++)
    {
      north += 2.5 / 3.6;
      walk.onFix(at(t, north, 400, 2.5));
    }
    assertEquals(RideSession.State.RECORDING, walk.state());
  }

  @Test
  public void journalFromBeforeProfilesIsRoad()
  {
    final RideJournal.Content c = RideJournal.parse("H,1,1780000000000,-,Free ride\nP,1780000000000,45.0,3.0,400.0\n");
    assertNotNull(c);
    assertEquals(Profile.ROAD, c.profile);
    assertEquals(1, c.track.pointCount());
  }

  @Test
  public void rideToAPlaceKeepsItsProfileAndPlace()
  {
    final RideSession s = new RideSession(true);
    s.startTo(1_780_000_000_000L, 0, "Café", 45.77, 3.08, Profile.GRAVEL);
    final RideJournal.Content c = RideJournal.parse(s.drainJournal(0));
    assertNotNull(c);
    assertEquals(Profile.GRAVEL, c.profile);
    assertEquals(45.77, c.placeLat, 1e-9);
    assertEquals(3.08, c.placeLon, 1e-9);
  }

  @Test
  public void journalWithoutHeaderRestoresNothing()
  {
    assertNull(RideJournal.parse(""));
    assertNull(RideJournal.parse("P,1,45,3,400\n"));
  }
}
