package com.ironlog.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.core.sensors.HealthConnectSteps
import com.ironlog.app.core.sensors.StepCounter
import com.ironlog.app.data.dao.RecordWithExercise
import com.ironlog.app.data.entity.StepSource
import com.ironlog.app.data.entity.UnitSystem
import com.ironlog.app.data.entity.WorkoutEntity
import com.ironlog.app.data.repository.BodyRepository
import com.ironlog.app.data.repository.CardioRepository
import com.ironlog.app.data.repository.EquipmentSettings
import com.ironlog.app.data.repository.StrengthGoalRepository
import com.ironlog.app.data.repository.ProfileRepository
import com.ironlog.app.data.repository.WorkoutRepository
import com.ironlog.app.data.repository.userMessage
import com.ironlog.app.domain.usecase.Recovery
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
import com.ironlog.app.core.sensors.HealthConnectWriter
import com.ironlog.app.data.entity.MuscleGroup
import com.ironlog.app.data.entity.MuscleTint
import com.ironlog.app.domain.usecase.MuscleParts

data class ActiveWorkoutInfo(val id: String, val name: String, val startedAt: Long, val completedSets: Int)

/** One weigh-in, oldest first, for the bodyweight line on Home. */
data class BodyweightPoint(val takenAt: Long, val weightKg: Double)

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
    val stepSource: StepSource = StepSource.PHONE,
    /** Health Connect is installed and has given us the steps permission. */
    val healthConnectReady: Boolean = false,
    val todaySteps: Int = 0,
    val lastWorkout: WorkoutEntity? = null,
    val recentBlock: TrainingMath.BlockSummary = TrainingMath.BlockSummary(),
    val bodyweight: List<BodyweightPoint> = emptyList(),
    val recovery: List<Recovery.MuscleState> = emptyList(),
    val equipment: EquipmentSettings = EquipmentSettings(),
    val recentRecords: List<RecordWithExercise> = emptyList(),
    /** The last four weeks of work by muscle part, newest first, for the muscle map. */
    val muscleWeeks: List<MuscleParts.Week> = emptyList(),
    val muscleTint: MuscleTint = MuscleTint.PLUM,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val profileRepository: ProfileRepository,
    private val cardioRepository: CardioRepository,
    private val bodyRepository: BodyRepository,
    private val goalRepository: StrengthGoalRepository,
    private val stepCounter: StepCounter,
    private val healthConnectSteps: HealthConnectSteps,
    private val healthConnectWriter: HealthConnectWriter
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
                workoutRepository.observeHistory(RECENT_WINDOW),
                workoutRepository.observeRecentRecords(3)
            ) { count, volume, dates, history, records ->
                _state.value.copy(
                    workoutsThisWeek = count,
                    volumeThisWeekKg = volume,
                    streakWeeks = TrainingMath.weeklyStreak(dates),
                    lastWorkout = history.firstOrNull()?.workout,
                    recentBlock = TrainingMath.summarise(history.map { detail -> detail.workout }),
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
                            recentBlock = next.recentBlock,
                            recentRecords = next.recentRecords
                        )
                    }
                }
        }

        viewModelScope.launch {
            bodyRepository.observeMeasurements()
                .catch { e -> _state.update { it.copy(error = e.userMessage("Could not load your bodyweight history")) } }
                .collect { measurements ->
                    val points = measurements
                        .mapNotNull { m -> m.weightKg?.let { BodyweightPoint(m.takenAt, it) } }
                        .sortedBy { it.takenAt }
                    _state.update { it.copy(bodyweight = points) }
                }
        }

        viewModelScope.launch {
            profileRepository.stepSource
                .catch { e -> _state.update { it.copy(error = e.userMessage("Could not read your step settings")) } }
                .collect { source ->
                    _state.update { it.copy(stepSource = source) }
                    if (source == StepSource.HEALTH_CONNECT) refreshHealthConnectSteps()
                }
        }

        viewModelScope.launch {
            profileRepository.equipment
                .catch { e -> _state.update { it.copy(error = e.userMessage("Could not load your equipment settings")) } }
                .collect { equipment -> _state.update { it.copy(equipment = equipment) } }
        }

        viewModelScope.launch {
            val since = System.currentTimeMillis() - MUSCLE_MAP_WINDOW_MS
            combine(workoutRepository.observeExerciseWork(since), profileRepository.muscleTint) { rows, tint -> rows to tint }
                .catch { e -> _state.update { it.copy(error = e.userMessage("Could not map your muscles")) } }
                .collect { (rows, tint) ->
                    val work = rows.map { row ->
                        MuscleParts.Work(
                            exerciseName = row.exerciseName,
                            primary = row.primaryMuscle,
                            secondary = row.secondaryMuscles.split(',')
                                .mapNotNull { name -> runCatching { MuscleGroup.valueOf(name.trim()) }.getOrNull() },
                            startedAt = row.startedAt,
                            sets = row.sets,
                            volumeKg = row.volume
                        )
                    }
                    _state.update { it.copy(muscleWeeks = MuscleParts.weekly(work), muscleTint = tint) }
                }
        }

        viewModelScope.launch {
            goalRepository.observeMuscleSessions()
                .catch { e -> _state.update { it.copy(error = e.userMessage("Could not work out your recovery")) } }
                .collect { rows ->
                    val sessions = rows.map { row ->
                        Recovery.MuscleSession(row.muscle, row.trainedAt, row.volume)
                    }
                    // The clock is read here rather than inside the calculation so
                    // the maths stays a pure function of its inputs.
                    _state.update { it.copy(recovery = Recovery.states(sessions, System.currentTimeMillis())) }
                }
        }

        viewModelScope.launch {
            cardioRepository.observeTodaySteps()
                .catch { e -> _state.update { it.copy(error = e.userMessage("Could not load today's steps")) } }
                .collect { steps -> _state.update { it.copy(todaySteps = steps) } }
        }
    }

    /**
     * Pulls today's total from Health Connect.
     *
     * A read rather than a subscription: Health Connect has no change stream,
     * and a watch syncs in batches anyway, so polling when the screen appears
     * is both simpler and closer to how the data actually arrives.
     */
    fun refreshHealthConnectSteps() {
        viewModelScope.launch {
            val granted = healthConnectSteps.hasPermission()
            val steps = if (granted) healthConnectSteps.stepsToday() else null
            _state.update {
                it.copy(
                    healthConnectReady = granted,
                    // Keep the last good figure if this read failed: a blank
                    // where a number was is read as "you did nothing today".
                    todaySteps = steps?.toInt() ?: it.todaySteps
                )
            }
        }
    }

    fun healthConnectAvailability() = healthConnectSteps.availability()

    /** Read and write together, in one request, so the user is asked once. */
    fun healthConnectPermissions() = healthConnectSteps.permissions + healthConnectWriter.permissions

    /**
     * Listens to the pedometer while the screen is started. Idempotent, so the
     * permission callback and the lifecycle effect can both call it safely.
     */
    fun startSteps() {
        if (_state.value.stepSource != StepSource.PHONE) return
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

    companion object {
        /**
         * How many recent sessions the Home summary covers. Two weeks of
         * training for most people, which is long enough to smooth out a single
         * heavy or missed day without reaching back into a different training
         * block.
         */
        const val RECENT_WINDOW = 14

        private fun greetingForNow(): String = when (LocalTime.now().hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }
}

/** Four weeks: the map's chips reach back that far. */
private const val MUSCLE_MAP_WINDOW_MS = 28L * 86_400_000L
