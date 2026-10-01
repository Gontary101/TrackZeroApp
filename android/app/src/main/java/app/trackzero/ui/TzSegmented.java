package app.trackzero.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import app.organicmaps.R;

/** Pill segmented control (B3 filter, R5 Climb / Full route). Options are 44dp, or 40dp when compact. */
public class TzSegmented extends LinearLayout
{
  public interface Listener
  {
    void onSelected(int index);
  }

  private final boolean mCompact;
  private int mSelected = -1;
  @Nullable
  private Listener mListener;

  public TzSegmented(@NonNull Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
    final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.TzSegmented);
    mCompact = a.getBoolean(R.styleable.TzSegmented_tzCompact, false);
    a.recycle();
    setOrientation(HORIZONTAL);
    setBackgroundResource(R.drawable.tz_bg_well_r26);
    final int pad = getResources().getDimensionPixelSize(R.dimen.tz_space_4);
    setPadding(pad, pad, pad, pad);
  }

  public void setOptions(@NonNull CharSequence... options)
  {
    removeAllViews();
    final int h = getResources().getDimensionPixelSize(mCompact ? R.dimen.tz_segment_h_compact : R.dimen.tz_segment_h);
    final int pad = getResources().getDimensionPixelSize(R.dimen.tz_segment_pad);
    for (int i = 0; i < options.length; i++)
    {
      final TextView option = new TextView(getContext(), null, 0, R.style.TzText_Option);
      option.setText(options[i]);
      option.setGravity(Gravity.CENTER);
      option.setPadding(pad, 0, pad, 0);
      final int index = i;
      option.setOnClickListener(v -> {
        if (index == mSelected)
          return;
        select(index);
        if (mListener != null)
          mListener.onSelected(index);
      });
      final LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, h);
      if (i > 0)
        lp.setMarginStart(getResources().getDimensionPixelSize(R.dimen.tz_space_4));
      addView(option, lp);
    }
    mSelected = -1;
    select(0);
  }

  public void setListener(@Nullable Listener listener)
  {
    mListener = listener;
  }

  public int getSelected()
  {
    return mSelected;
  }

  /** Selects without notifying the listener. */
  public void select(int index)
  {
    mSelected = index;
    for (int i = 0; i < getChildCount(); i++)
    {
      final TextView option = (TextView) getChildAt(i);
      final boolean on = i == index;
      option.setSelected(on);
      option.setBackgroundResource(on ? (mCompact ? R.drawable.tz_bg_surface3_r20 : R.drawable.tz_bg_surface3_r22) : 0);
      option.setTextColor(ContextCompat.getColor(getContext(), on ? R.color.tz_text1 : R.color.tz_text2));
    }
  }
}
