package com.ironlog.app.core.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the phone's hardware pedometer.
 *
 * The important quirk of [Sensor.TYPE_STEP_COUNTER] is that it reports steps
 * since the device last booted, not since midnight, and it keeps counting while
 * the app is closed. Treating the raw value as "today's steps" would show a
 * number in the tens of thousands on a phone that has been up for a week.
 *
 * So the app stores a per-day baseline: the first raw reading seen on a given
 * calendar day. Today's total is (current reading - baseline). A reboot resets the
 * hardware counter to zero, which shows up as a reading *below* the stored
 * baseline — that case is detected and the baseline is re-taken rather than
 * producing a negative step count.
 *
 * TYPE_STEP_COUNTER is preferred over TYPE_STEP_DETECTOR because the counter is
 * maintained in low-power hardware, so it does not hold a wakelock or drain the
 * battery while a cardio session records.
 */
@Singleton
class StepCounter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    fun isAvailable(): Boolean =
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    /** Emits the raw cumulative sensor reading. Callers apply their own baseline. */
    fun rawCounts(): Flow<Float> = callbackFlow {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (sensor == null) {
            close()
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(event.values.firstOrNull() ?: 0f)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        // SENSOR_DELAY_NORMAL is plenty: the hardware batches steps and there is
        // no benefit to sampling a pedometer faster than the user can walk.
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { sensorManager.unregisterListener(listener) }
    }

    /**
     * Resolves a raw reading against a stored daily baseline.
     * Returns the step total for the day and the baseline that should be stored.
     */
    fun resolve(raw: Float, storedBaseline: Float?): StepResolution {
        val baseline = when {
            storedBaseline == null -> raw          // first reading today
            raw < storedBaseline -> raw            // device rebooted; counter reset
            else -> storedBaseline
        }
        return StepResolution(
            steps = (raw - baseline).toInt().coerceAtLeast(0),
            baseline = baseline
        )
    }
}

data class StepResolution(val steps: Int, val baseline: Float)
