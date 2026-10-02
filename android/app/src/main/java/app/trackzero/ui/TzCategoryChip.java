package app.trackzero.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;
import app.organicmaps.R;

/** Search category chip (B2): 44dp pill, 18dp icon; selected = surface3 + mint icon. */
public class TzCategoryChip extends AppCompatTextView
{
  private boolean mChecked;

  public TzCategoryChip(@NonNull Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
    TextViewCompat.setTextAppearance(this, R.style.TzText_Option);
    setGravity(Gravity.CENTER_VERTICAL);
    // 44dp pill (B2), 48dp touch target: the pill is drawn inset.
    setMinHeight(getResources().getDimensionPixelSize(R.dimen.tz_touch_min_browse));
    setPaddingRelative(getResources().getDimensionPixelSize(R.dimen.tz_space_12), 0,
                       getResources().getDimensionPixelSize(R.dimen.tz_space_16), 0);
    setCompoundDrawablePadding(getResources().getDimensionPixelSize(R.dimen.tz_space_8));
    setClickable(true);
    setFocusable(true);
    setChecked(false);
  }

  public void setIcon(@DrawableRes int icon)
  {
    final Drawable d = ContextCompat.getDrawable(getContext(), icon).mutate();
    final int size = getResources().getDimensionPixelSize(R.dimen.tz_chip_icon);
    d.setBounds(0, 0, size, size);
    setCompoundDrawablesRelative(d, null, null, null);
    setChecked(mChecked);
  }

  public boolean isChecked()
  {
    return mChecked;
  }

  public void setChecked(boolean checked)
  {
    mChecked = checked;
    setSelected(checked);
    final int inset = (getResources().getDimensionPixelSize(R.dimen.tz_touch_min_browse)
                       - getResources().getDimensionPixelSize(R.dimen.tz_chip_h)) / 2;
    // setBackground() takes the drawable's padding (the inset): keep the chip's own.
    final int start = getPaddingStart(), end = getPaddingEnd();
    setBackground(new InsetDrawable(ContextCompat.getDrawable(getContext(), checked ? R.drawable.tz_bg_surface3_r22
                                                                                    : R.drawable.tz_bg_surface2_r22),
                                    0, inset, 0, inset));
    setPaddingRelative(start, 0, end, 0);
    TextViewCompat.setCompoundDrawableTintList(
        this, ContextCompat.getColorStateList(getContext(), checked ? R.color.tz_mint : R.color.tz_text2));
  }
}
