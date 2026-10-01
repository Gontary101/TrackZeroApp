package app.trackzero.ride;

import androidx.annotation.NonNull;

/** Delivers fixes on the main thread between {@link #start} and {@link #stop}. */
public interface TzLocationSource
{
  interface Listener
  {
    void onFix(@NonNull TzFix fix);
  }

  void start(@NonNull Listener listener);

  void stop();
}
