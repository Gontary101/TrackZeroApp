package app.trackzero;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One background thread for all TrackZero file IO (serialises index writes), results posted to the UI thread. */
public final class TzIo
{
  public interface Callback<T>
  {
    void onResult(T value);
  }

  /** Background work; failures are part of the result, not exceptions. */
  public interface Work<T>
  {
    T run();
  }

  private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
    final Thread t = new Thread(r, "tz-io");
    t.setPriority(Thread.MIN_PRIORITY);
    return t;
  });
  private static final Handler MAIN = new Handler(Looper.getMainLooper());

  private TzIo() {}

  public static void run(@NonNull Runnable task)
  {
    IO.execute(task);
  }

  public static void post(@NonNull Runnable task)
  {
    MAIN.post(task);
  }

  /** Runs {@code work} on the IO thread and delivers its result on the UI thread. */
  public static <T> void submit(@NonNull Work<T> work, @NonNull Callback<T> callback)
  {
    IO.execute(() -> {
      final T result = work.run();
      MAIN.post(() -> callback.onResult(result));
    });
  }

  @NonNull
  public static String readText(@NonNull File file) throws IOException
  {
    try (FileInputStream in = new FileInputStream(file))
    {
      final ByteArrayOutputStream out = new ByteArrayOutputStream((int) file.length());
      final byte[] buf = new byte[16 * 1024];
      for (int n; (n = in.read(buf)) > 0;)
        out.write(buf, 0, n);
      return out.toString(StandardCharsets.UTF_8.name());
    }
  }

  /** Writes {@code text} to {@code file} atomically: temp file in the same directory, then rename. */
  public static void writeAtomically(@NonNull File file, @NonNull String text) throws IOException
  {
    final File tmp = new File(file.getPath() + ".tmp");
    try (FileOutputStream out = new FileOutputStream(tmp))
    {
      out.write(text.getBytes(StandardCharsets.UTF_8));
      out.getFD().sync();
    }
    if (!tmp.renameTo(file))
      throw new IOException("Cannot rename " + tmp + " to " + file);
  }
}
