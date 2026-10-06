package app.trackzero.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.InsetDrawable;
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
  @Nullable
  private Listener mReselect;
  private boolean mFill;

  public TzSegmented(@NonNull Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
    final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.TzSegmented);
    mCompact = a.getBoolean(R.styleable.TzSegmented_tzCompact, false);
    a.recycle();
    setOrientation(HORIZONTAL);
    setBackgroundResource(R.drawable.tz_bg_well_r26);
    // Options reach the top and bottom edges (their pill is inset): the whole control is tappable, ≥ 48dp.
    final int pad = getResources().getDimensionPixelSize(R.dimen.tz_space_4);
    setPadding(pad, 0, pad, 0);
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
        {
          if (mReselect != null)
            mReselect.onSelected(index);
          return;
        }
        select(index);
        if (mListener != null)
          mListener.onSelected(index);
      });
      final LayoutParams lp = mFill ? new LayoutParams(0, h + 2 * inset(), 1f)
                                    : new LayoutParams(LayoutParams.WRAP_CONTENT, h + 2 * inset());
      if (i > 0)
        lp.setMarginStart(getResources().getDimensionPixelSize(R.dimen.tz_space_4));
      addView(option, lp);
    }
    mSelected = -1;
    select(0);
  }

  /** The well's 4dp rim around the selected pill. */
  private int inset()
  {
    return getResources().getDimensionPixelSize(R.dimen.tz_space_4);
  }

  public void setListener(@Nullable Listener listener)
  {
    mListener = listener;
  }

  /** A tap on the option already selected (E1's second wording, Phase 34). */
  public void setReselectListener(@Nullable Listener listener)
  {
    mReselect = listener;
  }

  /** The options share the width equally (E1's two wordings, Phase 34); call before {@link #setOptions}. */
  public void setFill(boolean fill)
  {
    mFill = fill;
  }

  /** Renames an option in place. */
  public void setOptionText(int index, @NonNull CharSequence text)
  {
    ((TextView) getChildAt(index)).setText(text);
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
      // setBackground() takes the drawable's padding (the inset): keep the option's own.
      final int start = option.getPaddingStart(), end = option.getPaddingEnd();
      option.setBackground(on ? new InsetDrawable(ContextCompat.getDrawable(getContext(), mCompact
                                                                                        ? R.drawable.tz_bg_surface3_r20
                                                                                        : R.drawable.tz_bg_surface3_r22),
                                                  0, inset(), 0, inset())
                              : null);
      option.setPaddingRelative(start, 0, end, 0);
      option.setTextColor(ContextCompat.getColor(getContext(), on ? R.color.tz_text1 : R.color.tz_text2));
    }
  }
}
