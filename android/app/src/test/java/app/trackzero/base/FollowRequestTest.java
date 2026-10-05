package app.trackzero.base;

import static org.junit.Assert.assertEquals;

import app.organicmaps.sdk.location.LocationState;
import org.junit.Test;

public class FollowRequestTest
{
  private static final int NOT_FOLLOW = LocationState.NOT_FOLLOW;
  private static final int FOLLOW = LocationState.FOLLOW;
  private static final int ROTATE = LocationState.FOLLOW_AND_ROTATE;

  @Test
  public void aStaleNotFollowWithoutTouchKeepsTheRequest()
  {
    // The preview stopped following, then the ride asked to follow; the preview's answer comes late.
    final FollowRequest r = new FollowRequest();
    r.request(true);
    assertEquals(FollowRequest.Action.NOTHING, r.onModeChanged(NOT_FOLLOW, false, ROTATE));
    assertEquals(ROTATE, r.mode());
  }

  @Test
  public void aPanCancelsTheRequest()
  {
    final FollowRequest r = new FollowRequest();
    r.request(true);
    assertEquals(FollowRequest.Action.CANCELLED, r.onModeChanged(NOT_FOLLOW, true, NOT_FOLLOW));
    assertEquals(FollowRequest.NONE, r.mode());
    // Nothing more after that.
    assertEquals(FollowRequest.Action.NOTHING, r.onModeChanged(FOLLOW, false, FOLLOW));
  }

  @Test
  public void aFallBackToNorthUpAsksForHeadingUpAgain()
  {
    final FollowRequest r = new FollowRequest();
    r.request(true);
    assertEquals(FollowRequest.Action.ASK_AGAIN, r.onModeChanged(FOLLOW, false, FOLLOW));
  }

  @Test
  public void anOlderStopAppliedAfterTheRequestIsAnsweredWithTheRequestAgain()
  {
    final FollowRequest r = new FollowRequest();
    r.request(true);
    assertEquals(FollowRequest.Action.ASK_AGAIN, r.onModeChanged(NOT_FOLLOW, false, NOT_FOLLOW));
    assertEquals(ROTATE, r.mode());
  }

  @Test
  public void aStaleFollowReportIsIgnoredWhenTheEngineIsHeadingUp()
  {
    final FollowRequest r = new FollowRequest();
    r.request(true);
    assertEquals(FollowRequest.Action.NOTHING, r.onModeChanged(FOLLOW, false, ROTATE));
  }

  @Test
  public void northUpRequestsAndNoRequestsDoNothing()
  {
    final FollowRequest r = new FollowRequest();
    assertEquals(FollowRequest.Action.NOTHING, r.onModeChanged(NOT_FOLLOW, true, NOT_FOLLOW));
    r.request(false);
    assertEquals(FollowRequest.Action.NOTHING, r.onModeChanged(FOLLOW, false, FOLLOW));
    r.forget();
    assertEquals(FollowRequest.NONE, r.mode());
  }
}
