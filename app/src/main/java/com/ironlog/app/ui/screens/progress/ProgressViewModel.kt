package com.ironlog.app.ui.screens.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.data.dao.ExerciseFrequency
import com.ironlog.app.data.dao.RecordWithExercise
import com.ironlog.app.data.entity.MuscleGroup
import com.ironlog.app.data.entity.UnitSystem
import com.ironlog.app.data.repository.CardioRepository
import com.ironlog.app.data.repository.ExerciseRepository
import com.ironlog.app.data.repository.ProfileRepository
import com.ironlog.app.data.repository.WorkoutRepository
import com.ironlog.app.data.repository.userMessage
import com.ironlog.app.ui.format.formatShortDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class ProgressRange(val label: String, val weeks: Int) {
    FOUR_WEEKS("4 weeks", 4),
    TWELVE_WEEKS("12 weeks", 12),
    SIX_MONTHS("6 months", 26)
}

data class WeekBucket(val label: String, val volumeKg: Double, val workouts: Int)
data class MuscleShare(val label: String, val volumeKg: Double, val sets: Int)
data class DaySteps(val label: String, val steps: Int)

data class ProgressUiState(
    val isLoading: Boolean = true,
    val range: ProgressRange = ProgressRange.TWELVE_WEEKS,
    val unit: UnitSystem = UnitSystem.METRIC,
    val weeks: List<WeekBucket> = emptyList(),
    val muscles: List<MuscleShare> = emptyList(),
    val steps: List<DaySteps> = emptyList(),
    val records: List<RecordWithExercise> = emptyList(),
    val mostUsed: List<ExerciseFrequency> = emptyList(),
    val error: String? = null
) {
    val totalVolumeKg: Double get() = weeks.sumOf { it.volumeKg }
    val totalWorkouts: Int get() = weeks.sumOf { it.workouts }
    val hasTraining: Boolean get() = totalWorkouts > 0
}

private data class RangeData(
    val range: ProgressRange,
    val weeks: List<WeekBucket>,
    val muscles: List<MuscleShare>
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    exerciseRepository: ExerciseRepository,
    cardioRepository: CardioRepository,
    profileRepository: ProfileRepository
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val range = MutableStateFlow(ProgressRange.TWELVE_WEEKS)

    private val rangeData = range.flatMapLatest { selected ->
        val firstMonday = mondayOf(LocalDate.now(zone)).minusWeeks((selected.weeks - 1).toLong())
        val since = firstMonday.atStartOfDay(zone).toInstant().toEpochMilli()

        combine(
            workoutRepository.observeDailyVolume(since),
            workoutRepository.observeWorkoutDates(),
            workoutRepository.observeMuscleSplit(since)
        ) { daily, dates, split ->
            val mondays = (0 until selected.weeks).map { firstMonday.plusWeeks(it.toLong()) }

            // Daily buckets are whole days since the epoch; each is folded into
            // the Monday of its week so the chart has one bar per training week.
            val volumeByWeek = daily.groupBy { mondayOf(LocalDate.ofEpochDay(it.bucket)) }
                .mapValues { (_, points) -> points.sumOf { it.volume } }
            val workoutsByWeek = dates
                .filter { it >= since }
                .groupingBy { mondayOf(Instant.ofEpochMilli(it).atZone(zone).toLocalDate()) }
                .eachCount()

            RangeData(
                range = selected,
                weeks = mondays.map { monday ->
                    WeekBucket(
                        label = formatShortDate(monday.atStartOfDay(zone).toInstant().toEpochMilli()),
                        volumeKg = volumeByWeek[monday] ?: 0.0,
                        workouts = workoutsByWeek[monday] ?: 0
                    )
                },
                muscles = split
                    .filter { it.sets > 0 }
                    .map { m ->
                        MuscleShare(
                            label = MuscleGroup.entries.firstOrNull { it.name == m.primaryMuscle }?.label
                                ?: m.primaryMuscle,
                            volumeKg = m.volume,
                            sets = m.sets
                        )
                    }
            )
        }
    }

    private val stepsFlow = cardioRepository.observeStepsSince(6)

    val state: StateFlow<ProgressUiState> = combine(
        rangeData,
        profileRepository.observeUser(),
        stepsFlow,
        workoutRepository.observeRecentRecords(5),
        exerciseRepository.observeMostUsed(5)
    ) { data, user, activity, records, mostUsed ->
        val today = LocalDate.now(zone)
        val byDay = activity.associate { it.dateEpochDay to it.steps }
        ProgressUiState(
            isLoading = false,
            range = data.range,
            unit = user?.unitSystem ?: UnitSystem.METRIC,
            weeks = data.weeks,
            muscles = data.muscles,
            steps = (6 downTo 0).map { back ->
                val day = today.minusDays(back.toLong())
                DaySteps(
                    label = if (back == 0) "Today" else day.dayOfWeek.name.take(3).lowercase()
                        .replaceFirstChar { it.uppercase() },
                    steps = byDay[day.toEpochDay()] ?: 0
                )
            },
            records = records,
            mostUsed = mostUsed
        )
    }
        .catch { e -> emit(ProgressUiState(isLoading = false, error = e.userMessage("Could not load your progress"))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())

    fun selectRange(value: ProgressRange) {
        range.value = value
    }

    private fun mondayOf(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())
}
