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

  /**
   * Fixes may arrive in batches (every {@code maxDelayMs}, recorded once a second all the same) so the phone sleeps
   * between them; 0 delivers each at once. Sources that can't batch ignore it.
   */
  default void setMaxDelay(long maxDelayMs) {}
}
