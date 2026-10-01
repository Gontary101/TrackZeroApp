package app.trackzero.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import app.organicmaps.R;

/** Top-right map buttons: zoom group and locate. The locate icon is mint while the map follows the rider. */
public class TzMapControls extends LinearLayout
{
  public interface Listener
  {
    void onZoomIn();

    void onZoomOut();

    void onLocate();
  }

  @NonNull
  private final ImageButton mLocate;
  @Nullable
  private Listener mListener;

  public TzMapControls(@NonNull Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
    setOrientation(VERTICAL);
    setClipChildren(false);
    setClipToPadding(false);
    LayoutInflater.from(context).inflate(R.layout.tz_c_map_controls, this, true);
    mLocate = findViewById(R.id.tz_locate);
    findViewById(R.id.tz_zoom_in).setOnClickListener(v -> {
      if (mListener != null)
        mListener.onZoomIn();
    });
    findViewById(R.id.tz_zoom_out).setOnClickListener(v -> {
      if (mListener != null)
        mListener.onZoomOut();
    });
    mLocate.setOnClickListener(v -> {
      if (mListener != null)
        mListener.onLocate();
    });
  }

  public void setListener(@Nullable Listener listener)
  {
    mListener = listener;
  }

  public void setFollowing(boolean following)
  {
    ImageViewCompat.setImageTintList(
        mLocate, ContextCompat.getColorStateList(getContext(), following ? R.color.tz_mint : R.color.tz_text1));
  }
}
