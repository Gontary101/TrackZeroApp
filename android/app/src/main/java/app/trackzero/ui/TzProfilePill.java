package app.trackzero.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import app.organicmaps.R;
import app.trackzero.routes.Profile;

/**
 * The one profile control (Components v2): icon, label, chevron in a 44dp pill on the map. A tap opens the
 * {@code TzProfileSheet}; not shown during a ride (the profile is fixed at START).
 */
public class TzProfilePill extends LinearLayout
{
  @NonNull
  private final ImageView mIcon;
  @NonNull
  private final TextView mLabel;

  public TzProfilePill(@NonNull Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
    setOrientation(HORIZONTAL);
    setGravity(Gravity.CENTER_VERTICAL);
    setBackgroundResource(R.drawable.tz_bg_surface2_r22);
    setElevation(getResources().getDimension(R.dimen.tz_elev2));
    setPaddingRelative(getResources().getDimensionPixelSize(R.dimen.tz_profile_pill_pad_start), 0,
                       getResources().getDimensionPixelSize(R.dimen.tz_profile_pill_pad_end), 0);
    setMinimumHeight(getResources().getDimensionPixelSize(R.dimen.tz_profile_pill_h));
    setClickable(true);
    setFocusable(true);
    final int gap = getResources().getDimensionPixelSize(R.dimen.tz_profile_pill_gap);

    mIcon = new ImageView(context);
    ImageViewCompat.setImageTintList(mIcon, ContextCompat.getColorStateList(context, R.color.tz_text1));
    mIcon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    final int iconSize = getResources().getDimensionPixelSize(R.dimen.tz_profile_pill_icon);
    addView(mIcon, new LayoutParams(iconSize, iconSize));

    mLabel = new TextView(context, null, 0, R.style.TzText_ProfilePill);
    final LayoutParams llp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    llp.setMarginStart(gap);
    llp.setMarginEnd(gap);
    addView(mLabel, llp);

    final ImageView chevron = new ImageView(context);
    chevron.setImageResource(R.drawable.ic_tz_chev_down);
    ImageViewCompat.setImageTintList(chevron, ContextCompat.getColorStateList(context, R.color.tz_text2));
    chevron.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    final int chevronSize = getResources().getDimensionPixelSize(R.dimen.tz_profile_pill_chevron);
    addView(chevron, new LayoutParams(chevronSize, chevronSize));

    setProfile(Profile.ROAD);
  }

  public void setProfile(@NonNull Profile p)
  {
    mIcon.setImageResource(TzProfiles.icon(p));
    final String label = getResources().getString(TzProfiles.label(p));
    mLabel.setText(label);
    setContentDescription(getResources().getString(R.string.tz_cd_profile, label));
  }

  public void setListener(@Nullable OnClickListener listener)
  {
    setOnClickListener(listener);
  }
}
