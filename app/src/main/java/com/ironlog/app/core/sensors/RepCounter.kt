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
import kotlin.math.abs
import kotlin.math.sqrt

data class RepSignal(
    val repCount: Int,
    /** Smoothed acceleration magnitude, used to draw the live trace. */
    val magnitude: Float,
    val isMoving: Boolean
)

/**
 * Counts reps from the accelerometer while the phone rests on the user's body or
 * in a pocket during a set.
 *
 * How it works
 * ------------
 * The raw vector includes gravity, so the first step is a high-pass filter: a
 * slow-moving low-pass estimate of gravity is subtracted from each sample,
 * leaving only the acceleration the user produced. Its magnitude is then smoothed
 * with an exponential moving average to take out sensor noise.
 *
 * A rep is a full cycle of the resulting signal, so counting is a Schmitt trigger:
 * the signal must cross an upper threshold to arm, then fall below a lower
 * threshold to fire. Two separate thresholds — rather than one — stop a signal
 * hovering near the boundary from registering dozens of phantom reps. A minimum
 * interval between reps rejects anything faster than a human can physically move.
 *
 * This is a best-effort assist, not a measurement device: the count is offered as
 * a suggestion the user can accept or overwrite, never written to a set silently.
 */
@Singleton
class RepCounter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    fun isAvailable(): Boolean =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null

    fun counts(): Flow<RepSignal> = callbackFlow {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null) {
            close()
            return@callbackFlow
        }

        val gravity = FloatArray(3)
        var smoothed = 0f
        var armed = false
        var reps = 0
        var lastRepAt = 0L
        var samples = 0

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // Low-pass estimate of gravity, subtracted to isolate user motion.
                for (i in 0..2) {
                    gravity[i] = GRAVITY_ALPHA * gravity[i] + (1 - GRAVITY_ALPHA) * event.values[i]
                }
                val x = event.values[0] - gravity[0]
                val y = event.values[1] - gravity[1]
                val z = event.values[2] - gravity[2]
                val magnitude = sqrt(x * x + y * y + z * z)

                smoothed = SMOOTHING_ALPHA * smoothed + (1 - SMOOTHING_ALPHA) * magnitude
                samples++

                // Skip the first samples while the gravity estimate settles,
                // otherwise startup transients count as reps.
                if (samples < WARMUP_SAMPLES) {
                    trySend(RepSignal(reps, smoothed, false))
                    return
                }

                val now = System.currentTimeMillis()
                if (!armed && smoothed > UPPER_THRESHOLD) {
                    armed = true
                } else if (armed && smoothed < LOWER_THRESHOLD) {
                    armed = false
                    if (now - lastRepAt >= MIN_REP_INTERVAL_MS) {
                        reps++
                        lastRepAt = now
                    }
                }

                trySend(RepSignal(reps, smoothed, abs(smoothed) > LOWER_THRESHOLD))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { sensorManager.unregisterListener(listener) }
    }

    private companion object {
        const val GRAVITY_ALPHA = 0.8f
        const val SMOOTHING_ALPHA = 0.75f
        const val UPPER_THRESHOLD = 2.2f   // m/s^2 above which a rep is "armed"
        const val LOWER_THRESHOLD = 0.9f   // m/s^2 below which it fires
        const val MIN_REP_INTERVAL_MS = 600L
        const val WARMUP_SAMPLES = 20
    }
}
