package com.ironlog.app.core.sensors

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Steps from Health Connect, which is how a watch's step count reaches this app.
 *
 * A phone app cannot read a Samsung, Fitbit or Garmin watch over Bluetooth
 * directly: those are proprietary protocols spoken only by the vendor's own
 * app. What every one of those apps does do is write steps into Health
 * Connect, Android's on-device store for health data. Reading from there gets
 * the watch's count without a network call, a vendor SDK or an account, so the
 * app stays offline by design.
 *
 * Health Connect is also the source of truth for de-duplication: a phone in a
 * pocket and a watch on the wrist both counting the same walk is its problem to
 * reconcile, not ours, and it does so across every app writing to it.
 */
@Singleton
class HealthConnectSteps @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** Permission to read steps, requested through Health Connect's own flow. */
    val permissions: Set<String> = setOf(HealthPermission.getReadPermission(StepsRecord::class))

    enum class Availability {
        /** Installed and usable. */
        AVAILABLE,

        /** Supported by this Android version, but the provider needs installing. */
        NEEDS_INSTALL,

        /** Too old an Android version, or no provider on this device at all. */
        UNSUPPORTED
    }

    fun availability(): Availability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> Availability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Availability.NEEDS_INSTALL
        else -> Availability.UNSUPPORTED
    }

    private fun clientOrNull(): HealthConnectClient? =
        if (availability() == Availability.AVAILABLE) {
            runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
        } else {
            null
        }

    /** The contract the screen launches to ask for the permission. */
    fun permissionContract() = PermissionController.createRequestPermissionResultContract()

    suspend fun hasPermission(): Boolean = withContext(Dispatchers.IO) {
        val client = clientOrNull() ?: return@withContext false
        runCatching {
            client.permissionController.getGrantedPermissions().containsAll(permissions)
        }.getOrDefault(false)
    }

    /**
     * Today's steps as Health Connect totals them, or null when it cannot say.
     *
     * Null rather than zero: a missing reading and a day with no steps are
     * different facts, and showing zero for "not connected" would be a lie the
     * user cannot tell from a rest day.
     */
    suspend fun stepsToday(zone: ZoneId = ZoneId.systemDefault()): Long? = withContext(Dispatchers.IO) {
        val client = clientOrNull() ?: return@withContext null
        val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val now = java.time.Instant.now()

        runCatching {
            val result: AggregationResult = client.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, now)
                )
            )
            result[StepsRecord.COUNT_TOTAL] ?: 0L
        }.getOrNull()
    }
}
