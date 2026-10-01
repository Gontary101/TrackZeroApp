package app.trackzero;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;
import app.organicmaps.R;
import app.trackzero.ui.TzTextSize;

/** Debug-only gallery of TrackZero design tokens and components, compared against design.html boards. */
public class TzGalleryActivity extends Activity
{
  private static final String[] ICONS = {
      "map", "routes", "search", "more", "play", "plus", "minus", "locate", "turn_right", "turn_left", "straight",
      "fork_right", "roundabout", "reroute", "back", "mountain", "gain", "dist", "chev", "chev_up", "star",
      "star_filled", "voice", "sun", "pause", "stop", "import", "export", "water", "wrench", "cup", "pin",
      "download", "history", "ruler", "info", "gps", "close", "flag"};

  private LinearLayout mColumn;

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    final ScrollView scroll = new ScrollView(this);
    mColumn = new LinearLayout(this);
    mColumn.setOrientation(LinearLayout.VERTICAL);
    final int pad = getResources().getDimensionPixelSize(R.dimen.tz_gutter);
    mColumn.setPadding(pad, pad * 3, pad, pad * 3);
    scroll.addView(mColumn);
    setContentView(scroll);

    addTypeSection();
    addIconSection();
  }

  private void addTypeSection()
  {
    header("TYPE");
    number(R.style.TzText_Hero, "32.4", 208);
    number(R.style.TzText_Turn, "120 m", 64);
    number(R.style.TzText_MetricXL, "8.4", 56);
    number(R.style.TzText_MetricL, "38.2", 50);
    number(R.style.TzText_MetricM, "14:52", 32);
    number(R.style.TzText_MetricS, "70.5 km", 22);
    text(R.style.TzText_DotXL, "ROUTES");
    text(R.style.TzText_DotL, "SPEED");
    text(R.style.TzText_DotM, "PUY DE DÔME");
    text(R.style.TzText_TitleL, "Clermont-Ferrand Loop");
    text(R.style.TzText_TitleM, "Turn right");
    text(R.style.TzText_Body, "Import a GPX file from your phone.");
    text(R.style.TzText_Caption, "4 routes · newest first");
    text(R.style.TzText_Label, "Remaining");
    text(R.style.TzText_IslandLabel, "Routes");
    text(R.style.TzText_Button, "Start ride");
  }

  private void addIconSection()
  {
    header("ICONS");
    final GridLayout grid = new GridLayout(this);
    grid.setColumnCount(6);
    final int cell = getResources().getDimensionPixelSize(R.dimen.tz_touch_min_ride);
    for (String name : ICONS)
    {
      final ImageView icon = new ImageView(this);
      icon.setImageResource(getResources().getIdentifier("ic_tz_" + name, "drawable", getPackageName()));
      icon.setScaleType(ImageView.ScaleType.CENTER);
      icon.setContentDescription(name);
      grid.addView(icon, new ViewGroup.LayoutParams(cell, cell));
    }
    mColumn.addView(grid);
  }

  private void header(@NonNull String title)
  {
    final TextView tv = text(R.style.TzText_Label, title);
    tv.setPadding(0, getResources().getDimensionPixelSize(R.dimen.tz_space_32), 0,
                  getResources().getDimensionPixelSize(R.dimen.tz_space_8));
  }

  private void number(@StyleRes int style, @NonNull String value, float sp)
  {
    TzTextSize.applyCapped(text(style, value), sp);
  }

  @NonNull
  private TextView text(@StyleRes int style, @NonNull String value)
  {
    final TextView tv = new TextView(this, null, 0, style);
    tv.setText(value);
    tv.setGravity(Gravity.START);
    tv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                                                     ViewGroup.LayoutParams.WRAP_CONTENT));
    mColumn.addView(tv);
    return tv;
  }
}
