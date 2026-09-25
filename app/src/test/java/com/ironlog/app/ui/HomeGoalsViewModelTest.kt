package com.ironlog.app.ui

import com.ironlog.app.data.dao.GoalWithExercise
import com.ironlog.app.data.dao.HistoricSet
import com.ironlog.app.data.dao.MuscleSessionRow
import com.ironlog.app.data.entity.GoalMetric
import com.ironlog.app.data.entity.SetType
import com.ironlog.app.data.entity.StrengthGoalEntity
import com.ironlog.app.data.entity.WorkoutSetEntity
import com.ironlog.app.data.repository.StrengthGoalRepository
import com.ironlog.app.ui.screens.goals.HomeGoalsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The Home goals card's view model against a scripted repository: the
 * database rows go in, and what comes out is what the card would draw.
 *
 * The repository is faked at its interface, which is the seam the app's
 * architecture leaves for exactly this. No Android, no Room: the test runs
 * on the JVM in a fraction of a second.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeGoalsViewModelTest {

    private val repository = FakeStrengthGoalRepository()

    @Before
    fun useTestMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun restoreMain() = Dispatchers.resetMain()

    @Test
    fun aGoalIsMeasuredAgainstTheBestWorkingSetOnItsExercise() = runTest {
        repository.goals.value = listOf(goal("g1", "bench", GoalMetric.TOP_WEIGHT, targetKg = 100.0))
        repository.sets["bench"] = listOf(
            historic(80.0, 5),
            historic(90.0, 3),
            // A heavier warm-up single is not a working set and must not count.
            historic(110.0, 1, type = SetType.WARMUP)
        )

        val viewModel = HomeGoalsViewModel(repository)
        backgroundScope.launch { viewModel.goals.collect {} }
        runCurrent()

        val card = viewModel.goals.value.single()
        assertEquals("Bench press", card.exerciseName)
        assertEquals(90.0, card.currentKg, 1e-9)
        assertEquals(10.0, card.remainingKg, 1e-9)
        assertEquals(0.9f, card.progress, 1e-6f)
        assertFalse(card.isAchieved)
    }

    @Test
    fun aGoalWithNoSetsYetShowsZeroRatherThanDisappearing() = runTest {
        repository.goals.value = listOf(goal("g1", "squat", GoalMetric.ESTIMATED_1RM, targetKg = 140.0))

        val viewModel = HomeGoalsViewModel(repository)
        backgroundScope.launch { viewModel.goals.collect {} }
        runCurrent()

        val card = viewModel.goals.value.single()
        assertEquals(0.0, card.currentKg, 1e-9)
        assertEquals(0f, card.progress, 1e-6f)
    }

    @Test
    fun aRepositoryFailureLeavesTheCardEmptyInsteadOfCrashingHome() = runTest {
        repository.goals.value = listOf(goal("g1", "bench", GoalMetric.TOP_WEIGHT, targetKg = 100.0))
        repository.failSets = true

        val viewModel = HomeGoalsViewModel(repository)
        backgroundScope.launch { viewModel.goals.collect {} }
        runCurrent()

        assertTrue(viewModel.goals.value.isEmpty())
    }

    // ---------------------------------------------------------------- fixtures

    private fun goal(id: String, exerciseId: String, metric: GoalMetric, targetKg: Double) = GoalWithExercise(
        goal = StrengthGoalEntity(id = id, userId = "u1", exerciseId = exerciseId, metric = metric, targetKg = targetKg, createdAt = 0),
        exerciseName = if (exerciseId == "bench") "Bench press" else "Back squat"
    )

    private fun historic(weightKg: Double, reps: Int, type: SetType = SetType.NORMAL) = HistoricSet(
        set = WorkoutSetEntity(
            id = "s-$weightKg-$reps", workoutExerciseId = "we", setNumber = 1,
            type = type, weightKg = weightKg, reps = reps, isCompleted = true
        ),
        workoutId = "w1",
        startedAt = 1_000
    )

    private class FakeStrengthGoalRepository : StrengthGoalRepository {
        val goals = MutableStateFlow<List<GoalWithExercise>>(emptyList())
        val sets = mutableMapOf<String, List<HistoricSet>>()
        var failSets = false

        override fun observeGoals(): Flow<List<GoalWithExercise>> = goals

        override fun observeSetsFor(exerciseId: String): Flow<List<HistoricSet>> =
            if (failSets) flow { throw IllegalStateException("database closed") }
            else flowOf(sets[exerciseId].orEmpty())

        override fun observeMuscleSessions(): Flow<List<MuscleSessionRow>> = flowOf(emptyList())
        override suspend fun addGoal(exerciseId: String, metric: GoalMetric, targetKg: Double) = Result.success(Unit)
        override suspend fun markAchieved(goalId: String, achievedAt: Long) = Result.success(Unit)
        override suspend fun deleteGoal(goalId: String) = Result.success(Unit)
    }
}
