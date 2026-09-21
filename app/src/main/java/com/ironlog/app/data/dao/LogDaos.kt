package com.ironlog.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.ironlog.app.data.entity.BodyMeasurementEntity
import com.ironlog.app.data.entity.CardioSessionEntity
import com.ironlog.app.data.entity.DailyActivityEntity
import com.ironlog.app.data.entity.GoalMetric
import com.ironlog.app.data.entity.PersonalRecordEntity
import com.ironlog.app.data.entity.ProgressPhotoEntity
import com.ironlog.app.data.entity.QuestClaimEntity
import com.ironlog.app.data.entity.StorePurchaseEntity
import com.ironlog.app.data.entity.StrengthGoalEntity
import com.ironlog.app.data.entity.WorkoutEntity
import com.ironlog.app.data.entity.WorkoutExerciseEntity
import com.ironlog.app.data.entity.WorkoutSetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    @Upsert suspend fun upsertWorkout(workout: WorkoutEntity)
    @Upsert suspend fun upsertWorkoutExercise(item: WorkoutExerciseEntity)
    @Upsert suspend fun upsertWorkoutExercises(items: List<WorkoutExerciseEntity>)
    @Upsert suspend fun upsertSet(set: WorkoutSetEntity)
    @Upsert suspend fun upsertSets(sets: List<WorkoutSetEntity>)

    @Delete suspend fun deleteSet(set: WorkoutSetEntity)
    @Delete suspend fun deleteWorkoutExercise(item: WorkoutExerciseEntity)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteWorkout(id: String)

    /** The in-progress session, restored on cold start. */
    @Transaction
    @Query("SELECT * FROM workouts WHERE userId = :userId AND isActive = 1 LIMIT 1")
    fun observeActiveWorkout(userId: String): Flow<WorkoutWithDetail?>

    @Transaction
    @Query("SELECT * FROM workouts WHERE userId = :userId AND isActive = 1 LIMIT 1")
    suspend fun getActiveWorkout(userId: String): WorkoutWithDetail?

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id LIMIT 1")
    fun observeWorkout(id: String): Flow<WorkoutWithDetail?>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id LIMIT 1")
    suspend fun getWorkout(id: String): WorkoutWithDetail?

    @Transaction
    @Query(
        """
        SELECT * FROM workouts
        WHERE userId = :userId AND finishedAt IS NOT NULL
        ORDER BY startedAt DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun observeHistory(userId: String, limit: Int = 50, offset: Int = 0): Flow<List<WorkoutWithDetail>>

    @Query(
        """
        SELECT startedAt FROM workouts
        WHERE userId = :userId AND finishedAt IS NOT NULL
        ORDER BY startedAt DESC
        """
    )
    fun observeWorkoutDates(userId: String): Flow<List<Long>>

    @Query(
        """
        SELECT COUNT(*) FROM workouts
        WHERE userId = :userId AND finishedAt IS NOT NULL AND startedAt >= :since
        """
    )
    fun observeWorkoutCountSince(userId: String, since: Long): Flow<Int>

    @Query(
        """
        SELECT COALESCE(SUM(totalVolumeKg), 0) FROM workouts
        WHERE userId = :userId AND finishedAt IS NOT NULL AND startedAt >= :since
        """
    )
    fun observeVolumeSince(userId: String, since: Long): Flow<Double>

    /**
     * The previous performance shown greyed out in each set row. Pulls the sets
     * of the most recent finished workout that contained this exercise.
     */
    @Query(
        """
        SELECT s.* FROM workout_sets s
        JOIN workout_exercises we ON we.id = s.workoutExerciseId
        JOIN workouts w ON w.id = we.workoutId
        WHERE w.userId = :userId
          AND we.exerciseId = :exerciseId
          AND w.finishedAt IS NOT NULL
          AND w.id != :excludeWorkoutId
          AND s.isCompleted = 1
        ORDER BY w.startedAt DESC, s.setNumber ASC
        LIMIT 12
        """
    )
    suspend fun previousSetsFor(
        userId: String,
        exerciseId: String,
        excludeWorkoutId: String
    ): List<WorkoutSetEntity>

    @Query(
        """
        SELECT (w.startedAt + :utcOffsetMs) / 86400000 AS bucket,
               COALESCE(SUM(w.totalVolumeKg), 0) AS volume
        FROM workouts w
        WHERE w.userId = :userId AND w.finishedAt IS NOT NULL AND w.startedAt >= :since
        GROUP BY bucket
        ORDER BY bucket ASC
        """
    )
    fun observeDailyVolume(userId: String, since: Long, utcOffsetMs: Long): Flow<List<VolumePoint>>

    @Query(
        """
        SELECT e.primaryMuscle AS primaryMuscle,
               COALESCE(SUM(CASE WHEN s.type != 'WARMUP'
                    THEN COALESCE(s.weightKg, 0) * COALESCE(s.reps, 0) ELSE 0 END), 0) AS volume,
               SUM(CASE WHEN s.type != 'WARMUP' THEN 1 ELSE 0 END) AS sets
        FROM workout_sets s
        JOIN workout_exercises we ON we.id = s.workoutExerciseId
        JOIN workouts w ON w.id = we.workoutId
        JOIN exercises e ON e.id = we.exerciseId
        WHERE w.userId = :userId AND w.finishedAt IS NOT NULL
          AND w.startedAt >= :since AND s.isCompleted = 1
        GROUP BY e.primaryMuscle
        ORDER BY volume DESC
        """
    )
    fun observeMuscleSplit(userId: String, since: Long): Flow<List<MuscleVolume>>

    /** Per-session bests for one exercise, used by the exercise progress chart. */
    @Query(
        """
        SELECT w.id AS workoutId,
               w.startedAt AS startedAt,
               COALESCE(MAX(s.weightKg), 0) AS bestWeight,
               COALESCE(MAX(s.reps), 0) AS bestReps,
               COALESCE(SUM(CASE WHEN s.type != 'WARMUP'
                    THEN COALESCE(s.weightKg, 0) * COALESCE(s.reps, 0) ELSE 0 END), 0) AS volume
        FROM workout_sets s
        JOIN workout_exercises we ON we.id = s.workoutExerciseId
        JOIN workouts w ON w.id = we.workoutId
        WHERE w.userId = :userId AND we.exerciseId = :exerciseId
          AND w.finishedAt IS NOT NULL AND s.isCompleted = 1
        GROUP BY w.id
        ORDER BY w.startedAt ASC
        """
    )
    fun observeExerciseHistory(userId: String, exerciseId: String): Flow<List<ExerciseSessionPoint>>

    @Query("SELECT * FROM workout_sets WHERE workoutExerciseId = :workoutExerciseId ORDER BY setNumber")
    suspend fun setsFor(workoutExerciseId: String): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_sets WHERE id = :id LIMIT 1")
    suspend fun findSet(id: String): WorkoutSetEntity?

    @Query("SELECT * FROM workout_exercises WHERE id = :id LIMIT 1")
    suspend fun findWorkoutExercise(id: String): WorkoutExerciseEntity?

    @Query("DELETE FROM workout_sets WHERE id = :id")
    suspend fun deleteSetById(id: String)

    @Query("DELETE FROM workout_exercises WHERE id = :id")
    suspend fun deleteWorkoutExerciseById(id: String)

    @Transaction
    @Query("SELECT * FROM workouts WHERE userId = :userId AND finishedAt IS NOT NULL ORDER BY startedAt DESC")
    suspend fun allFinished(userId: String): List<WorkoutWithDetail>

    /** Best previous session, for the session-volume record. */
    @Query(
        """
        SELECT MAX(totalVolumeKg) FROM workouts
        WHERE userId = :userId AND finishedAt IS NOT NULL AND id != :excludeWorkoutId
        """
    )
    suspend fun bestSessionVolume(userId: String, excludeWorkoutId: String): Double?

    /** Every completed set of one exercise with its session, for rebuilding records. */
    @Query(
        """
        SELECT s.*, w.id AS workoutId, w.startedAt AS startedAt FROM workout_sets s
        JOIN workout_exercises we ON we.id = s.workoutExerciseId
        JOIN workouts w ON w.id = we.workoutId
        WHERE w.userId = :userId AND we.exerciseId = :exerciseId
          AND w.finishedAt IS NOT NULL AND s.isCompleted = 1
        ORDER BY w.startedAt ASC, s.setNumber ASC
        """
    )
    suspend fun completedSetsFor(userId: String, exerciseId: String): List<HistoricSet>
}

@Dao
interface PersonalRecordDao {

    @Upsert suspend fun upsert(record: PersonalRecordEntity)
    @Upsert suspend fun upsertAll(records: List<PersonalRecordEntity>)

    @Query(
        """
        SELECT * FROM personal_records
        WHERE userId = :userId AND exerciseId = :exerciseId AND recordType = :type
        LIMIT 1
        """
    )
    suspend fun find(userId: String, exerciseId: String, type: String): PersonalRecordEntity?

    @Query("SELECT * FROM personal_records WHERE userId = :userId AND exerciseId = :exerciseId")
    fun observeForExercise(userId: String, exerciseId: String): Flow<List<PersonalRecordEntity>>

    @Query("SELECT * FROM personal_records WHERE userId = :userId ORDER BY achievedAt DESC LIMIT :limit")
    fun observeRecent(userId: String, limit: Int = 20): Flow<List<PersonalRecordEntity>>

    @Query("SELECT COUNT(*) FROM personal_records WHERE userId = :userId")
    fun observeCount(userId: String): Flow<Int>

    @Query(
        """
        SELECT pr.*, e.name AS exerciseName FROM personal_records pr
        JOIN exercises e ON e.id = pr.exerciseId
        WHERE pr.userId = :userId
        ORDER BY pr.achievedAt DESC
        LIMIT :limit
        """
    )
    fun observeRecentWithNames(userId: String, limit: Int = 30): Flow<List<RecordWithExercise>>

    @Query("DELETE FROM personal_records WHERE userId = :userId AND exerciseId = :exerciseId")
    suspend fun deleteForExercise(userId: String, exerciseId: String)
}

@Dao
interface BodyDao {

    @Upsert suspend fun upsertMeasurement(item: BodyMeasurementEntity)
    @Delete suspend fun deleteMeasurement(item: BodyMeasurementEntity)

    @Query("SELECT * FROM body_measurements WHERE userId = :userId ORDER BY takenAt DESC")
    fun observeMeasurements(userId: String): Flow<List<BodyMeasurementEntity>>

    @Query("SELECT * FROM body_measurements WHERE userId = :userId ORDER BY takenAt DESC LIMIT 1")
    fun observeLatestMeasurement(userId: String): Flow<BodyMeasurementEntity?>

    @Upsert suspend fun upsertPhoto(item: ProgressPhotoEntity)
    @Delete suspend fun deletePhoto(item: ProgressPhotoEntity)

    @Query("SELECT * FROM progress_photos WHERE userId = :userId ORDER BY takenAt DESC")
    fun observePhotos(userId: String): Flow<List<ProgressPhotoEntity>>

    @Query("SELECT * FROM progress_photos WHERE id = :id LIMIT 1")
    suspend fun findPhoto(id: String): ProgressPhotoEntity?
}

@Dao
interface CardioDao {

    @Upsert suspend fun upsertSession(session: CardioSessionEntity)
    @Query("DELETE FROM cardio_sessions WHERE id = :id") suspend fun deleteSession(id: String)

    @Query("SELECT * FROM cardio_sessions WHERE id = :id LIMIT 1")
    suspend fun findSession(id: String): CardioSessionEntity?

    @Query("SELECT * FROM cardio_sessions WHERE id = :id LIMIT 1")
    fun observeSession(id: String): Flow<CardioSessionEntity?>

    @Query("SELECT * FROM cardio_sessions WHERE userId = :userId ORDER BY startedAt DESC")
    fun observeSessions(userId: String): Flow<List<CardioSessionEntity>>

    @Query("SELECT * FROM cardio_sessions WHERE userId = :userId AND finishedAt IS NULL LIMIT 1")
    suspend fun activeSession(userId: String): CardioSessionEntity?

    @Upsert suspend fun upsertDailyActivity(item: DailyActivityEntity)

    @Query("SELECT * FROM daily_activity WHERE userId = :userId AND dateEpochDay = :day LIMIT 1")
    suspend fun dailyActivity(userId: String, day: Long): DailyActivityEntity?

    @Query("SELECT * FROM daily_activity WHERE userId = :userId AND dateEpochDay = :day LIMIT 1")
    fun observeDailyActivity(userId: String, day: Long): Flow<DailyActivityEntity?>

    @Query("SELECT * FROM daily_activity WHERE userId = :userId AND dateEpochDay >= :sinceDay ORDER BY dateEpochDay")
    fun observeActivityRange(userId: String, sinceDay: Long): Flow<List<DailyActivityEntity>>
}

@Dao
interface StrengthGoalDao {

    @Upsert suspend fun upsert(goal: StrengthGoalEntity)

    @Query("DELETE FROM strength_goals WHERE id = :id") suspend fun deleteById(id: String)

    @Query("SELECT * FROM strength_goals WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): StrengthGoalEntity?

    @Query(
        """
        SELECT * FROM strength_goals
        WHERE userId = :userId AND exerciseId = :exerciseId AND metric = :metric LIMIT 1
        """
    )
    suspend fun findFor(userId: String, exerciseId: String, metric: GoalMetric): StrengthGoalEntity?

    @Query(
        """
        SELECT g.*, e.name AS exerciseName FROM strength_goals g
        JOIN exercises e ON e.id = g.exerciseId
        WHERE g.userId = :userId
        ORDER BY g.createdAt DESC
        """
    )
    fun observeGoals(userId: String): Flow<List<GoalWithExercise>>

    /**
     * Every completed set on one exercise, newest workout last, for working out
     * where a goal stands. Sets rather than a pre-aggregated row: an estimated
     * 1RM has to come from the weight and reps of the *same* set, and a query
     * that takes the heaviest weight and the highest reps separately would
     * combine two different sets into a lift that never happened.
     */
    @Query(
        """
        SELECT s.*, w.id AS workoutId, w.startedAt AS startedAt FROM workout_sets s
        JOIN workout_exercises we ON we.id = s.workoutExerciseId
        JOIN workouts w ON w.id = we.workoutId
        WHERE w.userId = :userId AND we.exerciseId = :exerciseId
          AND w.finishedAt IS NOT NULL AND s.isCompleted = 1
        ORDER BY w.startedAt ASC
        """
    )
    fun observeCompletedSets(userId: String, exerciseId: String): Flow<List<HistoricSet>>

    /**
     * Working volume per muscle per session, for the recovery readout. Grouped
     * by workout so each session contributes one row per muscle it worked.
     */
    @Query(
        """
        SELECT e.primaryMuscle AS muscle,
               w.startedAt AS trainedAt,
               COALESCE(SUM(CASE WHEN s.type != 'WARMUP'
                    THEN COALESCE(s.weightKg, 0) * COALESCE(s.reps, 0) ELSE 0 END), 0) AS volume
        FROM workout_sets s
        JOIN workout_exercises we ON we.id = s.workoutExerciseId
        JOIN workouts w ON w.id = we.workoutId
        JOIN exercises e ON e.id = we.exerciseId
        WHERE w.userId = :userId AND w.finishedAt IS NOT NULL
          AND s.isCompleted = 1 AND w.startedAt >= :since
        GROUP BY w.id, e.primaryMuscle
        ORDER BY w.startedAt DESC
        """
    )
    fun observeMuscleSessions(userId: String, since: Long): Flow<List<MuscleSessionRow>>
}

@Dao
interface RewardsDao {

    @Upsert suspend fun upsertClaim(claim: QuestClaimEntity)

    @Query("SELECT * FROM quest_claims WHERE userId = :userId AND periodKey IN (:periodKeys)")
    fun observeClaims(userId: String, periodKeys: List<String>): Flow<List<QuestClaimEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM quest_claims
        WHERE userId = :userId AND questId = :questId AND periodKey = :periodKey
        """
    )
    suspend fun claimCount(userId: String, questId: String, periodKey: String): Int

    @Upsert suspend fun upsertPurchase(purchase: StorePurchaseEntity)

    @Query("SELECT * FROM store_purchases WHERE userId = :userId")
    fun observePurchases(userId: String): Flow<List<StorePurchaseEntity>>

    @Query("SELECT * FROM store_purchases WHERE userId = :userId AND itemId = :itemId LIMIT 1")
    suspend fun findPurchase(userId: String, itemId: String): StorePurchaseEntity?

    @Query("SELECT * FROM store_purchases WHERE userId = :userId AND itemId IN (:itemIds)")
    suspend fun purchasesIn(userId: String, itemIds: List<String>): List<StorePurchaseEntity>

    /**
     * The wallet: everything earned less everything spent.
     *
     * Derived rather than held as a running total, so a balance can never drift
     * away from the claims and purchases that produced it -- there is no second
     * number to keep in step.
     */
    @Query(
        """
        SELECT COALESCE((SELECT SUM(coins) FROM quest_claims WHERE userId = :userId), 0) -
               COALESCE((SELECT SUM(coinsSpent) FROM store_purchases WHERE userId = :userId), 0)
        """
    )
    fun observeBalance(userId: String): Flow<Int>

    @Query(
        """
        SELECT COALESCE((SELECT SUM(coins) FROM quest_claims WHERE userId = :userId), 0) -
               COALESCE((SELECT SUM(coinsSpent) FROM store_purchases WHERE userId = :userId), 0)
        """
    )
    suspend fun balance(userId: String): Int
}
