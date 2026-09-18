package com.ironlog.app.core.notifications

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ViewModel-facing handle on [RestTimerService]. ViewModels must not hold an
 * Activity context, and starting a service needs one, so the application context
 * is injected here instead.
 */
@Singleton
class RestTimerController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val remainingSeconds: StateFlow<Int> = RestTimerService.remainingSeconds
    val isRunning: StateFlow<Boolean> = RestTimerService.isRunning

    /**
     * Starting a foreground service can be refused (e.g. the OS decides the app
     * is in the background); the failure is returned rather than crashing the
     * logger, and the caller shows it.
     */
    fun start(seconds: Int, nextExerciseName: String): Result<Unit> = runCatching {
        RestTimerService.start(context, seconds, nextExerciseName)
    }

    fun addTime(): Result<Unit> = runCatching { RestTimerService.addTime(context) }

    fun skip(): Result<Unit> = runCatching { RestTimerService.stop(context) }
}
