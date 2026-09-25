package com.ironlog.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironlog.app.core.database.AppDatabase
import com.ironlog.app.data.entity.ExerciseCategory
import com.ironlog.app.data.entity.ExerciseEntity
import com.ironlog.app.data.entity.MuscleGroup
import com.ironlog.app.data.entity.RecordType
import com.ironlog.app.data.entity.SetType
import com.ironlog.app.data.entity.UserEntity
import com.ironlog.app.core.security.SecureTokenStore
import com.ironlog.app.data.repository.CurrentUser
import com.ironlog.app.data.repository.WorkoutRepository
import com.ironlog.app.data.repository.WorkoutRepositoryImpl
import com.ironlog.app.domain.usecase.DetectPersonalRecords
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The workout logger's repository, driven the way the screen drives it:
 * start, add an exercise, fill in sets, tick them off, finish.
 *
 * Real DAOs on an in-memory database and the real record detector. The one
 * thing stubbed is [CurrentUser], which in the app reads the encrypted
 * session store; here it answers with a fixed id so the test never touches
 * the session of whoever is signed in to the installed app.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: WorkoutRepository

    private val userId = "repo-user"
    private val bench = ExerciseEntity(id = "bench", name = "Bench press", primaryMuscle = MuscleGroup.CHEST, category = ExerciseCategory.BARBELL)

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        db.userDao().insert(
            UserEntity(id = userId, email = "repo@example.com", displayName = "Repo", passwordHash = "h", passwordSalt = "s", createdAt = 0)
        )
        db.exerciseDao().upsert(bench)

        val signedIn = object : CurrentUser(SecureTokenStore(ApplicationProvider.getApplicationContext())) {
            override fun idOrNull(): String = userId
            override fun require(): String = userId
        }

        val recordDao = db.personalRecordDao()
        repository = WorkoutRepositoryImpl(
            db = db,
            workoutDao = db.workoutDao(),
            routineDao = db.routineDao(),
            exerciseDao = db.exerciseDao(),
            userDao = db.userDao(),
            recordDao = recordDao,
            records = DetectPersonalRecords(recordDao),
            currentUser = signedIn
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun finishingASessionCountsWorkingSetsOnlyAndDropsUntickedOnes() = runBlocking {
        val workoutId = repository.start(null).getOrThrow()
        repository.addExercises(workoutId, listOf(bench.id)).getOrThrow()
        val sets = repository.observeWorkout(workoutId).first()!!.exercises.single().sets.sortedBy { it.setNumber }
        assertEquals("three sets are laid out when there is no history", 3, sets.size)

        // A heavy warm-up single, one working set, and the third left untouched.
        repository.updateSet(sets[0].copy(type = SetType.WARMUP, weightKg = 120.0, reps = 1)).getOrThrow()
        repository.updateSet(sets[1].copy(weightKg = 100.0, reps = 5)).getOrThrow()
        repository.updateSet(sets[2].copy(weightKg = 100.0, reps = 5)).getOrThrow()
        repository.toggleComplete(sets[0].id, null, null).getOrThrow()
        repository.toggleComplete(sets[1].id, null, null).getOrThrow()

        val summary = repository.finishActive().getOrThrow()

        assertEquals(1, summary.sets)
        assertEquals(5, summary.reps)
        assertEquals(500.0, summary.volumeKg, 1e-9)
        val stored = repository.observeWorkout(workoutId).first()!!
        assertFalse(stored.workout.isActive)
        assertEquals("the unticked set is gone", 2, stored.exercises.single().sets.size)
        assertEquals(500.0, stored.workout.totalVolumeKg, 1e-9)
        assertNull("no session is active any more", repository.observeActive().first())
    }

    @Test
    fun aWarmUpNeverSetsARecordAndARepeatDoesNotBeatOne() = runBlocking {
        // Session one: the 120 kg warm-up must not become the heaviest-weight record.
        val first = session(warmUpKg = 120.0, workingKg = 100.0, reps = 5)
        val heaviest = first.records.single { it.type == RecordType.HEAVIEST_WEIGHT }
        assertEquals(100.0, heaviest.value, 1e-9)
        assertTrue("every record in a first session is new", first.records.all { it.previousValue == null })
        assertFalse("a first session is not a session-volume record over nothing", first.isSessionVolumeRecord)

        // Session two: the same working weight and reps. Equal is not better.
        val second = session(warmUpKg = 120.0, workingKg = 100.0, reps = 5)
        assertTrue("repeating a best is not a new record", second.records.isEmpty())
        assertFalse(second.isSessionVolumeRecord)

        // Session three: one more rep beats the estimated 1RM, set volume and most-reps
        // records, but not the heaviest weight.
        val third = session(warmUpKg = 120.0, workingKg = 100.0, reps = 6)
        val types = third.records.map { it.type }.toSet()
        assertTrue(RecordType.BEST_ESTIMATED_1RM in types)
        assertTrue(RecordType.BEST_SET_VOLUME in types)
        assertTrue(RecordType.MOST_REPS in types)
        assertFalse(RecordType.HEAVIEST_WEIGHT in types)
        assertTrue("600 kg beats the previous 500 kg session", third.isSessionVolumeRecord)
        assertEquals("one row per record type, never one per session", 4, repository.observeRecordCount().first())
    }

    @Test
    fun failuresComeBackAsResultsWithMessagesForTheUser() = runBlocking {
        val workoutId = repository.start(null).getOrThrow()
        repository.addExercises(workoutId, listOf(bench.id)).getOrThrow()
        val set = repository.observeWorkout(workoutId).first()!!.exercises.single().sets.first()

        val noReps = repository.toggleComplete(set.id, fallbackWeightKg = 100.0, fallbackReps = null)
        assertTrue(noReps.isFailure)
        assertEquals("Enter the reps for this set first", noReps.exceptionOrNull()?.message)

        val nothingDone = repository.finishActive()
        assertTrue(nothingDone.isFailure)
        assertEquals("Complete at least one set, or discard the workout", nothingDone.exceptionOrNull()?.message)

        repository.discardActive().getOrThrow()
        assertNull(repository.observeActive().first())
    }

    /** One full session on the bench: a warm-up single and one working set, finished. */
    private suspend fun session(warmUpKg: Double, workingKg: Double, reps: Int) = run {
        val workoutId = repository.start(null).getOrThrow()
        repository.addExercises(workoutId, listOf(bench.id)).getOrThrow()
        val sets = repository.observeWorkout(workoutId).first()!!.exercises.single().sets.sortedBy { it.setNumber }
        repository.updateSet(sets[0].copy(type = SetType.WARMUP, weightKg = warmUpKg, reps = 1)).getOrThrow()
        repository.updateSet(sets[1].copy(weightKg = workingKg, reps = reps)).getOrThrow()
        repository.toggleComplete(sets[0].id, null, null).getOrThrow()
        repository.toggleComplete(sets[1].id, null, null).getOrThrow()
        repository.finishActive().getOrThrow()
    }
}
