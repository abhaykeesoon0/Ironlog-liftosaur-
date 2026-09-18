package com.ironlog.app.core.location

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.ironlog.app.core.notifications.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CardioProgress(
    val points: List<RoutePoint> = emptyList(),
    val distanceMeters: Double = 0.0,
    val elapsedSeconds: Int = 0,
    val paceSecondsPerKm: Int = 0,
    val isTracking: Boolean = false,
    val isPaused: Boolean = false,
    val hasSignal: Boolean = false,
    val error: String? = null
)

/**
 * Keeps GPS sampling alive for the length of a run.
 *
 * Android freezes background processes aggressively, so collecting location in a
 * ViewModel would stop the moment the screen turns off — which is exactly when a
 * runner puts the phone in an armband. A foreground service with the `location`
 * type is the sanctioned way to keep sampling, and the ongoing notification is
 * the honest disclosure that tracking is happening.
 *
 * Progress is exposed as static state rather than through binding: there is at
 * most one session at a time, and the screen needs to reattach to it after a
 * rotation or a return from background without a bind/unbind dance.
 */
@AndroidEntryPoint
class CardioTrackingService : Service() {

    @Inject lateinit var locationTracker: LocationTracker
    @Inject lateinit var notifications: NotificationHelper

    private var scope: CoroutineScope? = null
    private var tickJob: Job? = null
    private var startedAt = 0L
    private var pausedAccumulatedSeconds = 0
    private var pausedAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP -> stopTracking()
        }
        return START_STICKY
    }

    private fun startTracking() {
        if (_progress.value.isTracking) return

        startedAt = System.currentTimeMillis()
        pausedAccumulatedSeconds = 0
        _progress.value = CardioProgress(isTracking = true)

        startForeground(
            NotificationHelper.ID_CARDIO,
            notifications.cardioTracking(0.0, 0, "—")
        )

        val activeScope = scope ?: return

        locationTracker.trackRoute()
            .onEach { point -> onNewPoint(point) }
            .catch { throwable ->
                _progress.value = _progress.value.copy(
                    error = throwable.message ?: "Location tracking stopped",
                    isTracking = false
                )
                stopTracking()
            }
            .launchIn(activeScope)

        tickJob = activeScope.launch {
            while (isActive) {
                delay(1000)
                if (_progress.value.isPaused) continue
                val current = _progress.value
                val elapsed = elapsedSeconds()
                val pace = GeoMath.avgPaceSecondsPerKm(current.distanceMeters, elapsed)
                _progress.value = current.copy(elapsedSeconds = elapsed, paceSecondsPerKm = pace)
                startForeground(
                    NotificationHelper.ID_CARDIO,
                    notifications.cardioTracking(
                        current.distanceMeters,
                        elapsed,
                        GeoMath.formatPace(pace)
                    )
                )
            }
        }
    }

    private fun onNewPoint(point: RoutePoint) {
        val current = _progress.value
        if (current.isPaused) return

        val points = current.points + point
        val added = current.points.lastOrNull()
            ?.let { GeoMath.distanceMeters(it, point) } ?: 0.0

        _progress.value = current.copy(
            points = points,
            distanceMeters = current.distanceMeters + added,
            hasSignal = true
        )
    }

    private fun pause() {
        if (!_progress.value.isTracking || _progress.value.isPaused) return
        pausedAt = System.currentTimeMillis()
        _progress.value = _progress.value.copy(isPaused = true)
    }

    private fun resume() {
        if (!_progress.value.isPaused) return
        pausedAccumulatedSeconds += ((System.currentTimeMillis() - pausedAt) / 1000).toInt()
        pausedAt = 0L
        _progress.value = _progress.value.copy(isPaused = false)
    }

    /** Wall-clock elapsed, minus any time spent paused. */
    private fun elapsedSeconds(): Int =
        (((System.currentTimeMillis() - startedAt) / 1000).toInt() - pausedAccumulatedSeconds)
            .coerceAtLeast(0)

    private fun stopTracking() {
        tickJob?.cancel()
        tickJob = null
        _progress.value = _progress.value.copy(isTracking = false, isPaused = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        tickJob?.cancel()
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.ironlog.app.CARDIO_START"
        const val ACTION_PAUSE = "com.ironlog.app.CARDIO_PAUSE"
        const val ACTION_RESUME = "com.ironlog.app.CARDIO_RESUME"
        const val ACTION_STOP = "com.ironlog.app.CARDIO_STOP"

        private val _progress = MutableStateFlow(CardioProgress())
        val progress: StateFlow<CardioProgress> = _progress.asStateFlow()

        fun reset() {
            _progress.value = CardioProgress()
        }

        fun start(context: Context) = send(context, ACTION_START, foreground = true)
        fun pause(context: Context) = send(context, ACTION_PAUSE)
        fun resume(context: Context) = send(context, ACTION_RESUME)
        fun stop(context: Context) = send(context, ACTION_STOP)

        private fun send(context: Context, action: String, foreground: Boolean = false) {
            val intent = Intent(context, CardioTrackingService::class.java).setAction(action)
            if (foreground) context.startForegroundService(intent) else context.startService(intent)
        }
    }
}
