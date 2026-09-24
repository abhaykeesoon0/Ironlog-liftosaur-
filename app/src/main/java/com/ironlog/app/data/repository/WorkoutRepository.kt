package com.ironlog.app.data.repository

import androidx.room.withTransaction
import com.ironlog.app.core.database.AppDatabase
import com.ironlog.app.core.security.Validators
import com.ironlog.app.data.dao.ExerciseDao
import com.ironlog.app.data.dao.MuscleVolume
import com.ironlog.app.data.dao.RepCount
import com.ironlog.app.data.dao.TrainingTotals
import com.ironlog.app.data.dao.PersonalRecordDao
import com.ironlog.app.data.dao.RecordWithExercise
import com.ironlog.app.data.dao.RoutineDao
import com.ironlog.app.data.dao.UserDao
import com.ironlog.app.data.dao.VolumePoint
import com.ironlog.app.data.dao.WorkoutDao
import com.ironlog.app.data.dao.WorkoutWithDetail
import com.ironlog.app.data.entity.RecordType
import com.ironlog.app.data.entity.SetType
import com.ironlog.app.data.entity.WorkoutEntity
import com.ironlog.app.data.entity.WorkoutExerciseEntity
import com.ironlog.app.data.entity.WorkoutSetEntity
import com.ironlog.app.domain.usecase.DetectPersonalRecords
import com.ironlog.app.domain.usecase.DetectedRecord
import com.ironlog.app.domain.usecase.TrainingMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** What the logger needs to know after a set is ticked off. */
data class SetCompletion(
    val isCompleted: Boolean,
    val restSeconds: Int,
    val exerciseName: String,
    /** Non-null when this set beat a stored record. */
    val record: RecordType?
)

data class SummaryRecord(
    val exerciseName: String,
    val type: RecordType,
    val value: Double,
    val previousValue: Double?,
    val weightKg: Double?,
    val reps: Int?
)

data class WorkoutSummary(
    val workoutId: String,
    val name: String,
    val durationSeconds: Int,
    val volumeKg: Double,
    val sets: Int,
    val reps: Int,
    val records: List<SummaryRecord>,
    val isSessionVolumeRecord: Boolean
)

/**
 * Record types in order of how much a lifter cares; a set that beats several
 * records at once is badged with the first match.
 */
private val RECORD_PRIORITY = listOf(
    RecordType.HEAVIEST_WEIGHT,
    RecordType.BEST_ESTIMATED_1RM,
    RecordType.BEST_SET_VOLUME,
    RecordType.MOST_REPS
)

interface WorkoutRepository {
    fun observeActive(): Flow<WorkoutWithDetail?>
    fun observeWorkout(id: String): Flow<WorkoutWithDetail?>
    fun observeHistory(limit: Int = 200): Flow<List<WorkoutWithDetail>>
    fun observeWorkoutDates(): Flow<List<Long>>
    fun observeWorkoutCountSince(since: Long): Flow<Int>
    fun observeVolumeSince(since: Long): Flow<Double>
    fun observeDailyVolume(since: Long): Flow<List<VolumePoint>>
    fun observeMuscleSplit(since: Long): Flow<List<MuscleVolume>>
    fun observeRepCounts(since: Long): Flow<List<RepCount>>
    fun observeTotals(from: Long, to: Long): Flow<TrainingTotals>
    fun observeRecentRecords(limit: Int = 30): Flow<List<RecordWithExercise>>
    fun observeRecordCount(): Flow<Int>

    /** Starts a session, or returns the one already running. */
    suspend fun start(routineId: String?): Result<String>
    suspend fun rename(workoutId: String, name: String): Result<Unit>
    suspend fun updateNotes(workoutId: String, notes: String): Result<Unit>
    suspend fun addExercises(workoutId: String, exerciseIds: List<String>): Result<Unit>
    suspend fun removeExercise(workoutExerciseId: String): Result<Unit>
    suspend fun updateExerciseNotes(workoutExerciseId: String, notes: String): Result<Unit>
    suspend fun updateRest(workoutExerciseId: String, restSeconds: Int): Result<Unit>
    suspend fun addSet(workoutExerciseId: String): Result<Unit>
    suspend fun updateSet(set: WorkoutSetEntity): Result<Unit>
    suspend fun deleteSet(setId: String): Result<Unit>
    /** Puts a deleted set back, exactly as it was, and renumbers the rows around it. */
    suspend fun restoreSet(set: WorkoutSetEntity): Result<Unit>

    /**
     * Toggles a set. When completing a set with empty fields, [fallbackWeightKg]
     * and [fallbackReps] -- the greyed-out previous values the user was looking
     * at -- are filled in, matching what a tap on the tick means in practice.
     */
    suspend fun toggleComplete(setId: String, fallbackWeightKg: Double?, fallbackReps: Int?): Result<SetCompletion>
    suspend fun previousSets(exerciseId: String, excludeWorkoutId: String): Result<List<WorkoutSetEntity>>
    suspend fun finishActive(): Result<WorkoutSummary>
    suspend fun discardActive(): Result<Unit>
    suspend fun delete(workoutId: String): Result<Unit>
    suspend fun allFinished(): Result<List<WorkoutWithDetail>>
}

@Singleton
class WorkoutRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val workoutDao: WorkoutDao,
    private val routineDao: RoutineDao,
    private val exerciseDao: ExerciseDao,
    private val userDao: UserDao,
    private val recordDao: PersonalRecordDao,
    private val records: DetectPersonalRecords,
    private val currentUser: CurrentUser
) : WorkoutRepository {

    // ------------------------------------------------------------------ observe

    override fun observeActive(): Flow<WorkoutWithDetail?> =
        currentUser.idOrNull()?.let { workoutDao.observeActiveWorkout(it) } ?: flowOf(null)

    override fun observeWorkout(id: String): Flow<WorkoutWithDetail?> = workoutDao.observeWorkout(id)

    override fun observeHistory(limit: Int): Flow<List<WorkoutWithDetail>> =
        currentUser.idOrNull()?.let { workoutDao.observeHistory(it, limit, 0) } ?: flowOf(emptyList())

    override fun observeWorkoutDates(): Flow<List<Long>> =
        currentUser.idOrNull()?.let { workoutDao.observeWorkoutDates(it) } ?: flowOf(emptyList())

    override fun observeWorkoutCountSince(since: Long): Flow<Int> =
        currentUser.idOrNull()?.let { workoutDao.observeWorkoutCountSince(it, since) } ?: flowOf(0)

    override fun observeVolumeSince(since: Long): Flow<Double> =
        currentUser.idOrNull()?.let { workoutDao.observeVolumeSince(it, since) } ?: flowOf(0.0)

    override fun observeDailyVolume(since: Long): Flow<List<VolumePoint>> =
        currentUser.idOrNull()?.let {
            // Bucket by *local* calendar day (bucket = local epoch day): a 23:30
            // session belongs to that evening, not to the next UTC day.
            val offset = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong()
            workoutDao.observeDailyVolume(it, since, offset)
        } ?: flowOf(emptyList())

    override fun observeMuscleSplit(since: Long): Flow<List<MuscleVolume>> =
        currentUser.idOrNull()?.let { workoutDao.observeMuscleSplit(it, since) } ?: flowOf(emptyList())

    override fun observeRepCounts(since: Long): Flow<List<RepCount>> =
        currentUser.idOrNull()?.let { workoutDao.observeRepCounts(it, since) } ?: flowOf(emptyList())

    override fun observeTotals(from: Long, to: Long): Flow<TrainingTotals> =
        currentUser.idOrNull()?.let { workoutDao.observeTotals(it, from, to) } ?: flowOf(TrainingTotals.NONE)

    override fun observeRecentRecords(limit: Int): Flow<List<RecordWithExercise>> =
        currentUser.idOrNull()?.let { recordDao.observeRecentWithNames(it, limit) } ?: flowOf(emptyList())

    override fun observeRecordCount(): Flow<Int> =
        currentUser.idOrNull()?.let { recordDao.observeCount(it) } ?: flowOf(0)

    // ------------------------------------------------------------------ session

    override suspend fun start(routineId: String?): Result<String> = io {
        val userId = currentUser.require()
        workoutDao.getActiveWorkout(userId)?.let { return@io it.workout.id }

        userDao.findById(userId) ?: throw NotSignedInException()
        val routine = routineId?.let { routineDao.getRoutine(it) ?: throw UserFacingException("Routine not found") }
        val now = System.currentTimeMillis()

        val workout = WorkoutEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            routineId = routine?.routine?.id,
            name = routine?.routine?.name ?: defaultName(),
            startedAt = now,
            isActive = true
        )

        db.withTransaction {
            workoutDao.upsertWorkout(workout)
            routine?.exercises
                ?.sortedBy { it.routineExercise.orderIndex }
                ?.forEachIndexed { index, item ->
                    val workoutExercise = WorkoutExerciseEntity(
                        id = UUID.randomUUID().toString(),
                        workoutId = workout.id,
                        exerciseId = item.exercise.id,
                        orderIndex = index,
                        restSeconds = item.routineExercise.restSeconds,
                        supersetGroup = item.routineExercise.supersetGroup,
                        notes = item.routineExercise.notes
                    )
                    workoutDao.upsertWorkoutExercise(workoutExercise)
                    workoutDao.upsertSets(
                        (1..item.routineExercise.targetSets).map { n ->
                            WorkoutSetEntity(
                                id = UUID.randomUUID().toString(),
                                workoutExerciseId = workoutExercise.id,
                                setNumber = n
                            )
                        }
                    )
                }
        }
        workout.id
    }

    override suspend fun rename(workoutId: String, name: String): Result<Unit> = io {
        val workout = ownedWorkout(workoutId)
        val clean = Validators.sanitiseSingleLine(name)
        if (clean.isBlank()) throw UserFacingException("Give the workout a name")
        workoutDao.upsertWorkout(workout.copy(name = clean))
    }

    override suspend fun updateNotes(workoutId: String, notes: String): Result<Unit> = io {
        val workout = ownedWorkout(workoutId)
        workoutDao.upsertWorkout(workout.copy(notes = Validators.sanitiseText(notes)))
    }

    override suspend fun addExercises(workoutId: String, exerciseIds: List<String>): Result<Unit> = io {
        val userId = currentUser.require()
        val detail = workoutDao.getWorkout(workoutId) ?: throw UserFacingException("Workout not found")
        if (detail.workout.userId != userId) throw UserFacingException("Workout not found")
        val rest = userDao.findById(userId)?.restTimerDefaultSeconds ?: 120
        val startIndex = (detail.exercises.maxOfOrNull { it.workoutExercise.orderIndex } ?: -1) + 1

        db.withTransaction {
            exerciseIds.forEachIndexed { offset, exerciseId ->
                exerciseDao.findById(exerciseId) ?: throw UserFacingException("Exercise not found")
                val item = WorkoutExerciseEntity(
                    id = UUID.randomUUID().toString(),
                    workoutId = workoutId,
                    exerciseId = exerciseId,
                    orderIndex = startIndex + offset,
                    restSeconds = rest
                )
                workoutDao.upsertWorkoutExercise(item)
                // Pre-create as many sets as last time, so the previous-performance
                // column lines up row for row. Three when there is no history.
                val previousCount = latestSession(userId, exerciseId, workoutId).size
                val setCount = if (previousCount == 0) 3 else previousCount.coerceAtMost(10)
                workoutDao.upsertSets(
                    (1..setCount).map { n ->
                        WorkoutSetEntity(
                            id = UUID.randomUUID().toString(),
                            workoutExerciseId = item.id,
                            setNumber = n
                        )
                    }
                )
            }
        }
    }

    override suspend fun removeExercise(workoutExerciseId: String): Result<Unit> = io {
        ownedWorkoutExercise(workoutExerciseId)
        workoutDao.deleteWorkoutExerciseById(workoutExerciseId)
    }

    override suspend fun updateExerciseNotes(workoutExerciseId: String, notes: String): Result<Unit> = io {
        val item = ownedWorkoutExercise(workoutExerciseId)
        workoutDao.upsertWorkoutExercise(item.copy(notes = Validators.sanitiseText(notes, 200)))
    }

    override suspend fun updateRest(workoutExerciseId: String, restSeconds: Int): Result<Unit> = io {
        val item = ownedWorkoutExercise(workoutExerciseId)
        workoutDao.upsertWorkoutExercise(item.copy(restSeconds = restSeconds.coerceIn(0, 900)))
    }

    override suspend fun addSet(workoutExerciseId: String): Result<Unit> = io {
        ownedWorkoutExercise(workoutExerciseId)
        val sets = workoutDao.setsFor(workoutExerciseId)
        if (sets.size >= MAX_SETS) throw UserFacingException("That's the maximum of $MAX_SETS sets")
        val last = sets.maxByOrNull { it.setNumber }
        workoutDao.upsertSet(
            WorkoutSetEntity(
                id = UUID.randomUUID().toString(),
                workoutExerciseId = workoutExerciseId,
                setNumber = (last?.setNumber ?: 0) + 1,
                // Carrying the last set's load forward saves retyping it on every
                // straight set, which is the common case.
                weightKg = last?.weightKg,
                type = if (last?.type == SetType.WARMUP) SetType.NORMAL else (last?.type ?: SetType.NORMAL)
            )
        )
    }

    override suspend fun updateSet(set: WorkoutSetEntity): Result<Unit> = io {
        val existing = workoutDao.findSet(set.id) ?: throw UserFacingException("Set not found")
        ownedWorkoutExercise(existing.workoutExerciseId)
        validateSet(set)
        workoutDao.upsertSet(set.copy(workoutExerciseId = existing.workoutExerciseId))
        if (existing.isCompleted) refreshLiveRecords(existing.workoutExerciseId)
    }

    override suspend fun restoreSet(set: WorkoutSetEntity): Result<Unit> = io {
        ownedWorkoutExercise(set.workoutExerciseId)
        db.withTransaction {
            workoutDao.upsertSet(set)
            // Renumber by the numbers the rows carry, so the restored set lands
            // where it was and the rest read 1, 2, 3 again.
            workoutDao.setsFor(set.workoutExerciseId)
                .sortedWith(compareBy({ it.setNumber }, { it.id != set.id }))
                .forEachIndexed { index, row ->
                    if (row.setNumber != index + 1) workoutDao.upsertSet(row.copy(setNumber = index + 1))
                }
            if (set.isCompleted) refreshLiveRecords(set.workoutExerciseId)
        }
    }

    override suspend fun deleteSet(setId: String): Result<Unit> = io {
        val existing = workoutDao.findSet(setId) ?: throw UserFacingException("Set not found")
        ownedWorkoutExercise(existing.workoutExerciseId)
        db.withTransaction {
            workoutDao.deleteSetById(setId)
            // Renumber so the rows read 1, 2, 3 rather than 1, 3, 4.
            workoutDao.setsFor(existing.workoutExerciseId)
                .sortedBy { it.setNumber }
                .mapIndexed { index, set -> set.copy(setNumber = index + 1) }
                .let { workoutDao.upsertSets(it) }
        }
        refreshLiveRecords(existing.workoutExerciseId)
    }

    override suspend fun toggleComplete(
        setId: String,
        fallbackWeightKg: Double?,
        fallbackReps: Int?
    ): Result<SetCompletion> = io {
        val set = workoutDao.findSet(setId) ?: throw UserFacingException("Set not found")
        val item = ownedWorkoutExercise(set.workoutExerciseId)
        val exercise = exerciseDao.findById(item.exerciseId) ?: throw UserFacingException("Exercise not found")

        if (set.isCompleted) {
            workoutDao.upsertSet(set.copy(isCompleted = false, completedAt = null, prType = null))
            refreshLiveRecords(item.id)
            return@io SetCompletion(false, item.restSeconds, exercise.name, null)
        }

        val category = exercise.category
        val completed = set.copy(
            weightKg = set.weightKg ?: fallbackWeightKg.takeIf { category.tracksWeight },
            reps = set.reps ?: fallbackReps.takeIf { category.tracksReps },
            isCompleted = true,
            completedAt = System.currentTimeMillis()
        )
        if (category.tracksReps && (completed.reps ?: 0) <= 0) {
            throw UserFacingException("Enter the reps for this set first")
        }
        if (category.tracksWeight && completed.weightKg == null) {
            throw UserFacingException("Enter the weight for this set first")
        }
        if (!category.tracksReps && completed.durationSeconds == null && completed.distanceMeters == null) {
            throw UserFacingException("Enter a time or distance first")
        }
        validateSet(completed)
        workoutDao.upsertSet(completed)

        val record = refreshLiveRecords(item.id)[completed.id]
        SetCompletion(true, item.restSeconds, exercise.name, record)
    }

    override suspend fun previousSets(exerciseId: String, excludeWorkoutId: String): Result<List<WorkoutSetEntity>> =
        io { latestSession(currentUser.require(), exerciseId, excludeWorkoutId) }

    override suspend fun finishActive(): Result<WorkoutSummary> = io {
        val userId = currentUser.require()
        val now = System.currentTimeMillis()

        db.withTransaction {
            val detail = workoutDao.getActiveWorkout(userId)
                ?: throw UserFacingException("There's no workout in progress")

            // Unticked sets are plans, not work; they are dropped rather than
            // stored as zeros that would drag every average down.
            detail.exercises.forEach { item ->
                item.sets.filterNot { it.isCompleted }.forEach { workoutDao.deleteSetById(it.id) }
                if (item.sets.none { it.isCompleted }) {
                    workoutDao.deleteWorkoutExerciseById(item.workoutExercise.id)
                }
            }
            val kept = detail.exercises
                .map { it to it.sets.filter { set -> set.isCompleted }.sortedBy { set -> set.setNumber } }
                .filter { (_, sets) -> sets.isNotEmpty() }
            if (kept.isEmpty()) {
                throw UserFacingException("Complete at least one set, or discard the workout")
            }

            val summaryRecords = mutableListOf<SummaryRecord>()
            val prSetIds = mutableMapOf<String, RecordType>()

            // Grouped by movement: the same exercise added twice is still one lift.
            kept.groupBy { (item, _) -> item.exercise.id }.forEach { (exerciseId, group) ->
                val sets = group.flatMap { it.second }
                val detected = records.detect(userId, exerciseId, sets)
                records.persist(userId, exerciseId, detail.workout.id, detected, now)
                badge(detected).forEach { (setId, type) -> prSetIds[setId] = type }
                detected.forEach {
                    summaryRecords += SummaryRecord(
                        exerciseName = group.first().first.exercise.name,
                        type = it.recordType,
                        value = it.value,
                        previousValue = it.previousValue,
                        weightKg = it.weightKg,
                        reps = it.reps
                    )
                }
            }

            // Renumber and write the final badge state in one pass.
            kept.forEach { (_, sets) ->
                workoutDao.upsertSets(
                    sets.mapIndexed { index, set ->
                        set.copy(setNumber = index + 1, prType = prSetIds[set.id])
                    }
                )
            }

            val allSets = kept.flatMap { it.second }
            val volume = TrainingMath.totalVolume(allSets)
            val sessionRecord = records.detectSessionVolume(
                volume,
                workoutDao.bestSessionVolume(userId, detail.workout.id)
            )
            val duration = ((now - detail.workout.startedAt) / 1000).toInt().coerceAtLeast(0)

            workoutDao.upsertWorkout(
                detail.workout.copy(
                    finishedAt = now,
                    isActive = false,
                    totalVolumeKg = volume,
                    totalSets = TrainingMath.workingSetCount(allSets),
                    totalReps = TrainingMath.totalReps(allSets),
                    durationSeconds = duration,
                    prCount = summaryRecords.size + if (sessionRecord != null) 1 else 0
                )
            )
            detail.workout.routineId?.let { routineDao.markPerformed(it, now) }

            WorkoutSummary(
                workoutId = detail.workout.id,
                name = detail.workout.name,
                durationSeconds = duration,
                volumeKg = volume,
                sets = TrainingMath.workingSetCount(allSets),
                reps = TrainingMath.totalReps(allSets),
                records = summaryRecords,
                isSessionVolumeRecord = sessionRecord != null && !sessionRecord.isFirstEver
            )
        }
    }

    override suspend fun discardActive(): Result<Unit> = io {
        val userId = currentUser.require()
        val active = workoutDao.getActiveWorkout(userId) ?: return@io
        workoutDao.deleteWorkout(active.workout.id)
    }

    /**
     * Deleting a session also rebuilds the records of every exercise in it, so a
     * PR set in the deleted session does not live on in the records list.
     */
    override suspend fun delete(workoutId: String): Result<Unit> = io {
        val userId = currentUser.require()
        val detail = workoutDao.getWorkout(workoutId) ?: throw UserFacingException("Workout not found")
        if (detail.workout.userId != userId) throw UserFacingException("Workout not found")
        db.withTransaction {
            workoutDao.deleteWorkout(workoutId)
            detail.exercises.map { it.exercise.id }.distinct().forEach { exerciseId ->
                records.rebuild(userId, exerciseId, workoutDao.completedSetsFor(userId, exerciseId))
            }
        }
    }

    override suspend fun allFinished(): Result<List<WorkoutWithDetail>> =
        io { workoutDao.allFinished(currentUser.require()) }

    // ------------------------------------------------------------------ helpers

    /**
     * Re-runs record detection over one exercise's completed sets in the live
     * session and writes the badges. Nothing is persisted to the records table
     * until the workout is finished, so un-ticking a set cannot leave a phantom PR.
     */
    private suspend fun refreshLiveRecords(workoutExerciseId: String): Map<String, RecordType> {
        val userId = currentUser.require()
        val item = workoutDao.findWorkoutExercise(workoutExerciseId) ?: return emptyMap()
        val sets = workoutDao.setsFor(workoutExerciseId)
        val badges = badge(records.detect(userId, item.exerciseId, sets))
        val changed = sets.filter { it.prType != badges[it.id] }.map { it.copy(prType = badges[it.id]) }
        if (changed.isNotEmpty()) workoutDao.upsertSets(changed)
        return badges
    }

    /** Maps each record-setting set to the single most meaningful record it set. */
    private fun badge(detected: List<DetectedRecord>): Map<String, RecordType> =
        detected.groupBy { it.setId }.mapValues { (_, list) ->
            RECORD_PRIORITY.first { type -> list.any { it.recordType == type } }
        }

    private suspend fun latestSession(userId: String, exerciseId: String, excludeWorkoutId: String): List<WorkoutSetEntity> {
        val sets = workoutDao.previousSetsFor(userId, exerciseId, excludeWorkoutId)
        val latest = sets.firstOrNull()?.workoutExerciseId ?: return emptyList()
        return sets.filter { it.workoutExerciseId == latest }.sortedBy { it.setNumber }
    }

    private suspend fun ownedWorkout(workoutId: String): WorkoutEntity {
        val userId = currentUser.require()
        val workout = workoutDao.getWorkout(workoutId)?.workout ?: throw UserFacingException("Workout not found")
        if (workout.userId != userId) throw UserFacingException("Workout not found")
        return workout
    }

    private suspend fun ownedWorkoutExercise(id: String): WorkoutExerciseEntity {
        val item = workoutDao.findWorkoutExercise(id) ?: throw UserFacingException("Exercise not found in this workout")
        ownedWorkout(item.workoutId)
        return item
    }

    private fun validateSet(set: WorkoutSetEntity) {
        set.weightKg?.let {
            if (!it.isFinite() || it < 0 || it > Validators.MAX_WEIGHT_KG) throw UserFacingException("Check the weight")
        }
        set.reps?.let { if (it !in 0..Validators.MAX_REPS) throw UserFacingException("Check the reps") }
        set.rpe?.let { if (it !in 6.0..10.0) throw UserFacingException("RPE must be between 6 and 10") }
        set.durationSeconds?.let { if (it !in 0..86_400) throw UserFacingException("Check the time") }
        set.distanceMeters?.let { if (!it.isFinite() || it < 0 || it > 500_000) throw UserFacingException("Check the distance") }
    }

    private fun defaultName(): String = when (LocalTime.now().hour) {
        in 5..11 -> "Morning workout"
        in 12..16 -> "Afternoon workout"
        in 17..21 -> "Evening workout"
        else -> "Night workout"
    }

    private suspend fun <T> io(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private companion object {
        const val MAX_SETS = 20
    }
}
