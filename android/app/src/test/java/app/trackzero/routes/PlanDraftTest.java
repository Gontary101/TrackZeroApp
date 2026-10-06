package app.trackzero.routes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlanDraftTest
{
  @Test
  public void firstTapIsTheDestinationThenItBecomesAWaypoint()
  {
    final PlanDraft d = new PlanDraft();
    assertTrue(d.isEmpty());
    d.tap(1, 1);
    assertEquals(0, d.waypointCount());
    assertEquals(1, d.destination().lat, 0);
    d.tap(2, 2);
    assertEquals(1, d.waypointCount());
    assertEquals(1, d.points().get(0).lat, 0); // The old destination, now waypoint 1.
    assertEquals(2, d.destination().lat, 0);
  }

  @Test
  public void eightWaypointsAtMost()
  {
    final PlanDraft d = new PlanDraft();
    for (int i = 0; i < 9; i++)
      assertTrue(d.tap(i, i));
    assertEquals(PlanDraft.MAX_WAYPOINTS, d.waypointCount());
    assertFalse("A tenth point would be a ninth waypoint", d.tap(10, 10));
    assertEquals(8, d.destination().lat, 0);
  }

  @Test
  public void removingTheDestinationMakesTheLastWaypointTheDestination()
  {
    final PlanDraft d = new PlanDraft();
    d.tap(1, 1);
    d.tap(2, 2);
    d.tap(3, 3);
    d.remove(2);
    assertEquals(2, d.destination().lat, 0);
    assertEquals(1, d.waypointCount());
    d.remove(0);
    assertEquals(2, d.destination().lat, 0);
    assertEquals(0, d.waypointCount());
  }

  @Test
  public void reorderAndMove()
  {
    final PlanDraft d = new PlanDraft();
    d.tap(1, 1);
    d.tap(2, 2);
    d.tap(3, 3);
    d.reorder(2, 0); // The destination dragged to the top: it is the first waypoint now, 2 the destination.
    assertEquals(3, d.points().get(0).lat, 0);
    assertEquals(2, d.destination().lat, 0);
    d.move(1, 5, 5);
    assertEquals(5, d.points().get(1).lat, 0);
  }

  @Test
  public void undoRestoresWholeStatesTwentyDeep()
  {
    final PlanDraft d = new PlanDraft();
    assertFalse(d.canUndo());
    d.tap(1, 1);
    d.tap(2, 2);
    d.remove(0);
    d.undo();
    assertEquals(2, d.points().size());
    d.undo();
    assertEquals(1, d.points().size());
    d.undo();
    assertTrue(d.isEmpty());
    assertFalse(d.canUndo());
    d.tap(0, 0);
    for (int i = 0; i < 30; i++)
      d.move(0, i, i);
    int undos = 0;
    while (d.canUndo())
    {
      d.undo();
      undos++;
    }
    assertEquals(PlanDraft.UNDO_DEPTH, undos);
  }

  @Test
  public void startFromThePickedPointOrTheRider()
  {
    final PlanDraft d = new PlanDraft();
    assertNull(d.start());
    d.setStart(new PlanDraft.Point(7, 7, null));
    assertEquals(7, d.start().lat, 0);
    d.nameStart(7, 7, "Rue Rameau");
    assertEquals("Rue Rameau", d.start().name);
    d.undo();
    assertNull(d.start());
  }

  @Test
  public void aNameArrivingForAMovedPointIsDropped()
  {
    final PlanDraft d = new PlanDraft();
    d.tap(1, 1);
    d.move(0, 2, 2);
    d.name(0, 1, 1, "Old place");
    assertNull(d.destination().name);
    d.name(0, 2, 2, "Orcines");
    assertEquals("Orcines", d.destination().name);
  }
}
