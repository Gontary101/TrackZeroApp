package app.trackzero;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import app.organicmaps.BuildConfig;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * What the map and the ride did, for finding out why (Phase 16): the last 2,000 events in memory, written to
 * {@code files/tz/trace.txt} on request. On in debug builds; in release builds behind a hidden switch (About).
 * <p>
 * Callers check {@link #on()} before building the text, so a trace call costs one static read when tracing is off:
 * {@code if (TzTrace.on()) TzTrace.log("map", "followRider " + headingUp);}
 */
public final class TzTrace
{
  static final int SIZE = 2000;
  private static final String FILE = "trace.txt";
  private static final String LOG_TAG = "TzTrace";

  /** Debug builds, also -PtzMeasure ones (not debuggable, so not BuildConfig.DEBUG), always trace. */
  private static final boolean DEBUG_BUILD = "debug".equals(BuildConfig.BUILD_TYPE);

  private static volatile boolean sOn = DEBUG_BUILD;
  private static boolean sLoaded;

  private static final long[] sTimes = new long[SIZE];
  private static final String[] sTags = new String[SIZE];
  private static final String[] sTexts = new String[SIZE];
  private static int sNext;
  private static int sCount;

  private TzTrace() {}

  /** Reads the hidden switch once per process (release builds). */
  public static void init(@NonNull Context c)
  {
    if (sLoaded)
      return;
    sLoaded = true;
    if (!DEBUG_BUILD)
      sOn = TzPrefs.trace(c);
  }

  public static boolean on()
  {
    return sOn;
  }

  /** The hidden switch: debug builds always trace. */
  public static void setOn(@NonNull Context c, boolean on)
  {
    TzPrefs.setTrace(c, on);
    sOn = on || DEBUG_BUILD;
  }

  /** JVM tests: tracing reads the Android clock. */
  @VisibleForTesting
  public static void enableForTests(boolean on)
  {
    sOn = on;
  }

  public static void log(@NonNull String tag, @NonNull String text)
  {
    if (!sOn)
      return;
    final long now = SystemClock.elapsedRealtime();
    put(now, tag, text);
    // Debug builds also write to logcat, so test scripts can read the events without the share sheet.
    if (DEBUG_BUILD)
      Log.i(LOG_TAG, now + " " + tag + " " + text);
  }

  static void put(long timeMs, @NonNull String tag, @NonNull String text)
  {
    synchronized (sTimes)
    {
      sTimes[sNext] = timeMs;
      sTags[sNext] = tag;
      sTexts[sNext] = text;
      sNext = (sNext + 1) % SIZE;
      if (sCount < SIZE)
        sCount++;
    }
  }

  /** The events kept, oldest first, as "time\ttag\ttext". */
  @NonNull
  static String[] lines()
  {
    synchronized (sTimes)
    {
      final String[] lines = new String[sCount];
      for (int i = 0; i < sCount; i++)
      {
        final int j = (sNext - sCount + i + SIZE) % SIZE;
        lines[i] = sTimes[j] + "\t" + sTags[j] + "\t" + sTexts[j];
      }
      return lines;
    }
  }

  static void clear()
  {
    synchronized (sTimes)
    {
      sNext = 0;
      sCount = 0;
    }
  }

  /** Coordinates as the trace writes them: 5 decimals (about 1 m). */
  @NonNull
  public static String ll(double lat, double lon)
  {
    return String.format(Locale.US, "%.5f,%.5f", lat, lon);
  }

  /** Writes the events, oldest first, to {@code files/tz/trace.txt} (call off the main thread). */
  @NonNull
  public static File dump(@NonNull Context c) throws IOException
  {
    final String[] lines = lines();
    final File dir = new File(c.getFilesDir(), "tz");
    //noinspection ResultOfMethodCallIgnored
    dir.mkdirs();
    final File file = new File(dir, FILE);
    try (Writer out = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))
    {
      out.write("# TrackZero trace " + BuildConfig.VERSION_NAME + ", now " + SystemClock.elapsedRealtime()
                + " ms since boot (elapsedRealtime)\n");
      for (String line : lines)
        out.write(line + "\n");
    }
    return file;
  }
}
