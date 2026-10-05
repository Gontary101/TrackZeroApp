package app.trackzero.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.core.widget.TextViewCompat;
import app.organicmaps.R;
import app.trackzero.follow.Maneuver;
import app.trackzero.follow.TurnState;
import app.trackzero.format.TzFormat;
import java.util.Locale;

/** Ride page 0 turn instruction (R2), imminent state (≤ 30 m) and the warnings: off route (R3), wrong way (R3W). */
public class TzTurnCard extends LinearLayout
{
  private static final float DISTANCE_SP = 64f;
  private static final float DISTANCE_UNIT_SP = 28f;
  private static final float OFF_ROUTE_SP = 44f;
  private static final float OFF_ROUTE_UNIT_SP = 22f;

  @NonNull
  private final View mWell;
  @NonNull
  private final ImageView mGlyph;
  @NonNull
  private final TextView mExit;
  @NonNull
  private final TextView mOffTitle;
  @NonNull
  private final TzMetricView mDistance;
  @NonNull
  private final TextView mInstruction;
  @NonNull
  private final TextView mStreet;
  @NonNull
  private final View mDivider;
  @NonNull
  private final View mThenRow;
  @NonNull
  private final ImageView mThenGlyph;
  @NonNull
  private final TextView mThenText;

  /** What the card shows: a turn, or one of the warnings, each in the same amber layout. */
  private enum Mode
  {
    TURN,
    OFF_ROUTE,
    WRONG_WAY
  }

  private TzFormat.Units mUnits = TzFormat.Units.METRIC;
  @Nullable
  private Mode mMode;
  private boolean mRecalculating;
  private int mShownExit = -1;

  public TzTurnCard(@NonNull Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
    setOrientation(VERTICAL);
    final int pad = getResources().getDimensionPixelSize(R.dimen.tz_space_16);
    setPadding(pad, pad, pad, pad);
    setElevation(getResources().getDimension(R.dimen.tz_elev2));
    LayoutInflater.from(context).inflate(R.layout.tz_c_turn_card, this, true);
    mWell = findViewById(R.id.tz_turn_well);
    mGlyph = findViewById(R.id.tz_turn_glyph);
    mExit = findViewById(R.id.tz_turn_exit);
    mOffTitle = findViewById(R.id.tz_turn_off_title);
    mDistance = findViewById(R.id.tz_turn_distance);
    mInstruction = findViewById(R.id.tz_turn_instruction);
    mStreet = findViewById(R.id.tz_turn_street);
    mDivider = findViewById(R.id.tz_turn_divider);
    mThenRow = findViewById(R.id.tz_turn_then);
    mThenGlyph = findViewById(R.id.tz_turn_then_glyph);
    mThenText = findViewById(R.id.tz_turn_then_text);
    mDistance.setUnitGap(getResources().getDimensionPixelSize(R.dimen.tz_unit_gap_turn));
    setMode(Mode.TURN);
  }

  public void setUnits(@NonNull TzFormat.Units units)
  {
    mUnits = units;
  }

  public void bind(@NonNull TurnState s)
  {
    final Locale locale = Locale.getDefault();
    setMode(s.wrongWay ? Mode.WRONG_WAY : s.offRoute ? Mode.OFF_ROUTE : Mode.TURN);
    if (s.wrongWay)
      return;
    final TzFormat.Value distance = TzFormat.distance(s.distanceM, mUnits, locale);
    if (s.offRoute)
    {
      mDistance.setValue(distance.number, distance.unit + " " + getResources().getString(R.string.tz_off_route_away));
      if (s.recalculating != mRecalculating)
      {
        mRecalculating = s.recalculating;
        mInstruction.setText(s.recalculating ? R.string.tz_recalculating : R.string.tz_head_back);
      }
      return;
    }

    mDistance.setValue(distance);
    TzManeuvers.applyGlyph(mGlyph, s.maneuver, s.glyphRotationDeg, true);
    showExit(s.maneuver == Maneuver.ROUNDABOUT ? s.exit : 0);
    mWell.setBackgroundResource(s.imminent ? R.drawable.tz_bg_mint_r22 : R.drawable.tz_bg_surface3_r22);
    final int ink = s.imminent ? R.color.tz_on_mint : R.color.tz_text1;
    tintGlyph(ink);
    mExit.setTextColor(ContextCompat.getColor(getContext(), ink));
    TzViews.setText(mInstruction, TzManeuvers.instruction(getContext(), s.maneuver, s.exit));
    TzViews.setText(mStreet, s.street);
    mStreet.setVisibility(s.street.isEmpty() ? GONE : VISIBLE);

    final boolean then = s.thenManeuver != null;
    mDivider.setVisibility(then ? VISIBLE : GONE);
    mThenRow.setVisibility(then ? VISIBLE : GONE);
    if (then)
    {
      TzManeuvers.applyGlyph(mThenGlyph, s.thenManeuver, 0f, false);
      final String d = TzFormat.distance(s.thenDistanceM, mUnits, locale).toString();
      TzViews.setText(mThenText, s.thenStreet.isEmpty() ? d : d + " · " + s.thenStreet);
    }
  }

  /** A roundabout's exit under its glyph, the glyph moved up to make room; 0 hides it. */
  private void showExit(int exit)
  {
    if (exit == mShownExit)
      return;
    mShownExit = exit;
    final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mGlyph.getLayoutParams();
    lp.gravity = exit > 0 ? Gravity.TOP | Gravity.CENTER_HORIZONTAL : Gravity.CENTER;
    lp.topMargin = exit > 0 ? getResources().getDimensionPixelSize(R.dimen.tz_space_8) : 0;
    mGlyph.setLayoutParams(lp);
    mExit.setVisibility(exit > 0 ? VISIBLE : GONE);
    mExit.setText(exit > 0 ? String.valueOf(exit) : "");
  }

  /** Restyles only on a state change; bind() runs on every fix. */
  private void setMode(@NonNull Mode mode)
  {
    if (mode == mMode)
      return;
    mMode = mode;
    final boolean off = mode != Mode.TURN;
    setBackgroundResource(off ? R.drawable.tz_bg_amber_bg_r28 : R.drawable.tz_bg_surface2_r28);
    mOffTitle.setVisibility(off ? VISIBLE : GONE);
    mDistance.setNumberSize(off ? OFF_ROUTE_SP : DISTANCE_SP);
    mDistance.setUnitSize(off ? OFF_ROUTE_UNIT_SP : DISTANCE_UNIT_SP);
    mDistance.setUnitColor(ContextCompat.getColor(getContext(), off ? R.color.tz_amber_unit : R.color.tz_text2));
    TextViewCompat.setTextAppearance(mInstruction, off ? R.style.TzText_OffRouteLine : R.style.TzText_TurnInstruction);
    if (!off)
      return;
    mWell.setBackgroundResource(R.drawable.tz_bg_amber_well_r22);
    final boolean wrongWay = mode == Mode.WRONG_WAY;
    // A warning: glyph, title, big text and its unit, a line.
    mOffTitle.setText(wrongWay ? R.string.tz_wrong_way : R.string.tz_off_route);
    TzManeuvers.applyGlyph(mGlyph, Maneuver.REROUTE, 0f, true); // also undoes a turn glyph's mirror and rotation
    if (wrongWay)
    {
      mGlyph.setImageResource(R.drawable.ic_tz_uturn_bold);
      mDistance.setValue(getResources().getString(R.string.tz_wrong_way_turn),
                         getResources().getString(R.string.tz_wrong_way_around));
    }
    showExit(0);
    tintGlyph(R.color.tz_amber);
    mRecalculating = false;
    mInstruction.setText(wrongWay ? R.string.tz_wrong_way_line : R.string.tz_head_back);
    mStreet.setVisibility(GONE);
    mDivider.setVisibility(GONE);
    mThenRow.setVisibility(GONE);
  }

  private void tintGlyph(int colorRes)
  {
    ImageViewCompat.setImageTintList(mGlyph, ContextCompat.getColorStateList(getContext(), colorRes));
  }
}
