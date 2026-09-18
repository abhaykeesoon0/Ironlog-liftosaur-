package com.ironlog.app.core.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ironlog.app.core.security.SecureTokenStore
import com.ironlog.app.data.dao.WorkoutDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Daily training reminder.
 *
 * WorkManager rather than AlarmManager: the reminder does not need to-the-minute
 * precision, and WorkManager survives reboots and respects Doze without the app
 * holding SCHEDULE_EXACT_ALARM, a permission Play now restricts to alarm clocks.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun schedule(hour: Int, minute: Int) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(hour, minute)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMillis()

        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()

        // Unique work, re-enqueued: changing the time replaces the schedule
        // instead of stacking a second daily notification.
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request)
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private companion object {
        const val WORK_NAME = "daily_training_reminder"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun workoutDao(): WorkoutDao
    fun tokenStore(): SecureTokenStore
    fun notificationHelper(): NotificationHelper
}

/**
 * Only nudges when the user has not trained today and is still signed in, so the
 * reminder never nags someone who has already been to the gym.
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication(applicationContext, ReminderEntryPoint::class.java)
        val userId = entry.tokenStore().userId() ?: return Result.success()

        val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val trainedToday = entry.workoutDao().observeWorkoutCountSince(userId, startOfDay).first() > 0
        if (!trainedToday) {
            entry.notificationHelper().workoutReminder(
                title = "Time to train",
                body = "Nothing logged yet today. Open IronLog to start a routine."
            )
        }
        return Result.success()
    }
}
