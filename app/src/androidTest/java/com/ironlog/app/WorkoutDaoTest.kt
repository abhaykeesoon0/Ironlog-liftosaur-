package com.ironlog.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironlog.app.core.database.AppDatabase
import com.ironlog.app.data.entity.ExerciseCategory
import com.ironlog.app.data.entity.ExerciseEntity
import com.ironlog.app.data.entity.MuscleGroup
import com.ironlog.app.data.entity.SetType
import com.ironlog.app.data.entity.UserEntity
import com.ironlog.app.data.entity.WorkoutEntity
import com.ironlog.app.data.entity.WorkoutExerciseEntity
import com.ironlog.app.data.entity.WorkoutSetEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The aggregate queries the Home and Progress screens are built on, run
 * against a real in-memory Room database rather than a fake DAO. The SQL is
 * the thing under test: a fake would only prove the test agrees with itself.
 *
 * The rule that matters most is the warm-up rule -- warm-up sets never count
 * toward volume -- because it is applied inside SQL here, not in Kotlin, and
 * so is not covered by the TrainingMath unit tests.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutDaoTest {

    private lateinit var db: AppDatabase

    private val user = UserEntity(
        id = "u1", email = "dao@example.com", displayName = "Dao", passwordHash = "h", passwordSalt = "s", createdAt = 0
    )
    private val squat = ExerciseEntity(id = "squat", name = "Back squat", primaryMuscle = MuscleGroup.QUADS, category = ExerciseCategory.BARBELL)

    @Before
    fun openDatabase() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        db.userDao().insert(user)
        db.exerciseDao().upsert(squat)
    }

    @After
    fun closeDatabase() = db.close()

    @Test
    fun totalsSkipWarmUpsAndUnfinishedSessions() = runBlocking {
        // A finished session: one warm-up, two working sets.
        finishedWorkout("w1", startedAt = 1_000, volume = 0.0)
        sets(
            "w1", "we1",
            set(1, SetType.WARMUP, 60.0, 10),
            set(2, SetType.NORMAL, 100.0, 5),
            set(3, SetType.NORMAL, 100.0, 5)
        )
        // A session still in progress: its sets are plans, not work.
        db.workoutDao().upsertWorkout(WorkoutEntity(id = "w2", userId = "u1", name = "Live", startedAt = 2_000, isActive = true))
        sets("w2", "we2", set(1, SetType.NORMAL, 140.0, 3))

        val totals = db.workoutDao().observeTotals("u1", from = 0, to = 10_000).first()

        assertEquals(1, totals.workouts)
        assertEquals(2, totals.sets)
        assertEquals(10, totals.reps)
        assertEquals(1000.0, totals.volumeKg, 1e-9)
        // The warm-up's 60 kg is not the lightest working weight, and the unfinished 140 kg is not the heaviest.
        assertEquals(100.0, totals.bestWeightKg, 1e-9)
        assertEquals(100.0, totals.averageWeightKg, 1e-9)
    }

    @Test
    fun volumeSinceSumsOnlyFinishedSessionsInsideTheWindow() = runBlocking {
        finishedWorkout("old", startedAt = 500, volume = 400.0)
        finishedWorkout("recent", startedAt = 5_000, volume = 1_200.0)
        finishedWorkout("later", startedAt = 6_000, volume = 300.0)
        db.workoutDao().upsertWorkout(
            WorkoutEntity(id = "active", userId = "u1", name = "Live", startedAt = 7_000, isActive = true, totalVolumeKg = 999.0)
        )

        assertEquals(1_500.0, db.workoutDao().observeVolumeSince("u1", since = 5_000).first(), 1e-9)
        assertEquals(2, db.workoutDao().observeWorkoutCountSince("u1", since = 5_000).first())
    }

    @Test
    fun deletingTheUserCascadesThroughWorkoutsExercisesAndSets() = runBlocking {
        finishedWorkout("w1", startedAt = 1_000, volume = 0.0)
        sets("w1", "we1", set(1, SetType.NORMAL, 100.0, 5))

        db.userDao().deleteById("u1")

        assertNull(db.workoutDao().getWorkout("w1"))
        assertNull(db.workoutDao().findWorkoutExercise("we1"))
        assertEquals(0, db.workoutDao().setsFor("we1").size)
    }

    // ---------------------------------------------------------------- fixtures

    private suspend fun finishedWorkout(id: String, startedAt: Long, volume: Double) {
        db.workoutDao().upsertWorkout(
            WorkoutEntity(
                id = id, userId = "u1", name = "Session $id", startedAt = startedAt,
                finishedAt = startedAt + 3_600_000, totalVolumeKg = volume
            )
        )
    }

    private suspend fun sets(workoutId: String, workoutExerciseId: String, vararg rows: (String) -> WorkoutSetEntity) {
        db.workoutDao().upsertWorkoutExercise(
            WorkoutExerciseEntity(id = workoutExerciseId, workoutId = workoutId, exerciseId = squat.id, orderIndex = 0)
        )
        db.workoutDao().upsertSets(rows.map { it(workoutExerciseId) })
    }

    private fun set(number: Int, type: SetType, weightKg: Double, reps: Int): (String) -> WorkoutSetEntity = { parent ->
        WorkoutSetEntity(
            id = "$parent-$number", workoutExerciseId = parent, setNumber = number, type = type,
            weightKg = weightKg, reps = reps, isCompleted = true, completedAt = 1
        )
    }
}
