package app.trackzero.ride;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Plays the tones (FEAT-008, Phase 32) through the phone's speaker at media volume, even with Bluetooth connected: one
 * static {@code AudioTrack} per sound, released as soon as it has played, so audio exists only while a sound plays.
 * The samples are built once, the first time each pattern is needed. Main thread.
 */
public final class TzTones
{
  private static final String TAG = "TzTones";
  /** The track is released this long after its sound should have ended. */
  private static final long RELEASE_SLACK_MS = 200;
  private static final AudioAttributes ATTRIBUTES = new AudioAttributes.Builder()
                                                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                                                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                                        .build();
  private static final Map<TonePattern, short[]> BUFFERS = new EnumMap<>(TonePattern.class);

  @NonNull
  private final Context mContext;
  private final Handler mHandler = new Handler(Looper.getMainLooper());

  public TzTones(@NonNull Context context)
  {
    mContext = context.getApplicationContext();
  }

  /** One pattern (M1's tiles). */
  public long play(@NonNull TonePattern pattern)
  {
    return play(Collections.singletonList(new ToneRules.Sound(pattern, 0)));
  }

  /** Plays the sounds; returns how long they last, ms (0 when nothing plays), for a phrase to follow them. */
  public long play(@NonNull List<ToneRules.Sound> sounds)
  {
    if (sounds.isEmpty())
      return 0;
    long endMs = 0;
    for (ToneRules.Sound s : sounds)
      endMs = Math.max(endMs, s.atMs + s.pattern.durationMs());
    final short[] mix = new short[TonePattern.samples((int) endMs) + 1];
    for (ToneRules.Sound s : sounds)
    {
      final short[] b = buffer(s.pattern);
      final int at = TonePattern.samples((int) s.atMs);
      System.arraycopy(b, 0, mix, at, Math.min(b.length, mix.length - at));
    }
    final AudioTrack track;
    try
    {
      track = new AudioTrack(ATTRIBUTES,
                             new AudioFormat.Builder()
                                 .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                 .setSampleRate(TonePattern.RATE)
                                 .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                 .build(),
                             mix.length * 2, AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE);
      track.write(mix, 0, mix.length);
      speaker(track);
      TzAudioFocus.acquire(mContext);
      track.play();
    }
    catch (IllegalStateException | IllegalArgumentException | UnsupportedOperationException e)
    {
      Log.w(TAG, "Tone not played", e);
      return 0;
    }
    mHandler.postDelayed(() -> {
      track.release();
      TzAudioFocus.release(mContext);
    }, endMs + RELEASE_SLACK_MS);
    return endMs;
  }

  @NonNull
  private static short[] buffer(@NonNull TonePattern p)
  {
    short[] b = BUFFERS.get(p);
    if (b == null)
    {
      b = p.build();
      BUFFERS.put(p, b);
    }
    return b;
  }

  /** The phone's own speaker, not a connected headset or car (Android 6+; older phones play where audio goes). */
  private void speaker(@NonNull AudioTrack track)
  {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
      return;
    final AudioManager audio = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
    for (AudioDeviceInfo d : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS))
    {
      if (d.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
      {
        track.setPreferredDevice(d);
        return;
      }
    }
  }
}
