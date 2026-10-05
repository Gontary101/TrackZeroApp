package app.trackzero.base;

import static org.junit.Assert.assertEquals;

import app.organicmaps.sdk.routing.BicyclePath;
import org.junit.Test;

public class TzRouterTest
{
  /** CoMaps' RouterResultCode values (routing/routing_callbacks.hpp) and what the screens are told. */
  @Test
  public void reasons()
  {
    assertEquals(TzRouter.Reason.OK, TzRouter.Reason.of(0));
    assertEquals(TzRouter.Reason.CANCELLED, TzRouter.Reason.of(1));
    assertEquals(TzRouter.Reason.ERROR, TzRouter.Reason.of(2)); // NoCurrentPosition
    assertEquals(TzRouter.Reason.ERROR, TzRouter.Reason.of(3)); // InconsistentMWMandRoute
    assertEquals(TzRouter.Reason.NO_MAP, TzRouter.Reason.of(4)); // RouteFileNotExist
    assertEquals(TzRouter.Reason.NO_START, TzRouter.Reason.of(5)); // StartPointNotFound
    assertEquals(TzRouter.Reason.NO_END, TzRouter.Reason.of(6)); // EndPointNotFound
    assertEquals(TzRouter.Reason.ERROR, TzRouter.Reason.of(7)); // PointsInDifferentMWM
    assertEquals(TzRouter.Reason.NO_WAY, TzRouter.Reason.of(8)); // RouteNotFound
    assertEquals(TzRouter.Reason.NO_MAP, TzRouter.Reason.of(9)); // NeedMoreMaps
    assertEquals(TzRouter.Reason.ERROR, TzRouter.Reason.of(10)); // InternalError
    assertEquals(TzRouter.Reason.NO_MAP, TzRouter.Reason.of(11)); // FileTooOld
    assertEquals(TzRouter.Reason.NO_WAY, TzRouter.Reason.of(12)); // IntermediatePointNotFound
    assertEquals(TzRouter.Reason.NO_WAY, TzRouter.Reason.of(15)); // RouteNotFoundRedressRouteError
    assertEquals(TzRouter.Reason.ERROR, TzRouter.Reason.of(16)); // HasWarnings
  }

  @Test
  public void codeConstantsMatchCoMaps()
  {
    assertEquals(4, BicyclePath.NO_MAP);
    assertEquals(5, BicyclePath.START_NOT_FOUND);
    assertEquals(6, BicyclePath.END_NOT_FOUND);
    assertEquals(8, BicyclePath.ROUTE_NOT_FOUND);
    assertEquals(9, BicyclePath.NEED_MORE_MAPS);
    assertEquals(11, BicyclePath.FILE_TOO_OLD);
    assertEquals(12, BicyclePath.INTERMEDIATE_NOT_FOUND);
    assertEquals(15, BicyclePath.ROUTE_NOT_FOUND_REDRESS);
  }
}
