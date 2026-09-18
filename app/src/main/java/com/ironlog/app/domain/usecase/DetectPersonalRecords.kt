package com.ironlog.app.domain.usecase

import com.ironlog.app.data.dao.HistoricSet
import com.ironlog.app.data.dao.PersonalRecordDao
import com.ironlog.app.data.entity.PersonalRecordEntity
import com.ironlog.app.data.entity.RecordType
import com.ironlog.app.data.entity.WorkoutSetEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class DetectedRecord(
    val setId: String,
    val recordType: RecordType,
    val value: Double,
    val previousValue: Double?,
    val weightKg: Double?,
    val reps: Int?
) {
    val improvement: Double get() = value - (previousValue ?: 0.0)
    val isFirstEver: Boolean get() = previousValue == null
}

/**
 * Decides when a set counts as a personal record.
 *
 * Four record types are tracked because "a PR" means different things depending
 * on what the user is training for:
 *
 *  - **Heaviest weight** — the number most lifters actually care about.
 *  - **Best estimated 1RM** — catches genuine progress that heaviest-weight misses.
 *    Going from 100 kg x 3 to 100 kg x 6 is a large strength gain, but the top
 *    weight is unchanged, so without this the app would show nothing.
 *  - **Best set volume** (weight x reps) — the hypertrophy-relevant measure.
 *  - **Most reps** — the one that matters for bodyweight movements, where the
 *    load is fixed and reps are the only variable.
 *
 * Warm-up sets are excluded throughout: a warm-up single at a heavy weight is not
 * a record, and counting it would poison every subsequent comparison.
 */
@Singleton
class DetectPersonalRecords @Inject constructor(
    private val recordDao: PersonalRecordDao
) {

    /**
     * Compares one workout's sets against the stored records.
     *
     * Existing records are read once into a local map and then updated in memory
     * as the sets are walked, so that two PR-beating sets in the same session
     * resolve correctly — only the better of the two is recorded, and the second
     * set is measured against the first rather than against the stale stored value.
     */
    suspend fun detect(
        userId: String,
        exerciseId: String,
        sets: List<WorkoutSetEntity>
    ): List<DetectedRecord> {
        val eligible = sets
            .filter { it.isCompleted && it.type.countsTowardVolume }
            .filter { (it.reps ?: 0) > 0 }
        if (eligible.isEmpty()) return emptyList()

        val current = RecordType.entries
            .filter { it != RecordType.BEST_SESSION_VOLUME }
            .associateWith { type -> recordDao.find(userId, exerciseId, type.name)?.value }
            .toMutableMap()

        val detected = mutableListOf<DetectedRecord>()

        eligible.forEach { set ->
            val weight = set.weightKg ?: 0.0
            val reps = set.reps ?: 0

            val candidates = listOf(
                Triple(RecordType.HEAVIEST_WEIGHT, weight, weight > 0),
                Triple(
                    RecordType.BEST_ESTIMATED_1RM,
                    TrainingMath.estimatedOneRepMax(weight, reps),
                    weight > 0 && TrainingMath.isReliableEstimate(reps)
                ),
                Triple(RecordType.BEST_SET_VOLUME, weight * reps, weight > 0),
                Triple(RecordType.MOST_REPS, reps.toDouble(), true)
            )

            candidates.forEach { (type, value, applicable) ->
                if (!applicable || value <= 0.0) return@forEach
                val previous = current[type]
                // Strictly greater: repeating a previous best is not a new record.
                if (previous == null || value > previous + EPSILON) {
                    detected += DetectedRecord(
                        setId = set.id,
                        recordType = type,
                        value = value,
                        previousValue = previous,
                        weightKg = set.weightKg,
                        reps = set.reps
                    )
                    current[type] = value
                }
            }
        }

        // One set can beat several record types at once; the badge shows the most
        // meaningful, so later duplicates for the same type are dropped and the
        // best value per type survives.
        return detected
            .groupBy { it.recordType }
            .mapNotNull { (_, group) -> group.maxByOrNull { it.value } }
    }

    suspend fun persist(
        userId: String,
        exerciseId: String,
        workoutId: String,
        records: List<DetectedRecord>,
        achievedAt: Long
    ) {
        if (records.isEmpty()) return
        recordDao.upsertAll(
            records.map { record ->
                PersonalRecordEntity(
                    // Deterministic id keeps one row per (user, exercise, type),
                    // matching the unique index on the table.
                    id = recordId(userId, exerciseId, record.recordType),
                    userId = userId,
                    exerciseId = exerciseId,
                    recordType = record.recordType,
                    value = record.value,
                    weightKg = record.weightKg,
                    reps = record.reps,
                    workoutId = workoutId,
                    achievedAt = achievedAt
                )
            }
        )
    }

    /**
     * Session-level record. It is not stored in personal_records -- that table's
     * exerciseId is a foreign key, and a session belongs to no one exercise -- so
     * the previous best is read from the denormalised workout totals instead.
     */
    fun detectSessionVolume(volumeKg: Double, previousBestKg: Double?): DetectedRecord? {
        if (volumeKg <= 0) return null
        val previous = previousBestKg?.takeIf { it > 0 }
        if (previous != null && volumeKg <= previous + EPSILON) return null
        return DetectedRecord(
            setId = "",
            recordType = RecordType.BEST_SESSION_VOLUME,
            value = volumeKg,
            previousValue = previous,
            weightKg = null,
            reps = null
        )
    }

    private fun recordId(userId: String, exerciseId: String, type: RecordType): String =
        UUID.nameUUIDFromBytes("$userId|$exerciseId|${type.name}".toByteArray()).toString()

    /**
     * Rebuilds one exercise's records from its full history. Used after a workout
     * is deleted, so a record set in that session does not outlive the session.
     */
    suspend fun rebuild(userId: String, exerciseId: String, history: List<HistoricSet>) {
        recordDao.deleteForExercise(userId, exerciseId)
        history.groupBy { it.workoutId }
            .values
            .sortedBy { it.first().startedAt }
            .forEach { session ->
                val detected = detect(userId, exerciseId, session.map { it.set })
                persist(userId, exerciseId, session.first().workoutId, detected, session.first().startedAt)
            }
    }

    companion object {
        /** Guards against floating-point noise creating phantom records. */
        private const val EPSILON = 1e-6
    }
}
