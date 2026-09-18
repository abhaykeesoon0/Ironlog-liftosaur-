package com.ironlog.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.core.sensors.StepCounter
import com.ironlog.app.data.dao.RecordWithExercise
import com.ironlog.app.data.entity.UnitSystem
import com.ironlog.app.data.entity.WorkoutEntity
import com.ironlog.app.data.repository.CardioRepository
import com.ironlog.app.data.repository.ProfileRepository
import com.ironlog.app.data.repository.WorkoutRepository
import com.ironlog.app.data.repository.userMessage
import com.ironlog.app.domain.usecase.TrainingMath
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

data class ActiveWorkoutInfo(val id: String, val name: String, val startedAt: Long, val completedSets: Int)

data class HomeUiState(
    val displayName: String = "",
    val greeting: String = "",
    val unit: UnitSystem = UnitSystem.METRIC,
    val activeWorkout: ActiveWorkoutInfo? = null,
    val workoutsThisWeek: Int = 0,
    val weeklyGoal: Int = 4,
    val volumeThisWeekKg: Double = 0.0,
    val streakWeeks: Int = 0,
    val stepSensorAvailable: Boolean = false,
    val todaySteps: Int = 0,
    val lastWorkout: WorkoutEntity? = null,
    val recentRecords: List<RecordWithExercise> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val profileRepository: ProfileRepository,
    private val cardioRepository: CardioRepository,
    private val stepCounter: StepCounter
) : ViewModel() {

    private val _state = MutableStateFlow(
        HomeUiState(greeting = greetingForNow(), stepSensorAvailable = stepCounter.isAvailable())
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var stepJob: Job? = null

    init {
        observe()
    }

    private fun observe() {
        val weekStart = startOfWeekMillis()

        viewModelScope.launch {
            profileRepository.observeUser()
                .catch { e -> _state.update { it.copy(error = e.userMessage("Could not load your profile")) } }
                .collect { user ->
                    if (user != null) {
                        _state.update {
                            it.copy(displayName = user.displayName, unit = user.unitSystem, weeklyGoal = user.weeklyGoalWorkouts)
                        }
                    }
                }
        }

        viewModelScope.launch {
            workoutRepository.observeActive()
                .catch { e -> _state.update { it.copy(error = e.userMessage("Could not check for a workout in progress")) } }
                .collect { active ->
                    _state.update {
                        it.copy(
                            activeWorkout = active?.let { detail ->
                                ActiveWorkoutInfo(
                                    id = detail.workout.id,
                                    name = detail.workout.name,
                                    startedAt = detail.workout.startedAt,
                                    completedSets = detail.exercises.sumOf { ex -> ex.sets.count { s -> s.isCompleted } }
                                )
                            }
                        )
                    }
                }
        }

        viewModelScope.launch {
            combine(
                workoutRepository.observeWorkoutCountSince(weekStart),
                workoutRepository.observeVolumeSince(weekStart),
                workoutRepository.observeWorkoutDates(),
                workoutRepository.observeHistory(1),
                workoutRepository.observeRecentRecords(3)
            ) { count, volume, dates, history, records ->
                _state.value.copy(
                    workoutsThisWeek = count,
                    volumeThisWeekKg = volume,
                    streakWeeks = TrainingMath.weeklyStreak(dates),
                    lastWorkout = history.firstOrNull()?.workout,
                    recentRecords = records
                )
            }
                .catch { e -> _state.update { it.copy(error = e.userMessage("Could not load your training summary")) } }
                .collect { next ->
                    _state.update {
                        it.copy(
                            workoutsThisWeek = next.workoutsThisWeek,
                            volumeThisWeekKg = next.volumeThisWeekKg,
                            streakWeeks = next.streakWeeks,
                            lastWorkout = next.lastWorkout,
                            recentRecords = next.recentRecords
                        )
                    }
                }
        }

        viewModelScope.launch {
            cardioRepository.observeTodaySteps()
                .catch { e -> _state.update { it.copy(error = e.userMessage("Could not load today's steps")) } }
                .collect { steps -> _state.update { it.copy(todaySteps = steps) } }
        }
    }

    /**
     * Listens to the pedometer while the screen is started. Idempotent, so the
     * permission callback and the lifecycle effect can both call it safely.
     */
    fun startSteps() {
        if (!stepCounter.isAvailable() || stepJob?.isActive == true) return
        stepJob = viewModelScope.launch {
            stepCounter.rawCounts()
                .catch { e -> _state.update { it.copy(error = e.userMessage("The step counter stopped responding")) } }
                .collect { raw ->
                    cardioRepository.recordStepReading(raw).onFailure { e ->
                        _state.update { it.copy(error = e.userMessage("Could not save today's steps")) }
                    }
                }
        }
    }

    fun stopSteps() {
        stepJob?.cancel()
        stepJob = null
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    private fun startOfWeekMillis(zone: ZoneId = ZoneId.systemDefault()): Long {
        val today = LocalDate.now(zone)
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        return monday.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    private companion object {
        fun greetingForNow(): String = when (LocalTime.now().hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }
}
