package app.trackzero.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import app.organicmaps.R;

/**
 * A choice in a sheet (Components v2 "BigRow", the ProfileSheet rows): icon, title, subtitle, and the choice mark: a
 * 28dp mint disc with a check when chosen, a 2dp ring otherwise. The whole row is the target.
 */
public class TzBigRow extends LinearLayout
{
  @NonNull
  private final ImageView mIcon;
  @NonNull
  private final TextView mTitle;
  @NonNull
  private final TextView mSubtitle;
  @NonNull
  private final FrameLayout mMark;
  @NonNull
  private final ImageView mCheck;
  private boolean mChosen;

  public TzBigRow(@NonNull Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
    setOrientation(HORIZONTAL);
    setGravity(Gravity.CENTER_VERTICAL);
    setBackgroundResource(R.drawable.tz_bg_surface3_r20);
    final int padH = getResources().getDimensionPixelSize(R.dimen.tz_space_16);
    final int padV = getResources().getDimensionPixelSize(R.dimen.tz_big_row_pad_v);
    setPadding(padH, padV, padH, padV);
    setMinimumHeight(getResources().getDimensionPixelSize(R.dimen.tz_toggle_row_h));
    setClickable(true);
    setFocusable(true);
    final int gap = getResources().getDimensionPixelSize(R.dimen.tz_row_gap);

    mIcon = new ImageView(context);
    ImageViewCompat.setImageTintList(mIcon, ContextCompat.getColorStateList(context, R.color.tz_text1));
    mIcon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    final int iconSize = getResources().getDimensionPixelSize(R.dimen.tz_icon);
    addView(mIcon, new LayoutParams(iconSize, iconSize));

    final LinearLayout texts = new LinearLayout(context);
    texts.setOrientation(VERTICAL);
    mTitle = new TextView(context, null, 0, R.style.TzText_RowTitle);
    texts.addView(mTitle);
    mSubtitle = new TextView(context, null, 0, R.style.TzText_RowSubtitle);
    final LayoutParams slp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    slp.topMargin = getResources().getDimensionPixelSize(R.dimen.tz_turn_line_gap);
    texts.addView(mSubtitle, slp);
    final LayoutParams tlp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
    tlp.setMarginStart(gap);
    tlp.setMarginEnd(gap);
    addView(texts, tlp);

    final int mark = getResources().getDimensionPixelSize(R.dimen.tz_choice);
    mMark = new FrameLayout(context);
    mCheck = new ImageView(context);
    mCheck.setImageResource(R.drawable.ic_tz_check);
    ImageViewCompat.setImageTintList(mCheck, ContextCompat.getColorStateList(context, R.color.tz_on_mint));
    final int check = getResources().getDimensionPixelSize(R.dimen.tz_choice_check);
    mMark.addView(mCheck, new FrameLayout.LayoutParams(check, check, Gravity.CENTER));
    addView(mMark, new LayoutParams(mark, mark));
    setChosen(false);
  }

  public void bind(@DrawableRes int icon, @NonNull CharSequence title, @NonNull CharSequence subtitle)
  {
    mIcon.setImageResource(icon);
    mTitle.setText(title);
    mSubtitle.setText(subtitle);
  }

  public void setChosen(boolean chosen)
  {
    mChosen = chosen;
    mMark.setBackgroundResource(chosen ? R.drawable.tz_bg_mint_circle : R.drawable.tz_bg_ring_text3);
    mCheck.setVisibility(chosen ? VISIBLE : GONE);
  }

  // setChecked(int) replaces setChecked(boolean) only from API 36 and has no compat constants yet.
  @SuppressWarnings("deprecation")
  @Override
  public void onInitializeAccessibilityNodeInfo(@NonNull AccessibilityNodeInfo info)
  {
    super.onInitializeAccessibilityNodeInfo(info);
    info.setClassName(RadioButton.class.getName());
    info.setCheckable(true);
    info.setChecked(mChosen);
  }
}
