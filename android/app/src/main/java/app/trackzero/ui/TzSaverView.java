package app.trackzero.ui;

import android.content.Context;
import android.os.BatteryManager;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import app.organicmaps.R;
import app.trackzero.follow.Maneuver;
import app.trackzero.follow.TurnState;
import app.trackzero.format.TzFormat;
import java.util.Date;
import java.util.Locale;

/**
 * X2 · Battery saver (Phase 35): on black, dim, the next turn, the distance to it and the distance left, the clock and
 * the battery; SHARE and EXIT SAVER. A free ride shows the distance and time ridden. It is drawn only when what it
 * shows changes (see {@link #key}): the screen does no other work.
 */
public class TzSaverView extends FrameLayout
{
  public interface Listener
  {
    void onShare();

    void onExit();
  }

  /** The distance to the turn is redrawn when it crosses these, metres. */
  private static final double[] BANDS = {50, 200, 500, 1000};

  @NonNull
  private final TextView mClock;
  @NonNull
  private final ImageView mGlyph;
  @NonNull
  private final TzMetricView mDistance;
  @NonNull
  private final TextView mLine;
  @Nullable
  private String mKey;

  public TzSaverView(@NonNull Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
    setBackgroundResource(R.color.tz_saver_bg);
    setClickable(true);
    LayoutInflater.from(context).inflate(R.layout.tz_saver_view, this, true);
    mClock = findViewById(R.id.tz_saver_clock);
    mGlyph = findViewById(R.id.tz_saver_glyph);
    mDistance = findViewById(R.id.tz_saver_distance);
    mLine = findViewById(R.id.tz_saver_line);
    mDistance.setNumberColor(ContextCompat.getColor(context, R.color.tz_saver_text));
    mDistance.setUnitColor(ContextCompat.getColor(context, R.color.tz_saver_dim));
  }

  public void setListener(@NonNull Listener l)
  {
    findViewById(R.id.tz_saver_share).setOnClickListener(v -> l.onShare());
    findViewById(R.id.tz_saver_exit).setOnClickListener(v -> l.onExit());
  }

  /** The top and bottom rows clear the system bars. */
  public void setInsets(int top, int bottom)
  {
    final LayoutParams t = (LayoutParams) findViewById(R.id.tz_saver_top).getLayoutParams();
    t.topMargin = top + getResources().getDimensionPixelSize(R.dimen.tz_space_24);
    final LayoutParams b = (LayoutParams) findViewById(R.id.tz_saver_buttons).getLayoutParams();
    b.bottomMargin = bottom + getResources().getDimensionPixelSize(R.dimen.tz_space_24);
    requestLayout();
  }

  /** Forgets what was drawn: the next bind draws (the view was shown again). */
  public void invalidateContent()
  {
    mKey = null;
  }

  /**
   * A guided ride. Drawn only when the turn, its distance band, a warning or the minute changes.
   *
   * @param turn null until the rider is placed on the route
   */
  public void bindTurn(@Nullable TurnState turn, double remainingM, @NonNull TzFormat.Units units)
  {
    final long minute = System.currentTimeMillis() / 60_000;
    final String key = turn == null ? "none|" + minute
                                    : turn.maneuver + "|" + turn.exit + "|" + band(turn.distanceM) + "|"
                                          + turn.offRoute + "|" + turn.wrongWay + "|" + minute;
    if (key.equals(mKey))
      return;
    mKey = key;
    final Locale locale = Locale.getDefault();
    bindClock();
    if (turn == null)
    {
      mGlyph.setImageResource(R.drawable.ic_tz_straight_bold);
      mDistance.setValue(TzFormat.UNKNOWN, "");
      mLine.setText("");
      return;
    }
    final Maneuver shown = turn.wrongWay ? Maneuver.UTURN : turn.offRoute ? Maneuver.REROUTE : turn.maneuver;
    TzManeuvers.applyGlyph(mGlyph, shown, turn.glyphRotationDeg, true);
    mDistance.setValue(TzFormat.distance(turn.distanceM, units, locale));
    final String what = turn.wrongWay ? getResources().getString(R.string.tz_wrong_way_line)
                        : turn.offRoute ? getResources().getString(R.string.tz_off_route)
                                        : TzManeuvers.instruction(getContext(), turn.maneuver, turn.exit);
    mLine.setText(getResources().getString(R.string.tz_saver_line, what,
                                           TzFormat.distance(remainingM, units, locale).toString()));
  }

  /** A free ride: the distance and the time ridden, once a minute. */
  public void bindFree(double distanceM, long movingS, @NonNull TzFormat.Units units)
  {
    final String key = "free|" + System.currentTimeMillis() / 60_000;
    if (key.equals(mKey))
      return;
    mKey = key;
    bindClock();
    mGlyph.setImageResource(R.drawable.ic_tz_straight_bold);
    mDistance.setValue(TzFormat.distance(distanceM, units, Locale.getDefault()));
    mLine.setText(getResources().getString(R.string.tz_saver_free_line, TzFormat.hoursMinutes(movingS)));
  }

  /** The clock and the battery, read only when the view is redrawn. */
  private void bindClock()
  {
    final String time = DateFormat.getTimeFormat(getContext()).format(new Date());
    final BatteryManager battery = (BatteryManager) getContext().getSystemService(Context.BATTERY_SERVICE);
    final int pct = battery == null ? 0 : battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    mClock.setText(getResources().getString(R.string.tz_saver_clock, time, pct));
  }

  /** Which of 50 m / 200 m / 500 m / 1 km the distance is under. */
  static int band(double m)
  {
    int b = BANDS.length;
    for (int i = BANDS.length - 1; i >= 0; i--)
      if (m <= BANDS[i])
        b = i;
    return b;
  }
}
