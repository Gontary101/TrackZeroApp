package app.trackzero.ride;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import app.trackzero.follow.Maneuver;
import app.trackzero.follow.RouteManeuver;
import java.util.List;
import org.junit.Test;

public class ToneRulesTest
{
  private static final class Prefs implements ToneRules.Settings
  {
    boolean tones = true;
    boolean warnOffRoute = true;
    boolean warnGps = true;
    boolean paused;

    @Override
    public boolean tones()
    {
      return tones;
    }

    @Override
    public boolean warnOffRoute()
    {
      return warnOffRoute;
    }

    @Override
    public boolean warnGps()
    {
      return warnGps;
    }

    @Override
    public boolean paused()
    {
      return paused;
    }
  }

  private static final RouteManeuver RIGHT = new RouteManeuver(Maneuver.TURN_RIGHT, 100, 90);

  @Test
  public void aTurnSoonOnceAndNowTwice()
  {
    final ToneRules r = new ToneRules(new Prefs());
    final List<ToneRules.Sound> soon = r.turnSoon(RIGHT);
    assertEquals(1, soon.size());
    assertEquals(TonePattern.RIGHT, soon.get(0).pattern);
    final List<ToneRules.Sound> now = r.turnNow(RIGHT);
    assertEquals(2, now.size());
    assertEquals(TonePattern.RIGHT.durationMs() + 250, now.get(1).atMs);
    assertEquals(TonePattern.LEFT, r.turnSoon(new RouteManeuver(Maneuver.SLIGHT_LEFT, 100, -30)).get(0).pattern);
  }

  @Test
  public void aRoundaboutThenItsSideAndLeavingTheBike()
  {
    final ToneRules r = new ToneRules(new Prefs());
    final List<ToneRules.Sound> s = r.turnSoon(new RouteManeuver(Maneuver.ROUNDABOUT, 100, -70));
    assertEquals(TonePattern.ROUNDABOUT, s.get(0).pattern);
    assertEquals(TonePattern.LEFT, s.get(1).pattern);
    assertEquals(TonePattern.ROUNDABOUT.durationMs() + 40, s.get(1).atMs);
    final List<ToneRules.Sound> walk = r.turnSoon(new RouteManeuver(Maneuver.LEAVE_BIKE, 100, 0, ""));
    assertEquals(TonePattern.STRAIGHT, walk.get(0).pattern);
    assertEquals(TonePattern.ARRIVE, walk.get(1).pattern);
  }

  @Test
  public void offRouteOncePerEpisode()
  {
    final ToneRules r = new ToneRules(new Prefs());
    assertEquals(1, r.offRoute().size());
    assertTrue(r.offRoute().isEmpty());
    r.backOnRoute();
    assertEquals(TonePattern.OFF_ROUTE, r.offRoute().get(0).pattern);
  }

  @Test
  public void silentWhenPausedAndSettingsRespected()
  {
    final Prefs p = new Prefs();
    final ToneRules r = new ToneRules(p);
    p.paused = true;
    assertTrue(r.turnSoon(RIGHT).isEmpty());
    assertTrue(r.offRoute().isEmpty());
    p.paused = false;
    r.backOnRoute();
    p.warnOffRoute = false;
    assertTrue(r.offRoute().isEmpty());
    assertTrue(r.wrongWay().isEmpty());
    assertEquals(1, r.turnSoon(RIGHT).size());
    p.tones = false;
    assertTrue(r.turnSoon(RIGHT).isEmpty());
    assertTrue(r.arrived().isEmpty());
  }

  @Test
  public void gpsLostAfterTwentySecondsOnceAndBackAfterFive()
  {
    final ToneRules r = new ToneRules(new Prefs());
    r.fix(1_000);
    assertTrue(r.check(20_000).isEmpty());
    assertEquals(TonePattern.GPS_LOST, r.check(21_000).get(0).pattern);
    assertTrue(r.gpsLost());
    assertTrue(r.check(40_000).isEmpty());
    assertTrue(r.fix(41_000).isEmpty());
    assertTrue(r.fix(44_000).isEmpty());
    assertEquals(TonePattern.GPS_BACK, r.fix(46_000).get(0).pattern);
    assertFalse(r.gpsLost());
    // Re-armed after the recovery.
    assertEquals(1, r.check(66_000).size());
  }

  @Test
  public void aGapDuringRecoveryStartsItAgain()
  {
    final ToneRules r = new ToneRules(new Prefs());
    r.fix(0);
    r.check(20_000);
    r.fix(30_000);
    r.check(36_000);
    assertTrue(r.fix(37_000).isEmpty());
    assertTrue(r.fix(41_000).isEmpty());
    assertEquals(1, r.fix(42_000).size());
  }

  @Test
  public void gpsWarningOffIsSilentButStillKnown()
  {
    final Prefs p = new Prefs();
    p.warnGps = false;
    final ToneRules r = new ToneRules(p);
    r.fix(0);
    assertTrue(r.check(25_000).isEmpty());
    assertTrue(r.gpsLost());
  }
}
