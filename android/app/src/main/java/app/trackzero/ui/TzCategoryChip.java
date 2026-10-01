package app.trackzero.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
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
    setMinHeight(getResources().getDimensionPixelSize(R.dimen.tz_chip_h));
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
    setBackgroundResource(checked ? R.drawable.tz_bg_surface3_r22 : R.drawable.tz_bg_surface2_r22);
    TextViewCompat.setCompoundDrawableTintList(
        this, ContextCompat.getColorStateList(getContext(), checked ? R.color.tz_mint : R.color.tz_text2));
  }
}
