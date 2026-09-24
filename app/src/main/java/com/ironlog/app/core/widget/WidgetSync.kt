package com.ironlog.app.core.widget

import android.content.Context
import com.ironlog.app.data.repository.AuthRepository
import com.ironlog.app.data.repository.NutritionRepository
import com.ironlog.app.data.repository.ProfileRepository
import com.ironlog.app.data.repository.SessionState
import com.ironlog.app.data.repository.WorkoutRepository
import com.ironlog.app.domain.usecase.TrainingMath
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Keeps the widget's numbers in step with the log.
 *
 * Runs for the life of the process, watching the same repositories the Home
 * screen does and publishing whenever they change, so the widget is right
 * without any screen having to remember to tell it. Re-subscribes when the
 * session changes, because the repositories answer for whoever is signed in
 * at the moment they are asked.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class WidgetSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val workoutRepository: WorkoutRepository,
    private val profileRepository: ProfileRepository,
    private val nutritionRepository: NutritionRepository,
    private val summary: WidgetSummary
) {
    private data class Training(val workoutsThisWeek: Int, val goal: Int, val streak: Int)
    private data class Food(val kcal: Int, val target: Int?)

    fun start(scope: CoroutineScope) {
        val signedIn = authRepository.sessionState.map { it is SessionState.SignedIn }.distinctUntilChanged()

        scope.launch {
            signedIn.flatMapLatest { on ->
                if (!on) flowOf(Training(0, 0, 0)) else combine(
                    workoutRepository.observeWorkoutDates(),
                    profileRepository.observeUser()
                ) { dates, user ->
                    val zone = ZoneId.systemDefault()
                    val today = LocalDate.now(zone)
                    val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
                    val thisWeek = dates.count { !Instant.ofEpochMilli(it).atZone(zone).toLocalDate().isBefore(weekStart) }
                    Training(thisWeek, user?.weeklyGoalWorkouts ?: 0, TrainingMath.weeklyStreak(dates))
                }
            }.distinctUntilChanged().collect { t ->
                summary.publishTraining(context, t.workoutsThisWeek, t.goal, t.streak)
            }
        }

        scope.launch {
            signedIn.flatMapLatest { on ->
                if (!on) flowOf(Food(0, null)) else combine(
                    nutritionRepository.observeDay(System.currentTimeMillis()),
                    nutritionRepository.observeGoal()
                ) { entries, goal ->
                    Food(entries.sumOf { it.kcal }.roundToInt(), goal?.targetKcal)
                }
            }.distinctUntilChanged().collect { f ->
                summary.publishFood(context, f.kcal, f.target)
            }
        }
    }
}
