package com.ironlog.app.core.sensors

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import com.ironlog.app.data.entity.CardioType
import com.ironlog.app.data.entity.Meal
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes what the app records back to Health Connect.
 *
 * The app already reads steps from it. Writing sessions and meals back is the
 * other half of being a good citizen of the platform: a workout logged here
 * shows up in the user's health dashboard and in whatever else they run,
 * without this app having to know what that is. It is still an IPC call to
 * another app on the same phone -- nothing leaves the device -- so the
 * offline design holds.
 *
 * Writing needs its own permissions, asked for through Health Connect's own
 * flow alongside the read. Without them every write is a quiet skip rather
 * than a failure, because a user who declined to share is not an error, and
 * the log here is the one that matters.
 */
@Singleton
class HealthConnectWriter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    val permissions: Set<String> = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(DistanceRecord::class),
        HealthPermission.getWritePermission(NutritionRecord::class)
    )

    sealed interface Outcome {
        /** The record is in Health Connect. */
        data object Written : Outcome

        /** No provider, or no permission: nothing to do and nothing wrong. */
        data object Skipped : Outcome

        /** The write was attempted and refused, which is worth a line on screen. */
        data class Failed(val message: String) : Outcome
    }

    suspend fun writeStrength(name: String, startedAt: Long, finishedAt: Long): Outcome =
        write(ExerciseSessionRecord::class.java.name) { client ->
            val zone = ZoneId.systemDefault()
            client.insertRecords(
                listOf(
                    ExerciseSessionRecord(
                        startTime = Instant.ofEpochMilli(startedAt),
                        startZoneOffset = zone.rules.getOffset(Instant.ofEpochMilli(startedAt)),
                        endTime = Instant.ofEpochMilli(finishedAt),
                        endZoneOffset = zone.rules.getOffset(Instant.ofEpochMilli(finishedAt)),
                        exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
                        title = name,
                        metadata = Metadata()
                    )
                )
            )
        }

    suspend fun writeCardio(type: CardioType, startedAt: Long, finishedAt: Long, distanceMeters: Double): Outcome =
        write(ExerciseSessionRecord::class.java.name) { client ->
            val zone = ZoneId.systemDefault()
            val start = Instant.ofEpochMilli(startedAt)
            val end = Instant.ofEpochMilli(finishedAt)
            val records = mutableListOf<androidx.health.connect.client.records.Record>(
                ExerciseSessionRecord(
                    startTime = start,
                    startZoneOffset = zone.rules.getOffset(start),
                    endTime = end,
                    endZoneOffset = zone.rules.getOffset(end),
                    exerciseType = when (type) {
                        CardioType.RUN -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
                        CardioType.WALK -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING
                        CardioType.CYCLE -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING
                        CardioType.HIKE -> ExerciseSessionRecord.EXERCISE_TYPE_HIKING
                    },
                    title = "${type.label} with Liftosaur",
                    metadata = Metadata()
                )
            )
            if (distanceMeters > 0.0) {
                records += DistanceRecord(
                    startTime = start,
                    startZoneOffset = zone.rules.getOffset(start),
                    endTime = end,
                    endZoneOffset = zone.rules.getOffset(end),
                    distance = Length.meters(distanceMeters),
                    metadata = Metadata()
                )
            }
            client.insertRecords(records)
        }

    suspend fun writeNutrition(
        name: String,
        at: Long,
        kcal: Double,
        proteinG: Double,
        carbsG: Double,
        fatG: Double,
        meal: Meal
    ): Outcome = write(NutritionRecord::class.java.name) { client ->
        val zone = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(at)
        // A meal is logged as a moment; Health Connect wants a span, so it is
        // given one minute, which is how long it takes to eat a banana.
        val end = start.plusSeconds(60)
        client.insertRecords(
            listOf(
                NutritionRecord(
                    startTime = start,
                    startZoneOffset = zone.rules.getOffset(start),
                    endTime = end,
                    endZoneOffset = zone.rules.getOffset(end),
                    energy = Energy.kilocalories(kcal),
                    protein = Mass.grams(proteinG),
                    totalCarbohydrate = Mass.grams(carbsG),
                    totalFat = Mass.grams(fatG),
                    name = name,
                    mealType = when (meal) {
                        Meal.BREAKFAST -> MealType.MEAL_TYPE_BREAKFAST
                        Meal.LUNCH -> MealType.MEAL_TYPE_LUNCH
                        Meal.DINNER -> MealType.MEAL_TYPE_DINNER
                        Meal.SNACK -> MealType.MEAL_TYPE_SNACK
                    },
                    metadata = Metadata()
                )
            )
        )
    }

    /** Runs a write if there is a provider and the permission for it; otherwise skips. */
    private suspend fun write(recordType: String, block: suspend (HealthConnectClient) -> Unit): Outcome =
        withContext(Dispatchers.IO) {
            if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) return@withContext Outcome.Skipped
            val client = runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull() ?: return@withContext Outcome.Skipped
            val needed = permissions.firstOrNull { it.contains(recordType.substringAfterLast('.').removeSuffix("Record").uppercase()) }
            val granted = runCatching { client.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())
            if (needed != null && needed !in granted) return@withContext Outcome.Skipped
            runCatching { block(client) }
                .fold({ Outcome.Written }, { e -> Outcome.Failed(e.message ?: "Health Connect refused the record") })
        }
}
