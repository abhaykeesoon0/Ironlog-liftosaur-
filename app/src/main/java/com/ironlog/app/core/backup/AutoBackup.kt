package com.ironlog.app.core.backup

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ironlog.app.data.repository.BackupRepository
import com.ironlog.app.data.repository.NotSignedInException
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The weekly automatic backup.
 *
 * A training log is years of effort, and the one copy of it lives on a phone
 * that can be lost. The manual backup already exists; this runs the same
 * sealed export on a schedule so there is always a recent copy in the app's
 * private storage, made without the user having to remember.
 *
 * WorkManager for the same reasons as the reminder: the timing is loose, it
 * survives reboots, and it respects Doze. The work waits for a battery that
 * is not low, since a backup is the least urgent thing a dying phone could do.
 */
@Singleton
class AutoBackupScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val constraints = Constraints.Builder().setRequiresBatteryNotLow(true).build()

    /** Enqueues the weekly run. Idempotent: an existing schedule is kept, not doubled. */
    fun schedule() {
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS)
            .setConstraints(constraints)
            .setInitialDelay(7, TimeUnit.DAYS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** One backup as soon as the constraints allow, for "back up now" and for the moment the feature is turned on. */
    fun runNow() {
        val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(IMMEDIATE_WORK, ExistingWorkPolicy.KEEP, request)
    }

    fun cancel() {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(PERIODIC_WORK)
            cancelUniqueWork(IMMEDIATE_WORK)
        }
    }

    private companion object {
        const val PERIODIC_WORK = "weekly_auto_backup"
        const val IMMEDIATE_WORK = "auto_backup_now"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AutoBackupEntryPoint {
    fun backupRepository(): BackupRepository
}

/**
 * Writes one automatic backup. The repository owns the passphrase, the
 * destination and the record of the outcome; the worker only decides whether
 * a failure is worth retrying.
 */
class AutoBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = EntryPointAccessors.fromApplication(applicationContext, AutoBackupEntryPoint::class.java)
            .backupRepository()
        return repository.writeAutomaticBackup().fold(
            onSuccess = { Result.success() },
            onFailure = { e ->
                when {
                    // Nobody is signed in: nothing to back up, and nothing to retry.
                    e is NotSignedInException -> Result.success()
                    runAttemptCount < MAX_ATTEMPTS -> Result.retry()
                    else -> {
                        Log.e("IronLog", "Automatic backup gave up after $MAX_ATTEMPTS attempts", e)
                        Result.failure()
                    }
                }
            }
        )
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
