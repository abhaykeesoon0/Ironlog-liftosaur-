package com.ironlog.app.core.notifications

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The rest countdown.
 *
 * Running this as a plain coroutine in a ViewModel looks simpler, but the phone
 * goes in a pocket between sets: the process gets frozen and the countdown stops.
 * A foreground service with an ongoing notification keeps it accurate and gives
 * the user "+30s" and "Skip" controls without unlocking the phone.
 *
 * The remaining time is derived from a wall-clock deadline rather than
 * decremented, so a few seconds of doze or scheduling delay cannot make the timer
 * drift — it recomputes against [System.currentTimeMillis] on every tick.
 */
@AndroidEntryPoint
class RestTimerService : Service() {

    @Inject lateinit var notifications: NotificationHelper

    private var scope: CoroutineScope? = null
    private var tickJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val seconds = intent.getIntExtra(EXTRA_SECONDS, 120)
                val name = intent.getStringExtra(EXTRA_EXERCISE_NAME).orEmpty()
                start(seconds, name)
            }
            ACTION_ADD_TIME -> addSeconds(30)
            ACTION_STOP -> stopTimer(finished = false)
        }
        return START_NOT_STICKY
    }

    private fun start(seconds: Int, exerciseName: String) {
        exerciseLabel = exerciseName
        deadlineMillis = System.currentTimeMillis() + seconds * 1000L
        _remainingSeconds.value = seconds
        _isRunning.value = true

        startForeground(
            NotificationHelper.ID_REST_RUNNING,
            notifications.restTimerRunning(seconds, exerciseName)
        )

        tickJob?.cancel()
        tickJob = scope?.launch {
            while (isActive) {
                val remaining = remainingFromDeadline()
                _remainingSeconds.value = remaining
                if (remaining <= 0) {
                    notifications.restTimerFinished(exerciseLabel)
                    stopTimer(finished = true)
                    return@launch
                }
                notifications.restTimerRunning(remaining, exerciseLabel).let {
                    // Refreshing the same id updates the countdown in place.
                    startForeground(NotificationHelper.ID_REST_RUNNING, it)
                }
                delay(1000)
            }
        }
    }

    private fun addSeconds(extra: Int) {
        if (!_isRunning.value) return
        deadlineMillis += extra * 1000L
        _remainingSeconds.value = remainingFromDeadline()
    }

    private fun stopTimer(finished: Boolean) {
        tickJob?.cancel()
        tickJob = null
        _isRunning.value = false
        _remainingSeconds.value = 0
        _lastFinishedAt.value = if (finished) System.currentTimeMillis() else 0L
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun remainingFromDeadline(): Int =
        ((deadlineMillis - System.currentTimeMillis()) / 1000.0).toInt().coerceAtLeast(0)

    override fun onDestroy() {
        tickJob?.cancel()
        scope?.cancel()
        scope = null
        _isRunning.value = false
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.ironlog.app.REST_START"
        const val ACTION_STOP = "com.ironlog.app.REST_STOP"
        const val ACTION_ADD_TIME = "com.ironlog.app.REST_ADD_TIME"
        const val EXTRA_SECONDS = "seconds"
        const val EXTRA_EXERCISE_NAME = "exercise_name"

        private var deadlineMillis: Long = 0L
        private var exerciseLabel: String = ""

        private val _remainingSeconds = MutableStateFlow(0)
        val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _lastFinishedAt = MutableStateFlow(0L)
        val lastFinishedAt: StateFlow<Long> = _lastFinishedAt.asStateFlow()

        fun start(context: Context, seconds: Int, exerciseName: String) {
            val intent = Intent(context, RestTimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SECONDS, seconds)
                putExtra(EXTRA_EXERCISE_NAME, exerciseName)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, RestTimerService::class.java).setAction(ACTION_STOP)
            )
        }

        fun addTime(context: Context) {
            context.startService(
                Intent(context, RestTimerService::class.java).setAction(ACTION_ADD_TIME)
            )
        }
    }
}

/** Routes the notification's "+30s" and "Skip" buttons back into the service. */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SKIP_REST -> RestTimerService.stop(context)
            ACTION_ADD_REST_TIME -> RestTimerService.addTime(context)
        }
    }

    companion object {
        const val ACTION_SKIP_REST = "com.ironlog.app.SKIP_REST"
        const val ACTION_ADD_REST_TIME = "com.ironlog.app.ADD_REST_TIME"
    }
}
