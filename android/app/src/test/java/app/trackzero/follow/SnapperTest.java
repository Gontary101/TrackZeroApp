package app.trackzero.follow;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SnapperTest
{
  @Test
  public void pointTenMetresEastSnapsAtTwoHundredMetresAlong()
  {
    FollowRoute route = FollowTestRoutes.polyline(new double[] {0, 0}, new double[] {0, 1000});
    double[] point = FollowTestRoutes.point(10, 200);
    Snapper.Result result = new Snapper.Result();
    Snapper.snap(route, point[0], point[1], 0, 0, route.totalM(), false, result);
    assertEquals(200, result.along, 1);
    assertEquals(10, result.distanceM, 0.5);
  }

  @Test
  public void northboundCourseChoosesOutboundLegOfSameRoad()
  {
    FollowRoute route = outAndBack();
    double[] point = FollowTestRoutes.point(0, 300);
    Snapper.Result result = new Snapper.Result();
    Snapper.snap(route, point[0], point[1], 0, 0, route.totalM(), false, result);
    assertEquals(300, result.along, 1);
    assertEquals(0, result.distanceM, 0.01);
  }

  @Test
  public void southboundCourseChoosesReturnLegOfSameRoad()
  {
    FollowRoute route = outAndBack();
    double[] point = FollowTestRoutes.point(0, 300);
    Snapper.Result result = new Snapper.Result();
    Snapper.snap(route, point[0], point[1], 180, 0, route.totalM(), false, result);
    assertEquals(1700, result.along, 1);
    assertEquals(0, result.distanceM, 0.01);
  }

  @Test
  public void returnLegWindowChoosesReturnWithoutCourse()
  {
    FollowRoute route = outAndBack();
    double[] point = FollowTestRoutes.point(0, 300);
    Snapper.Result result = new Snapper.Result();
    Snapper.snap(route, point[0], point[1], Double.NaN,
        route.totalM() / 2, route.totalM(), false, result);
    assertEquals(1700, result.along, 1);
    assertTrue(result.along > route.totalM() / 2);
    assertEquals(0, result.distanceM, 0.01);
  }

  @Test
  public void preferEarliestChoosesStartOfClosedSquareWithoutCourse()
  {
    FollowRoute route = FollowTestRoutes.polyline(new double[] {0, 0}, new double[] {0, 400},
        new double[] {400, 400}, new double[] {400, 0}, new double[] {0, 0});
    double[] point = FollowTestRoutes.point(0, 0);
    Snapper.Result result = new Snapper.Result();
    Snapper.snap(route, point[0], point[1], Double.NaN, 0, route.totalM(), true, result);
    assertEquals(0, result.along, 1);
    assertEquals(0, result.distanceM, 0.01);
  }

  private static FollowRoute outAndBack()
  {
    return FollowTestRoutes.polyline(new double[] {0, 0}, new double[] {0, 1000}, new double[] {0, 0});
  }
}
