package com.ironlog.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.ironlog.app.data.entity.ExerciseEntity
import com.ironlog.app.data.entity.RoutineEntity
import com.ironlog.app.data.entity.RoutineExerciseEntity
import com.ironlog.app.data.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(user: UserEntity)

    @Update
    suspend fun update(user: UserEntity)

    /**
     * Email is normalised to lowercase before storage, so lookups compare
     * lowercase to lowercase rather than relying on collation.
     */
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun findByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<UserEntity?>

    @Query("SELECT COUNT(*) FROM users WHERE email = :email")
    suspend fun countByEmail(email: String): Int

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ExerciseDao {

    @Upsert
    suspend fun upsert(exercise: ExerciseEntity)

    /** A library or own custom exercise by name, ignoring case; for matching imported rows. */
    @Query(
        "SELECT * FROM exercises WHERE LOWER(name) = LOWER(:name) AND (ownerUserId IS NULL OR ownerUserId = :userId) " +
            "AND isArchived = 0 ORDER BY isCustom ASC LIMIT 1"
    )
    suspend fun findByName(name: String, userId: String): ExerciseEntity?

    @Upsert
    suspend fun upsertAll(exercises: List<ExerciseEntity>)

    @Delete
    suspend fun delete(exercise: ExerciseEntity)

    @Query("DELETE FROM exercises WHERE ownerUserId = :userId")
    suspend fun deleteOwnedBy(userId: String)

    @Query("SELECT COUNT(*) FROM exercises WHERE isCustom = 0")
    suspend fun countSeeded(): Int

    @Query("SELECT * FROM exercises WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<ExerciseEntity?>

    /**
     * The library query. Blank filters are passed as null and short-circuited in
     * SQL so a single prepared statement serves every combination of filters.
     */
    @Query(
        """
        SELECT * FROM exercises
        WHERE isArchived = 0
          AND (ownerUserId IS NULL OR ownerUserId = :userId)
          AND (:query IS NULL OR name LIKE '%' || :query || '%')
          AND (:muscle IS NULL OR primaryMuscle = :muscle)
          AND (:category IS NULL OR category = :category)
        ORDER BY isCustom DESC, name ASC
        """
    )
    fun observeLibrary(
        userId: String,
        query: String?,
        muscle: String?,
        category: String?
    ): Flow<List<ExerciseEntity>>

    @Query(
        """
        SELECT e.id AS exerciseId, e.name AS name, COUNT(DISTINCT w.id) AS sessions
        FROM exercises e
        JOIN workout_exercises we ON we.exerciseId = e.id
        JOIN workouts w ON w.id = we.workoutId
        WHERE w.userId = :userId AND w.finishedAt IS NOT NULL
        GROUP BY e.id
        ORDER BY sessions DESC
        LIMIT :limit
        """
    )
    fun observeMostUsed(userId: String, limit: Int = 8): Flow<List<ExerciseFrequency>>
}

@Dao
interface RoutineDao {

    @Upsert
    suspend fun upsertRoutine(routine: RoutineEntity)

    @Upsert
    suspend fun upsertRoutineExercises(items: List<RoutineExerciseEntity>)

    @Query("DELETE FROM routine_exercises WHERE routineId = :routineId")
    suspend fun clearRoutineExercises(routineId: String)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteRoutine(id: String)

    @Transaction
    @Query("SELECT * FROM routines WHERE userId = :userId ORDER BY folder ASC, orderIndex ASC, name ASC")
    fun observeRoutines(userId: String): Flow<List<RoutineWithExercises>>

    @Transaction
    @Query("SELECT * FROM routines WHERE id = :id LIMIT 1")
    fun observeRoutine(id: String): Flow<RoutineWithExercises?>

    @Transaction
    @Query("SELECT * FROM routines WHERE id = :id LIMIT 1")
    suspend fun getRoutine(id: String): RoutineWithExercises?

    @Query("SELECT DISTINCT folder FROM routines WHERE userId = :userId ORDER BY folder")
    fun observeFolders(userId: String): Flow<List<String>>

    @Query("UPDATE routines SET lastPerformedAt = :timestamp WHERE id = :id")
    suspend fun markPerformed(id: String, timestamp: Long)

    /**
     * Replacing a routine's exercise list is one transaction so a crash halfway
     * through cannot leave a routine with a partial set of movements.
     */
    @Transaction
    suspend fun saveRoutine(routine: RoutineEntity, exercises: List<RoutineExerciseEntity>) {
        upsertRoutine(routine)
        clearRoutineExercises(routine.id)
        upsertRoutineExercises(exercises)
    }
}
