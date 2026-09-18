package com.ironlog.app.data.repository

import com.ironlog.app.core.security.Validators
import com.ironlog.app.data.dao.ExerciseDao
import com.ironlog.app.data.dao.ExerciseFrequency
import com.ironlog.app.data.dao.ExerciseSessionPoint
import com.ironlog.app.data.dao.PersonalRecordDao
import com.ironlog.app.data.dao.WorkoutDao
import com.ironlog.app.data.entity.ExerciseCategory
import com.ironlog.app.data.entity.ExerciseEntity
import com.ironlog.app.data.entity.MuscleGroup
import com.ironlog.app.data.entity.PersonalRecordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface ExerciseRepository {
    fun observeLibrary(query: String, muscle: MuscleGroup?, category: ExerciseCategory?): Flow<List<ExerciseEntity>>
    fun observeExercise(id: String): Flow<ExerciseEntity?>
    fun observeMostUsed(limit: Int = 8): Flow<List<ExerciseFrequency>>
    fun observeHistory(exerciseId: String): Flow<List<ExerciseSessionPoint>>
    fun observeRecords(exerciseId: String): Flow<List<PersonalRecordEntity>>
    suspend fun createCustom(
        name: String,
        muscle: MuscleGroup,
        category: ExerciseCategory,
        instructions: String
    ): Result<ExerciseEntity>
    suspend fun archiveCustom(id: String): Result<Unit>
}

@Singleton
class ExerciseRepositoryImpl @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val recordDao: PersonalRecordDao,
    private val currentUser: CurrentUser
) : ExerciseRepository {

    override fun observeLibrary(
        query: String,
        muscle: MuscleGroup?,
        category: ExerciseCategory?
    ): Flow<List<ExerciseEntity>> {
        val userId = currentUser.idOrNull() ?: return flowOf(emptyList())
        val cleanQuery = Validators.sanitiseSingleLine(query).ifBlank { null }
        return exerciseDao.observeLibrary(userId, cleanQuery, muscle?.name, category?.name)
    }

    override fun observeExercise(id: String): Flow<ExerciseEntity?> = exerciseDao.observeById(id)

    override fun observeMostUsed(limit: Int): Flow<List<ExerciseFrequency>> {
        val userId = currentUser.idOrNull() ?: return flowOf(emptyList())
        return exerciseDao.observeMostUsed(userId, limit)
    }

    override fun observeHistory(exerciseId: String): Flow<List<ExerciseSessionPoint>> {
        val userId = currentUser.idOrNull() ?: return flowOf(emptyList())
        return workoutDao.observeExerciseHistory(userId, exerciseId)
    }

    override fun observeRecords(exerciseId: String): Flow<List<PersonalRecordEntity>> {
        val userId = currentUser.idOrNull() ?: return flowOf(emptyList())
        return recordDao.observeForExercise(userId, exerciseId)
    }

    override suspend fun createCustom(
        name: String,
        muscle: MuscleGroup,
        category: ExerciseCategory,
        instructions: String
    ): Result<ExerciseEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = currentUser.require()
            val cleanName = Validators.sanitiseSingleLine(name)
            Validators.validateExerciseName(cleanName)?.let { throw UserFacingException(it) }

            val exercise = ExerciseEntity(
                id = "custom-${UUID.randomUUID()}",
                name = cleanName,
                primaryMuscle = muscle,
                category = category,
                instructions = Validators.sanitiseText(instructions),
                ownerUserId = userId,
                isCustom = true,
                defaultBarWeightKg = if (category == ExerciseCategory.BARBELL) 20.0 else null
            )
            exerciseDao.upsert(exercise)
            exercise
        }
    }

    /**
     * Custom exercises are archived rather than deleted: deleting would cascade
     * through workout_exercises and silently erase every session that used it.
     */
    override suspend fun archiveCustom(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = currentUser.require()
            val exercise = exerciseDao.findById(id) ?: throw UserFacingException("Exercise not found")
            if (!exercise.isCustom || exercise.ownerUserId != userId) {
                throw UserFacingException("Only your own exercises can be removed")
            }
            exerciseDao.upsert(exercise.copy(isArchived = true))
        }
    }
}
