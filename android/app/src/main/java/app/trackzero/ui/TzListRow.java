package app.trackzero.ui;

import android.content.Context;
import android.content.res.TypedArray;
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

/** 64dp settings row (B7): icon, title, detail text, chevron. Grouped on a surface1 card with inset dividers. */
public class TzListRow extends LinearLayout
{
  @NonNull
  private final TextView mDetail;

  public TzListRow(@NonNull Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
    setOrientation(HORIZONTAL);
    setGravity(Gravity.CENTER_VERTICAL);
    final int pad = getResources().getDimensionPixelSize(R.dimen.tz_space_16);
    setPadding(pad, 0, pad, 0);
    setMinimumHeight(getResources().getDimensionPixelSize(R.dimen.tz_list_row_h));
    setClickable(true);
    setFocusable(true);
    final int gap = getResources().getDimensionPixelSize(R.dimen.tz_row_gap);

    final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.TzListRow);
    final ImageView icon = new ImageView(context);
    icon.setImageResource(a.getResourceId(R.styleable.TzListRow_tzIcon, 0));
    ImageViewCompat.setImageTintList(icon, ContextCompat.getColorStateList(context, R.color.tz_text2));
    icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    final int iconSize = getResources().getDimensionPixelSize(R.dimen.tz_icon);
    addView(icon, new LayoutParams(iconSize, iconSize));

    final TextView title = new TextView(context, null, 0, R.style.TzText_RowTitle);
    title.setText(a.getString(R.styleable.TzListRow_tzTitle));
    title.setMaxLines(1);
    a.recycle();
    final LayoutParams tlp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
    tlp.setMarginStart(gap);
    addView(title, tlp);

    mDetail = new TextView(context, null, 0, R.style.TzText_RowDetail);
    mDetail.setMaxLines(1);
    final LayoutParams dlp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    dlp.setMarginStart(gap);
    addView(mDetail, dlp);

    final ImageView chevron = new ImageView(context);
    chevron.setImageResource(R.drawable.ic_tz_chev);
    ImageViewCompat.setImageTintList(chevron, ContextCompat.getColorStateList(context, R.color.tz_text3));
    chevron.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    final int chev = getResources().getDimensionPixelSize(R.dimen.tz_chevron);
    final LayoutParams clp = new LayoutParams(chev, chev);
    clp.setMarginStart(gap);
    addView(chevron, clp);
  }

  public void setDetail(@Nullable CharSequence detail)
  {
    mDetail.setText(detail);
  }
}
