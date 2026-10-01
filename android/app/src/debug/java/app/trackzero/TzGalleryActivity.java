package app.trackzero;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
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
import app.trackzero.follow.Maneuver;
import app.trackzero.follow.TurnState;
import app.trackzero.format.TzFormat;
import app.trackzero.format.TzFormat.Units;
import app.trackzero.ui.TzBottomIsland;
import app.trackzero.ui.TzCategoryChip;
import app.trackzero.ui.TzChip;
import app.trackzero.ui.TzElevationProfileView;
import app.trackzero.ui.TzHoldButton;
import app.trackzero.ui.TzListRow;
import app.trackzero.ui.TzMapControls;
import app.trackzero.ui.TzPageHandle;
import app.trackzero.ui.TzRouteCard;
import app.trackzero.ui.TzSegmented;
import app.trackzero.ui.TzStatsIsland;
import app.trackzero.ui.TzTextSize;
import app.trackzero.ui.TzToggleRow;
import app.trackzero.ui.TzTurnCard;
import java.util.Locale;

/** Debug-only gallery of TrackZero design tokens and components, compared against design.html boards. */
public class TzGalleryActivity extends AppCompatActivity
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

    addComponentSection();
    addTypeSection();
    addIconSection();
  }

  private void addComponentSection()
  {
    final View c = getLayoutInflater().inflate(R.layout.tz_gallery_components, mColumn, false);
    mColumn.addView(c);
    final Locale l = Locale.US;
    final Units u = Units.METRIC;

    ((TzBottomIsland) c.findViewById(R.id.g_island2)).setActive(TzBottomIsland.Tab.ROUTES);

    final TzStatsIsland stats = c.findViewById(R.id.g_stats);
    stats.setCell(0, "Speed", "24.8", "km/h");
    stats.setCell(1, "ETA", "14:52", "");
    stats.setCell(2, "Remaining", "32.3", "km");

    ((TzTurnCard) c.findViewById(R.id.g_turn))
        .bind(new TurnState(Maneuver.TURN_RIGHT, 118, "Rue Montlosier", 0, Maneuver.TURN_LEFT, 350,
                            "Av. Vercingétorix", false, false));
    ((TzTurnCard) c.findViewById(R.id.g_turn_imminent))
        .bind(new TurnState(Maneuver.SLIGHT_LEFT, 25, "", 0, null, 0, "", true, false));
    ((TzTurnCard) c.findViewById(R.id.g_turn_off)).bind(TurnState.offRoute(140));

    final TzMapControls controls = c.findViewById(R.id.g_controls);
    controls.setListener(new TzMapControls.Listener() {
      private boolean mFollowing;

      @Override
      public void onZoomIn() {}

      @Override
      public void onZoomOut() {}

      @Override
      public void onLocate()
      {
        mFollowing = !mFollowing;
        controls.setFollowing(mFollowing);
      }
    });
    ((TzChip) c.findViewById(R.id.g_gps)).setState("GPS ±4 M", R.color.tz_mint, R.color.tz_text1);
    ((TzChip) c.findViewById(R.id.g_gps_bad)).setState("GPS ±35 M", R.color.tz_amber, R.color.tz_text1);
    final TzChip rec = c.findViewById(R.id.g_rec);
    rec.setState("REC", R.color.tz_mint, R.color.tz_text1);
    rec.setPulsing(true);
    ((TzChip) c.findViewById(R.id.g_paused)).setState("AUTO-PAUSED", R.color.tz_amber, R.color.tz_amber);

    final double[][] loop = sampleLoop();
    final TzRouteCard r1 = c.findViewById(R.id.g_route1);
    r1.bind("Clermont-Ferrand Loop", TzFormat.distance(70_500, u, l), TzFormat.elevation(1491, u, l), "Loop", true,
            loop[0], loop[1]);
    final TzRouteCard r2 = c.findViewById(R.id.g_route2);
    r2.bind("Puy de Dôme Ascent", TzFormat.distance(9_600, u, l), null, "Out & back", false,
            new double[] {45.770, 45.771, 45.773, 45.772, 45.775, 45.777},
            new double[] {2.990, 2.985, 2.981, 2.976, 2.972, 2.965});
    r1.setListener(toggling(r1, true));
    r2.setListener(toggling(r2, false));

    ((TzHoldButton) c.findViewById(R.id.g_hold))
        .setListener(() -> Toast.makeText(this, "Confirmed", Toast.LENGTH_SHORT).show());
    ((TzToggleRow) c.findViewById(R.id.g_toggle1)).setChecked(true, false);
    ((TzSegmented) c.findViewById(R.id.g_segmented)).setOptions("All", "Favorites", "Recent");
    final TzCategoryChip chip1 = c.findViewById(R.id.g_chip1);
    chip1.setIcon(R.drawable.ic_tz_water);
    chip1.setChecked(true);
    chip1.setOnClickListener(v -> chip1.setChecked(!chip1.isChecked()));
    final TzCategoryChip chip2 = c.findViewById(R.id.g_chip2);
    chip2.setIcon(R.drawable.ic_tz_cup);
    chip2.setOnClickListener(v -> chip2.setChecked(!chip2.isChecked()));
    ((TzListRow) c.findViewById(R.id.g_row1)).setDetail("Auvergne · 412 MB");
    ((TzListRow) c.findViewById(R.id.g_row2)).setDetail("12 rides");

    final TzPageHandle handle = c.findViewById(R.id.g_handle);
    handle.setOnClickListener(v -> handle.setPage((mPage = (mPage + 1) % TzPageHandle.PAGES), true));

    final float[] dist = new float[60];
    final float[] alt = new float[60];
    for (int i = 0; i < dist.length; i++)
    {
      dist[i] = i * 160f;
      alt[i] = 800f + 665f * (float) Math.pow(i / 59.0, 1.3) + 12f * (float) Math.sin(i / 3.0);
    }
    final TzElevationProfileView profile = c.findViewById(R.id.g_profile);
    profile.setProfile(dist, alt);
    profile.setRiderDistance(6000);
    profile.setSummit(dist[59], alt[59], "1,465 M");
    profile.setAxisLabels("0 KM", "9.4 KM");
    profile.setOnClickListener(v -> {
      mZoomed = !mZoomed;
      profile.setRange(mZoomed ? 4000 : 0, dist[59], true);
    });
    ((TzElevationProfileView) c.findViewById(R.id.g_profile_compact)).setProfile(dist, alt);
  }

  private int mPage;
  private boolean mZoomed;

  @NonNull
  private static TzRouteCard.Listener toggling(@NonNull TzRouteCard card, boolean initial)
  {
    return new TzRouteCard.Listener() {
      private boolean mFavorite = initial;

      @Override
      public void onOpen() {}

      @Override
      public void onToggleFavorite()
      {
        mFavorite = !mFavorite;
        card.setFavorite(mFavorite);
      }

      @Override
      public void onLongPress() {}
    };
  }

  /** A wobbly loop around Clermont-Ferrand. */
  @NonNull
  private static double[][] sampleLoop()
  {
    final int n = 120;
    final double[] lat = new double[n];
    final double[] lon = new double[n];
    for (int i = 0; i < n; i++)
    {
      final double a = 2 * Math.PI * i / (n - 1);
      final double r = 0.1 * (1 + 0.15 * Math.sin(3 * a));
      lat[i] = 45.78 + r * Math.sin(a);
      lon[i] = 3.08 + 1.3 * r * Math.cos(a);
    }
    return new double[][] {lat, lon};
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
