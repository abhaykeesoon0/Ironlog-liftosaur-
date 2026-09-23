package com.ironlog.app.ui.screens.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.data.dao.ExerciseFrequency
import com.ironlog.app.data.dao.ExerciseSessionPoint
import com.ironlog.app.data.dao.RepCount
import com.ironlog.app.data.dao.TrainingTotals
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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

/**
 * Working sets grouped by the rep range they fall in.
 *
 * The boundaries are the ones training is actually programmed around: singles
 * to fives is strength work, six to twelve is where size comes from, and
 * anything past that is endurance and joint work.
 */
data class RepBand(val label: String, val sets: Int, val note: String)

/** One lift's history, for the progression chart and the flow beneath it. */
data class TopLift(
    val exerciseId: String,
    val name: String,
    val sessions: List<ExerciseSessionPoint>
)

/**
 * The period on screen set against the period of equal length before it. Null
 * when there is no earlier period to compare with, which is the honest answer
 * for a user in their first few weeks.
 */
data class PeriodComparison(val now: TrainingTotals, val before: TrainingTotals) {
    val hasBefore: Boolean get() = !before.isEmpty
}

data class ProgressUiState(
    val isLoading: Boolean = true,
    val range: ProgressRange = ProgressRange.TWELVE_WEEKS,
    val unit: UnitSystem = UnitSystem.METRIC,
    val weeks: List<WeekBucket> = emptyList(),
    val muscles: List<MuscleShare> = emptyList(),
    val steps: List<DaySteps> = emptyList(),
    val records: List<RecordWithExercise> = emptyList(),
    val mostUsed: List<ExerciseFrequency> = emptyList(),
    val repBands: List<RepBand> = emptyList(),
    val comparison: PeriodComparison? = null,
    val topLift: TopLift? = null,
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

    /**
     * The distribution of rep ranges, and the same window set against the one
     * before it.
     *
     * Kept in its own flow rather than folded into the one above: combine takes
     * five sources at most, and nesting is clearer than a tuple of nine things.
     */
    private val analytics = range.flatMapLatest { selected ->
        val firstMonday = mondayOf(LocalDate.now(zone)).minusWeeks((selected.weeks - 1).toLong())
        val start = firstMonday.atStartOfDay(zone).toInstant().toEpochMilli()
        val previousStart = firstMonday.minusWeeks(selected.weeks.toLong())
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()

        combine(
            workoutRepository.observeRepCounts(start),
            workoutRepository.observeTotals(start, now),
            workoutRepository.observeTotals(previousStart, start)
        ) { repCounts, current, previous ->
            Analytics(bandsFrom(repCounts), PeriodComparison(current, previous))
        }
    }

    /**
     * The lift the user trains most, with every session of it.
     *
     * One exercise rather than all of them: a progression chart is only worth
     * reading when it is about a lift you actually repeat, and the screen links
     * through to any other exercise for the rest.
     */
    private val topLiftFlow = exerciseRepository.observeMostUsed(1).flatMapLatest { frequencies ->
        val top = frequencies.firstOrNull() ?: return@flatMapLatest flowOf(null)
        exerciseRepository.observeHistory(top.exerciseId).map { sessions ->
            TopLift(top.exerciseId, top.name, sessions)
        }
    }

    private val base: Flow<ProgressUiState> = combine(
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

    val state: StateFlow<ProgressUiState> = combine(base, analytics, topLiftFlow) { state, extra, topLift ->
        state.copy(
            repBands = extra.bands,
            comparison = extra.comparison,
            topLift = topLift
        )
    }
        .catch { e -> emit(ProgressUiState(isLoading = false, error = e.userMessage("Could not load your progress"))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())

    fun selectRange(value: ProgressRange) {
        range.value = value
    }

    private fun mondayOf(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())

    private data class Analytics(val bands: List<RepBand>, val comparison: PeriodComparison)

    /** Sets counted into the rep ranges training is programmed around. */
    private fun bandsFrom(counts: List<RepCount>): List<RepBand> {
        fun setsIn(range: IntRange) = counts.filter { it.reps in range }.sumOf { it.sets }
        val bands = listOf(
            RepBand("1-5", setsIn(1..5), "Strength"),
            RepBand("6-8", setsIn(6..8), "Strength and size"),
            RepBand("9-12", setsIn(9..12), "Size"),
            RepBand("13-20", setsIn(13..20), "Size and endurance"),
            RepBand("20+", setsIn(21..Int.MAX_VALUE), "Endurance")
        )
        // Nothing logged means no distribution, not a row of empty bars.
        return if (bands.sumOf { it.sets } == 0) emptyList() else bands
    }
}
