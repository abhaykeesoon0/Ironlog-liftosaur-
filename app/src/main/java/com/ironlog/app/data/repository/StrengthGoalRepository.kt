package com.ironlog.app.data.repository

import com.ironlog.app.data.dao.GoalWithExercise
import com.ironlog.app.data.dao.HistoricSet
import com.ironlog.app.data.dao.MuscleSessionRow
import com.ironlog.app.data.dao.StrengthGoalDao
import com.ironlog.app.data.entity.GoalMetric
import com.ironlog.app.data.entity.StrengthGoalEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface StrengthGoalRepository {
    fun observeGoals(): Flow<List<GoalWithExercise>>

    /** Every completed set on one exercise, for computing a goal's progress. */
    fun observeSetsFor(exerciseId: String): Flow<List<HistoricSet>>

    /** Working volume per muscle per session, back far enough to cover recovery. */
    fun observeMuscleSessions(): Flow<List<MuscleSessionRow>>

    suspend fun addGoal(exerciseId: String, metric: GoalMetric, targetKg: Double): Result<Unit>
    suspend fun markAchieved(goalId: String, achievedAt: Long): Result<Unit>
    suspend fun deleteGoal(goalId: String): Result<Unit>
}

@Singleton
class StrengthGoalRepositoryImpl @Inject constructor(
    private val goalDao: StrengthGoalDao,
    private val currentUser: CurrentUser
) : StrengthGoalRepository {

    override fun observeGoals(): Flow<List<GoalWithExercise>> =
        currentUser.idOrNull()?.let { goalDao.observeGoals(it) } ?: flowOf(emptyList())

    override fun observeSetsFor(exerciseId: String): Flow<List<HistoricSet>> =
        currentUser.idOrNull()?.let { goalDao.observeCompletedSets(it, exerciseId) } ?: flowOf(emptyList())

    override fun observeMuscleSessions(): Flow<List<MuscleSessionRow>> {
        val userId = currentUser.idOrNull() ?: return flowOf(emptyList())
        // Four weeks rather than just the recovery window: the average a window
        // is scaled against needs several sessions behind it, and one training
        // block is the right amount of history to call "usual".
        val since = System.currentTimeMillis() - AVERAGE_LOOKBACK_MS
        return goalDao.observeMuscleSessions(userId, since)
    }

    override suspend fun addGoal(exerciseId: String, metric: GoalMetric, targetKg: Double): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val userId = currentUser.require()
                if (targetKg <= 0.0) throw UserFacingException("Enter a target above zero")
                if (targetKg > MAX_TARGET_KG) throw UserFacingException("That target looks unrealistic")

                // Upsert on the unique (user, exercise, metric) index would replace
                // a goal the user may have forgotten about, so an existing target
                // is reported rather than quietly overwritten.
                val existing = goalDao.findFor(userId, exerciseId, metric)
                if (existing != null) throw UserFacingException("You already have a goal for that lift and measure")

                goalDao.upsert(
                    StrengthGoalEntity(
                        id = UUID.randomUUID().toString(),
                        userId = userId,
                        exerciseId = exerciseId,
                        metric = metric,
                        targetKg = targetKg,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
        }

    override suspend fun markAchieved(goalId: String, achievedAt: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val goal = goalDao.findById(goalId) ?: throw UserFacingException("That goal is no longer there")
                // Only the first time: re-stamping it on every read would move the
                // date every time the screen opened.
                if (goal.achievedAt == null) goalDao.upsert(goal.copy(achievedAt = achievedAt))
            }
        }

    override suspend fun deleteGoal(goalId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { goalDao.deleteById(goalId) }
    }

    private companion object {
        /** Four weeks of training, enough for a per-muscle average to mean something. */
        const val AVERAGE_LOOKBACK_MS = 28L * 24 * 3_600_000

        /** Above the heaviest lift ever recorded; anything past it is a typo. */
        const val MAX_TARGET_KG = 600.0
    }
}
