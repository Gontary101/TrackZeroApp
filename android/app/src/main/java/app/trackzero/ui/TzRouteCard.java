package app.trackzero.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import app.organicmaps.R;
import app.trackzero.format.TzFormat;

/** Library row (B3): silhouette, name, distance + gain, kind, favourite star. */
public class TzRouteCard extends LinearLayout
{
  public interface Listener
  {
    void onOpen();

    void onToggleFavorite();

    void onLongPress();
  }

  @NonNull
  private final TzSilhouetteView mSilhouette;
  @NonNull
  private final TextView mName;
  @NonNull
  private final TzMetricView mDistance;
  @NonNull
  private final TzMetricView mGain;
  @NonNull
  private final TextView mKind;
  @NonNull
  private final ImageButton mStar;
  @Nullable
  private Listener mListener;

  public TzRouteCard(@NonNull Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
    setOrientation(HORIZONTAL);
    setGravity(Gravity.CENTER_VERTICAL);
    setBackgroundResource(R.drawable.tz_bg_surface1_r24);
    setElevation(getResources().getDimension(R.dimen.tz_elev1));
    final int pad = getResources().getDimensionPixelSize(R.dimen.tz_space_12);
    setPaddingRelative(pad, pad, getResources().getDimensionPixelSize(R.dimen.tz_space_4), pad);
    LayoutInflater.from(context).inflate(R.layout.tz_c_route_card, this, true);
    mSilhouette = findViewById(R.id.tz_route_silhouette);
    mName = findViewById(R.id.tz_route_name);
    mDistance = findViewById(R.id.tz_route_distance);
    mGain = findViewById(R.id.tz_route_gain);
    mKind = findViewById(R.id.tz_route_kind);
    mStar = findViewById(R.id.tz_route_star);

    final LinearLayout body = findViewById(R.id.tz_route_body);
    body.setOnClickListener(v -> {
      if (mListener != null)
        mListener.onOpen();
    });
    body.setOnLongClickListener(v -> {
      if (mListener != null)
        mListener.onLongPress();
      return mListener != null;
    });
    mStar.setOnClickListener(v -> {
      if (mListener != null)
        mListener.onToggleFavorite();
    });
  }

  public void setListener(@Nullable Listener listener)
  {
    mListener = listener;
  }

  /** {@code gain} null when the track has no elevation (shows —). */
  public void bind(@NonNull CharSequence name, @NonNull TzFormat.Value distance, @Nullable TzFormat.Value gain,
                   @NonNull CharSequence kind, boolean favorite, @Nullable double[] lat, @Nullable double[] lon)
  {
    mName.setText(name);
    mDistance.setValue(distance);
    if (gain != null)
      mGain.setValue(gain);
    else
      mGain.setValue(TzFormat.UNKNOWN, "");
    mKind.setText(kind);
    mSilhouette.setPoints(lat, lon);
    setFavorite(favorite);
  }

  /** A proposed loop (L2, Phase 29): a chevron instead of the star; the card opens it. */
  public void showChevron()
  {
    mStar.setImageResource(R.drawable.ic_tz_chev);
    ImageViewCompat.setImageTintList(mStar, ContextCompat.getColorStateList(getContext(), R.color.tz_text2));
    mStar.setClickable(false);
    mStar.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  }

  /** Rides have no favorites: their cards hide the star. */
  public void setStarVisible(boolean visible)
  {
    mStar.setVisibility(visible ? VISIBLE : GONE);
  }

  public void setFavorite(boolean favorite)
  {
    mStar.setImageResource(favorite ? R.drawable.ic_tz_star_filled : R.drawable.ic_tz_star);
    ImageViewCompat.setImageTintList(
        mStar, ContextCompat.getColorStateList(getContext(), favorite ? R.color.tz_mint : R.color.tz_text2));
    mStar.setContentDescription(
        getResources().getString(favorite ? R.string.tz_cd_remove_favorite : R.string.tz_cd_add_favorite));
  }
}
