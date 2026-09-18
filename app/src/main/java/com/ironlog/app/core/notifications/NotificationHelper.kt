package com.ironlog.app.core.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ironlog.app.MainActivity
import com.ironlog.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * All notification construction lives here so channel ids, importance levels and
 * pending-intent flags cannot drift apart across the codebase.
 *
 * Three channels rather than one, because they want different treatment: the rest
 * timer must interrupt (you are standing under a bar waiting for it), reminders
 * should not, and the cardio tracker is a silent ongoing status.
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val manager = NotificationManagerCompat.from(context)

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val restTimer = NotificationChannel(
            CHANNEL_REST_TIMER,
            context.getString(R.string.channel_rest_timer),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_rest_timer_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 260, 140, 260)
            setShowBadge(false)
        }

        val reminders = NotificationChannel(
            CHANNEL_REMINDERS,
            context.getString(R.string.channel_reminders),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_reminders_desc)
        }

        val cardio = NotificationChannel(
            CHANNEL_CARDIO,
            context.getString(R.string.channel_cardio),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_cardio_desc)
            setShowBadge(false)
        }

        manager.createNotificationChannels(listOf(restTimer, reminders, cardio))
    }

    private fun openAppIntent(route: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            route?.let { putExtra(MainActivity.EXTRA_DEEP_LINK_ROUTE, it) }
        }
        return PendingIntent.getActivity(
            context,
            route.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** The ongoing countdown shown while resting between sets. */
    fun restTimerRunning(secondsRemaining: Int, exerciseName: String): Notification {
        val skip = PendingIntent.getBroadcast(
            context,
            REQUEST_SKIP,
            Intent(context, NotificationActionReceiver::class.java)
                .setAction(NotificationActionReceiver.ACTION_SKIP_REST),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val addTime = PendingIntent.getBroadcast(
            context,
            REQUEST_ADD_TIME,
            Intent(context, NotificationActionReceiver::class.java)
                .setAction(NotificationActionReceiver.ACTION_ADD_REST_TIME),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_REST_TIMER)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(formatClock(secondsRemaining))
            .setContentText("Resting — next up $exerciseName")
            .setContentIntent(openAppIntent(ROUTE_ACTIVE_WORKOUT))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "+30s", addTime)
            .addAction(0, "Skip", skip)
            .build()
    }

    /** Fired once when the rest interval reaches zero. */
    fun restTimerFinished(exerciseName: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_REST_TIMER)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Rest over")
            .setContentText("Next set: $exerciseName")
            .setContentIntent(openAppIntent(ROUTE_ACTIVE_WORKOUT))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 260, 140, 260))
            .build()
        notify(ID_REST_FINISHED, notification)
    }

    fun workoutReminder(title: String, body: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openAppIntent(ROUTE_ROUTINES))
            .setAutoCancel(true)
            .build()
        notify(ID_REMINDER, notification)
    }

    fun cardioTracking(distanceMeters: Double, elapsedSeconds: Int, paceLabel: String): Notification {
        val km = distanceMeters / 1000.0
        return NotificationCompat.Builder(context, CHANNEL_CARDIO)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(String.format("%.2f km · %s", km, formatClock(elapsedSeconds)))
            .setContentText("Average pace $paceLabel")
            .setContentIntent(openAppIntent(ROUTE_CARDIO))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .build()
    }

    /**
     * POST_NOTIFICATIONS may have been revoked between the permission check and
     * here, so the framework call is guarded rather than assumed to succeed.
     */
    private fun notify(id: Int, notification: Notification) {
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // Permission revoked while the app was running; nothing to show.
        }
    }

    fun cancel(id: Int) = manager.cancel(id)

    companion object {
        const val CHANNEL_REST_TIMER = "rest_timer"
        const val CHANNEL_REMINDERS = "reminders"
        const val CHANNEL_CARDIO = "cardio"

        const val ID_REST_RUNNING = 1001
        const val ID_REST_FINISHED = 1002
        const val ID_REMINDER = 1003
        const val ID_CARDIO = 1004

        private const val REQUEST_SKIP = 2001
        private const val REQUEST_ADD_TIME = 2002

        const val ROUTE_ACTIVE_WORKOUT = "workout/active"
        const val ROUTE_ROUTINES = "routines"
        const val ROUTE_CARDIO = "cardio/track"

        fun formatClock(totalSeconds: Int): String {
            val safe = totalSeconds.coerceAtLeast(0)
            return "%d:%02d".format(safe / 60, safe % 60)
        }
    }
}
