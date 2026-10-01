package app.trackzero.ride;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Pressure altitude (standard atmosphere) once a second, on devices with a barometer. */
final class BarometerSource implements SensorEventListener
{
  interface Listener
  {
    void onPressureAltitude(double altM);
  }

  private static final int PERIOD_US = 1_000_000;

  @Nullable
  private final SensorManager mManager;
  @Nullable
  private final Sensor mSensor;
  @Nullable
  private Listener mListener;

  BarometerSource(@NonNull Context context)
  {
    mManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    mSensor = mManager == null ? null : mManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
  }

  void start(@NonNull Listener listener)
  {
    if (mSensor == null)
      return;
    mListener = listener;
    mManager.registerListener(this, mSensor, PERIOD_US);
  }

  void stop()
  {
    if (mSensor == null)
      return;
    mManager.unregisterListener(this);
    mListener = null;
  }

  @Override
  public void onSensorChanged(SensorEvent event)
  {
    if (mListener != null)
      mListener.onPressureAltitude(SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE,
                                                             event.values[0]));
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
