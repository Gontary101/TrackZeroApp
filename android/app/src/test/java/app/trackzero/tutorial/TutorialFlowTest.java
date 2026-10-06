package app.trackzero.tutorial;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class TutorialFlowTest
{
  /** Settings as a map, like the app's: the tutorial must only ever write its own key. */
  private static final class Prefs implements TutorialFlow.Store
  {
    final Map<String, Object> values = new HashMap<>();

    @Override
    public boolean tutorialDone()
    {
      return Boolean.TRUE.equals(values.get("tutorialDone"));
    }

    @Override
    public void setTutorialDone()
    {
      values.put("tutorialDone", true);
    }
  }

  @Test
  public void shownOnceThenTheMap()
  {
    final Prefs p = new Prefs();
    assertEquals(TutorialFlow.Place.TUTORIAL, TutorialFlow.start(false, p));
    assertEquals(TutorialFlow.Place.MAP, TutorialFlow.finish(false, p));
    assertEquals(TutorialFlow.Place.MAP, TutorialFlow.start(false, p));
    assertEquals(TutorialFlow.Place.MAP, TutorialFlow.start(false, p));
  }

  @Test
  public void skipMarksDoneOnEveryStep()
  {
    for (int step = 0; step < TutorialFlow.STEPS; step++)
    {
      final Prefs p = new Prefs();
      assertEquals(TutorialFlow.Place.TUTORIAL, TutorialFlow.start(false, p));
      // SKIP, START and Back are the same call whatever the step.
      TutorialFlow.finish(false, p);
      assertTrue(p.tutorialDone());
    }
  }

  @Test
  public void aRunningRideComesFirst()
  {
    final Prefs p = new Prefs();
    assertEquals(TutorialFlow.Place.RIDE, TutorialFlow.start(true, p));
    assertFalse(p.tutorialDone());
    assertEquals(TutorialFlow.Place.TUTORIAL, TutorialFlow.start(false, p));
  }

  @Test
  public void replayReturnsToMoreAndLeavesOtherSettings()
  {
    final Prefs p = new Prefs();
    p.values.put("units", "imperial");
    p.values.put("voice", false);
    p.values.put("appearance", "light");
    TutorialFlow.finish(false, p);
    final Map<String, Object> before = new HashMap<>(p.values);
    assertEquals(TutorialFlow.Place.MORE, TutorialFlow.finish(true, p));
    assertEquals(before, p.values);
    assertEquals(TutorialFlow.Place.MAP, TutorialFlow.start(false, p));
  }

  @Test
  public void lastStepSaysStart()
  {
    assertFalse(TutorialFlow.isLast(0));
    assertFalse(TutorialFlow.isLast(2));
    assertTrue(TutorialFlow.isLast(3));
  }
}
