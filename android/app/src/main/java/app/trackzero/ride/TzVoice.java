package app.trackzero.ride;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.R;
import app.trackzero.TzIo;
import app.trackzero.TzPrefs;
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
  @NonNull
  private final AudioManager mAudio;
  @NonNull
  private final AudioAttributes mAttributes =
      new AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
          .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
          .build();
  @Nullable
  private AudioFocusRequest mFocusRequest;
  @Nullable
  private TextToSpeech mTts;
  private boolean mReady;
  private int mPending;
  private int mNextId;

  TzVoice(@NonNull Context context)
  {
    mContext = context;
    mAudio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
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
    tts.setAudioAttributes(mAttributes);
    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
      @Override
      public void onStart(String id) {}

      @Override
      public void onDone(String id)
      {
        TzIo.post(TzVoice.this::utteranceEnded);
      }

      @Override
      public void onError(String id)
      {
        TzIo.post(TzVoice.this::utteranceEnded);
      }
    });
    mReady = true;
  }

  void turnSoon(@NonNull RouteManeuver m, double distanceM)
  {
    final String distance = spokenDistance(distanceM);
    speak(mContext.getString(R.string.tz_voice_in, distance, mContext.getString(TzManeuvers.phrase(m.type))));
  }

  void turnNow(@NonNull RouteManeuver m)
  {
    speak(mContext.getString(TzManeuvers.phrase(m.type)));
  }

  void say(int stringRes)
  {
    speak(mContext.getString(stringRes));
  }

  void shutdown()
  {
    if (mTts != null)
      mTts.shutdown();
    mTts = null;
    mReady = false;
    if (mPending > 0)
      abandonFocus();
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

  private void speak(@NonNull String text)
  {
    final TextToSpeech tts = mTts;
    if (tts == null || !mReady || !TzPrefs.voice(mContext))
      return;
    if (mPending == 0)
      requestFocus();
    mPending++;
    if (tts.speak(text, TextToSpeech.QUEUE_ADD, null, "tz" + mNextId++) != TextToSpeech.SUCCESS)
      utteranceEnded();
  }

  private void utteranceEnded()
  {
    if (mPending == 0)
      return;
    if (--mPending == 0)
      abandonFocus();
  }

  private void requestFocus()
  {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
    {
      mFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                          .setAudioAttributes(mAttributes)
                          .build();
      mAudio.requestAudioFocus(mFocusRequest);
    }
    else
    {
      mAudio.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
    }
  }

  private void abandonFocus()
  {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
    {
      if (mFocusRequest != null)
        mAudio.abandonAudioFocusRequest(mFocusRequest);
      mFocusRequest = null;
    }
    else
    {
      mAudio.abandonAudioFocus(null);
    }
  }
}
