package app.trackzero.ui;

import android.text.TextUtils;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** View helpers for screens that update every second. */
public final class TzViews
{
  private TzViews() {}

  /**
   * Sets the text only when it changes: a TextView sized to its text asks for a layout pass on every setText, and
   * the ride screens set most of their numbers once a second to the same value.
   */
  public static void setText(@NonNull TextView view, @Nullable CharSequence text)
  {
    if (!TextUtils.equals(view.getText(), text))
      view.setText(text);
  }
}
