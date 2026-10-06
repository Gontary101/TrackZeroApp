package app.trackzero.ride;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Audio focus shared by the tones and the voice (Phase 32): other audio ducks while either plays, and a tone followed
 * by its phrase is one focus session. Held while anything holds it; main thread.
 */
final class TzAudioFocus
{
  static final AudioAttributes GUIDANCE = new AudioAttributes.Builder()
                                              .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                                              .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                              .build();

  private static int sHolders;
  @Nullable
  private static AudioFocusRequest sRequest;

  private TzAudioFocus() {}

  static void acquire(@NonNull Context c)
  {
    if (sHolders++ > 0)
      return;
    final AudioManager audio = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
    {
      sRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                     .setAudioAttributes(GUIDANCE)
                     .build();
      audio.requestAudioFocus(sRequest);
    }
    else
    {
      requestLegacy(audio);
    }
  }

  static void release(@NonNull Context c)
  {
    if (sHolders == 0 || --sHolders > 0)
      return;
    final AudioManager audio = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
    {
      if (sRequest != null)
        audio.abandonAudioFocusRequest(sRequest);
      sRequest = null;
    }
    else
    {
      abandonLegacy(audio);
    }
  }

  /** Android 7.1 and older: the only audio focus API there. */
  @SuppressWarnings("deprecation")
  private static void requestLegacy(@NonNull AudioManager audio)
  {
    audio.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
  }

  @SuppressWarnings("deprecation")
  private static void abandonLegacy(@NonNull AudioManager audio)
  {
    audio.abandonAudioFocus(null);
  }
}
