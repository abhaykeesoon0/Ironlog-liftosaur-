package com.ironlog.app.core.notifications

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.ironlog.app.data.repository.ProfileRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * A Quick Settings tile for the rest timer: one swipe down from anywhere,
 * one tap, and the clock is running -- without unlocking to the app in the
 * middle of a set.
 *
 * The tile mirrors the timer's state (active while it counts) and toggles
 * it: a tap starts a rest of the user's default length, a second tap ends
 * it early. It leans on the same service the workout screen uses, so the
 * notification, the buzz and the screen all agree.
 */
class RestTimerTile : TileService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun restTimer(): RestTimerController
        fun profile(): ProfileRepository
    }

    private val deps by lazy { EntryPointAccessors.fromApplication(applicationContext, Deps::class.java) }
    private var scope: CoroutineScope? = null
    private var watch: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope = it }
        watch = s.launch {
            deps.restTimer().isRunning.collect { running -> render(running) }
        }
    }

    override fun onStopListening() {
        watch?.cancel()
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val timer = deps.restTimer()
        if (timer.isRunning.value) {
            timer.skip()
            return
        }
        scope?.launch {
            val seconds = deps.profile().observeUser().first()?.restTimerDefaultSeconds ?: DEFAULT_SECONDS
            timer.start(seconds, "")
        }
    }

    private fun render(running: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Rest timer"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (running) "Counting down" else "Tap to start"
        }
        tile.updateTile()
    }

    private companion object {
        const val DEFAULT_SECONDS = 120
    }
}
