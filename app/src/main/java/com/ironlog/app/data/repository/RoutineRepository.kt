package com.ironlog.app.data.repository

import com.ironlog.app.core.security.Validators
import com.ironlog.app.data.dao.RoutineDao
import com.ironlog.app.data.dao.RoutineWithExercises
import com.ironlog.app.data.entity.RoutineEntity
import com.ironlog.app.data.entity.RoutineExerciseEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** One movement in a routine being edited. Values are validated on save. */
data class RoutineExerciseDraft(
    val exerciseId: String,
    val exerciseName: String,
    val targetSets: Int = 3,
    val targetRepsLow: Int = 8,
    val targetRepsHigh: Int = 12,
    val restSeconds: Int = 120,
    val notes: String = "",
    val targetWeightKg: Double? = null,
    val progressionKg: Double = 0.0
)

data class RoutineDraft(
    val id: String? = null,
    val name: String = "",
    val folder: String = "My routines",
    val notes: String = "",
    val exercises: List<RoutineExerciseDraft> = emptyList()
)

interface RoutineRepository {
    fun observeRoutines(): Flow<List<RoutineWithExercises>>
    fun observeRoutine(id: String): Flow<RoutineWithExercises?>
    suspend fun loadDraft(id: String): Result<RoutineDraft>
    suspend fun save(draft: RoutineDraft): Result<String>
    suspend fun delete(id: String): Result<Unit>
}

@Singleton
class RoutineRepositoryImpl @Inject constructor(
    private val routineDao: RoutineDao,
    private val currentUser: CurrentUser
) : RoutineRepository {

    override fun observeRoutines(): Flow<List<RoutineWithExercises>> {
        val userId = currentUser.idOrNull() ?: return flowOf(emptyList())
        return routineDao.observeRoutines(userId)
    }

    override fun observeRoutine(id: String): Flow<RoutineWithExercises?> = routineDao.observeRoutine(id)

    override suspend fun loadDraft(id: String): Result<RoutineDraft> = withContext(Dispatchers.IO) {
        runCatching {
            val routine = routineDao.getRoutine(id) ?: throw UserFacingException("Routine not found")
            RoutineDraft(
                id = routine.routine.id,
                name = routine.routine.name,
                folder = routine.routine.folder,
                notes = routine.routine.notes,
                exercises = routine.exercises
                    .sortedBy { it.routineExercise.orderIndex }
                    .map {
                        RoutineExerciseDraft(
                            exerciseId = it.exercise.id,
                            exerciseName = it.exercise.name,
                            targetSets = it.routineExercise.targetSets,
                            targetRepsLow = it.routineExercise.targetRepsLow,
                            targetRepsHigh = it.routineExercise.targetRepsHigh,
                            restSeconds = it.routineExercise.restSeconds,
                            notes = it.routineExercise.notes,
                            targetWeightKg = it.routineExercise.targetWeightKg,
                            progressionKg = it.routineExercise.progressionKg
                        )
                    }
            )
        }
    }

    override suspend fun save(draft: RoutineDraft): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = currentUser.require()
            val name = Validators.sanitiseSingleLine(draft.name)
            Validators.validateRoutineName(name)?.let { throw UserFacingException(it) }
            if (draft.exercises.isEmpty()) throw UserFacingException("Add at least one exercise")
            draft.exercises.forEach {
                if (it.targetSets !in 1..20) throw UserFacingException("${it.exerciseName}: sets must be 1-20")
                if (it.targetRepsLow !in 1..100 || it.targetRepsHigh !in it.targetRepsLow..100) {
                    throw UserFacingException("${it.exerciseName}: check the rep range")
                }
                if (it.restSeconds !in 0..900) throw UserFacingException("${it.exerciseName}: rest must be 0-15 min")
                it.targetWeightKg?.let { w -> if (w <= 0.0 || w > 1000.0) throw UserFacingException("${it.exerciseName}: check the target weight") }
                if (it.progressionKg < 0.0 || it.progressionKg > 50.0) throw UserFacingException("${it.exerciseName}: check the progression step")
            }

            val now = System.currentTimeMillis()
            val existing = draft.id?.let { routineDao.getRoutine(it)?.routine }
            if (existing != null && existing.userId != userId) throw UserFacingException("Routine not found")

            val routine = RoutineEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                userId = userId,
                name = name,
                folder = Validators.sanitiseSingleLine(draft.folder).ifBlank { "My routines" },
                notes = Validators.sanitiseText(draft.notes),
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                lastPerformedAt = existing?.lastPerformedAt,
                orderIndex = existing?.orderIndex ?: 0
            )
            val items = draft.exercises.mapIndexed { index, item ->
                RoutineExerciseEntity(
                    id = UUID.randomUUID().toString(),
                    routineId = routine.id,
                    exerciseId = item.exerciseId,
                    orderIndex = index,
                    targetSets = item.targetSets,
                    targetRepsLow = item.targetRepsLow,
                    targetRepsHigh = item.targetRepsHigh,
                    restSeconds = item.restSeconds,
                    notes = Validators.sanitiseText(item.notes, 200),
                    targetWeightKg = item.targetWeightKg,
                    progressionKg = item.progressionKg
                )
            }
            routineDao.saveRoutine(routine, items)
            routine.id
        }
    }

    override suspend fun delete(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = currentUser.require()
            val routine = routineDao.getRoutine(id) ?: throw UserFacingException("Routine not found")
            if (routine.routine.userId != userId) throw UserFacingException("Routine not found")
            routineDao.deleteRoutine(id)
        }
    }
}
