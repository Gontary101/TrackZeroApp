package app.trackzero.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.widget.ImageViewCompat;
import app.organicmaps.R;

/** 72dp settings row with icon, title, optional subtitle and a switch; the whole row toggles (R6). */
public class TzToggleRow extends LinearLayout
{
  public interface Listener
  {
    void onToggled(boolean checked);
  }

  @NonNull
  private final TextView mSubtitle;
  @NonNull
  private final TzSwitch mSwitch;
  private boolean mChecked;
  @Nullable
  private Listener mListener;

  public TzToggleRow(@NonNull Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
    setOrientation(HORIZONTAL);
    setGravity(Gravity.CENTER_VERTICAL);
    setBackgroundResource(R.drawable.tz_bg_surface3_r20);
    final int pad = getResources().getDimensionPixelSize(R.dimen.tz_space_16);
    setPadding(pad, 0, pad, 0);
    setMinimumHeight(getResources().getDimensionPixelSize(R.dimen.tz_toggle_row_h));
    setClickable(true);
    setFocusable(true);
    final int gap = getResources().getDimensionPixelSize(R.dimen.tz_row_gap);

    final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.TzToggleRow);
    final ImageView icon = new ImageView(context);
    icon.setImageResource(a.getResourceId(R.styleable.TzToggleRow_tzIcon, 0));
    ImageViewCompat.setImageTintList(icon, ContextCompat.getColorStateList(context, R.color.tz_text1));
    icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    final int iconSize = getResources().getDimensionPixelSize(R.dimen.tz_icon);
    addView(icon, new LayoutParams(iconSize, iconSize));

    final LinearLayout texts = new LinearLayout(context);
    texts.setOrientation(VERTICAL);
    final TextView title = new TextView(context, null, 0, R.style.TzText_RowTitle);
    title.setText(a.getString(R.styleable.TzToggleRow_tzTitle));
    texts.addView(title);
    mSubtitle = new TextView(context, null, 0, R.style.TzText_RowSubtitle);
    setSubtitle(a.getString(R.styleable.TzToggleRow_tzSubtitle));
    final LayoutParams slp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    slp.topMargin = getResources().getDimensionPixelSize(R.dimen.tz_turn_line_gap);
    texts.addView(mSubtitle, slp);
    a.recycle();
    final LayoutParams tlp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
    tlp.setMarginStart(gap);
    tlp.setMarginEnd(gap);
    addView(texts, tlp);

    mSwitch = new TzSwitch(context, null);
    addView(mSwitch);

    setOnClickListener(v -> {
      setChecked(!mChecked, true);
      if (mListener != null)
        mListener.onToggled(mChecked);
    });
  }

  public void setSubtitle(@Nullable CharSequence subtitle)
  {
    mSubtitle.setText(subtitle);
    mSubtitle.setVisibility(subtitle == null || subtitle.length() == 0 ? GONE : VISIBLE);
  }

  public void setListener(@Nullable Listener listener)
  {
    mListener = listener;
  }

  public boolean isChecked()
  {
    return mChecked;
  }

  public void setChecked(boolean checked, boolean animate)
  {
    mChecked = checked;
    mSwitch.setChecked(checked, animate);
  }

  // setChecked(int) replaces setChecked(boolean) only from API 36 and has no compat constants yet.
  @SuppressWarnings("deprecation")
  @Override
  public void onInitializeAccessibilityNodeInfo(@NonNull AccessibilityNodeInfo info)
  {
    super.onInitializeAccessibilityNodeInfo(info);
    final AccessibilityNodeInfoCompat compat = AccessibilityNodeInfoCompat.wrap(info);
    compat.setClassName(Switch.class.getName());
    compat.setCheckable(true);
    compat.setChecked(mChecked);
  }
}
