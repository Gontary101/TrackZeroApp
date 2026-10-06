package app.trackzero.ride;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.R;
import app.trackzero.TzIo;
import app.trackzero.TzPrefs;
import app.trackzero.follow.Maneuver;
import app.trackzero.follow.RouteManeuver;
import app.trackzero.format.TzFormat;
import app.trackzero.ui.TzManeuvers;
import java.util.Locale;

/**
 * Spoken turn announcements (plan §10.4) with the system text-to-speech, ducking other audio while it talks.
 * Phrases are dropped, not queued, while the engine starts or when the voice setting is off: a late "turn right"
 * is worse than none.
 */
final class TzVoice
{
  private static final String TAG = "TzVoice";
  private static final double M_PER_FT = 0.3048;

  @NonNull
  private final Context mContext;
  @Nullable
  private TextToSpeech mTts;
  private boolean mReady;
  private int mPending;
  private int mNextId;

  TzVoice(@NonNull Context context)
  {
    mContext = context;
    mTts = new TextToSpeech(context, this::onInit);
  }

  private void onInit(int status)
  {
    final TextToSpeech tts = mTts;
    if (tts == null || status != TextToSpeech.SUCCESS)
    {
      Log.w(TAG, "Text-to-speech unavailable: " + status);
      return;
    }
    // The phrases' own language, so an English phrase is never read by a French voice.
    final Locale language = Locale.forLanguageTag(mContext.getString(R.string.tz_voice_language));
    final int result = tts.setLanguage(language);
    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED)
    {
      Log.w(TAG, "No voice for " + language);
      return;
    }
    tts.setAudioAttributes(TzAudioFocus.GUIDANCE);
    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
      @Override
      public void onStart(String id) {}

      @Override
      public void onDone(String id)
      {
        TzIo.post(TzVoice.this::utteranceEnded);
      }

      // Abstract in UtteranceProgressListener: required, though onError(String, int) supersedes it.
      @SuppressWarnings("deprecation")
      @Override
      public void onError(String id)
      {
        TzIo.post(TzVoice.this::utteranceEnded);
      }
    });
    mReady = true;
  }

  /** {@code afterMs}: a tone plays first; the words wait for it (Phase 32). */
  void turnSoon(@NonNull RouteManeuver m, double distanceM, long afterMs)
  {
    final String distance = spokenDistance(distanceM);
    // A stop the rider added (Phase 30) is named.
    final String what = m.type == Maneuver.STOP ? mContext.getString(R.string.tz_voice_stop, m.street)
                                                : TzManeuvers.spoken(mContext, m.type, m.exit);
    speak(mContext.getString(R.string.tz_voice_in, distance, what), afterMs);
  }

  void turnNow(@NonNull RouteManeuver m, long afterMs)
  {
    speak(TzManeuvers.spoken(mContext, m.type, m.exit), afterMs);
  }

  void say(int stringRes, long afterMs)
  {
    speak(mContext.getString(stringRes), afterMs);
  }

  void say(@NonNull String text, long afterMs)
  {
    speak(text, afterMs);
  }

  void shutdown()
  {
    if (mTts != null)
      mTts.shutdown();
    mTts = null;
    mReady = false;
    if (mPending > 0)
      TzAudioFocus.release(mContext);
    mPending = 0;
  }

  /** "200 meters" / "650 feet": rounded the way people say it. */
  @NonNull
  private String spokenDistance(double meters)
  {
    if (TzPrefs.units(mContext) == TzFormat.Units.IMPERIAL)
      return mContext.getString(R.string.tz_voice_feet, (int) Math.round(meters / M_PER_FT / 50) * 50);
    final int step = meters >= 100 ? 50 : 10;
    return mContext.getString(R.string.tz_voice_meters, (int) Math.round(meters / step) * step);
  }

  private void speak(@NonNull String text, long afterMs)
  {
    final TextToSpeech tts = mTts;
    if (tts == null || !mReady || !TzPrefs.voice(mContext))
      return;
    if (mPending == 0)
      TzAudioFocus.acquire(mContext);
    if (afterMs > 0)
    {
      // Silence for as long as the tone plays: the phrase follows it.
      mPending++;
      if (tts.playSilentUtterance(afterMs, TextToSpeech.QUEUE_ADD, "tz" + mNextId++) != TextToSpeech.SUCCESS)
        utteranceEnded();
    }
    mPending++;
    if (tts.speak(text, TextToSpeech.QUEUE_ADD, null, "tz" + mNextId++) != TextToSpeech.SUCCESS)
      utteranceEnded();
  }

  private void utteranceEnded()
  {
    if (mPending == 0)
      return;
    if (--mPending == 0)
      TzAudioFocus.release(mContext);
  }
}
