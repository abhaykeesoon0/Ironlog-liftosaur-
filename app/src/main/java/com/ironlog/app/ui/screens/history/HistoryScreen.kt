package com.ironlog.app.ui.screens.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ironlog.app.data.dao.WorkoutWithDetail
import com.ironlog.app.data.entity.UnitSystem
import com.ironlog.app.data.repository.ProfileRepository
import com.ironlog.app.data.repository.WorkoutRepository
import com.ironlog.app.data.repository.userMessage
import com.ironlog.app.domain.usecase.Gamification
import com.ironlog.app.domain.usecase.TrainingMath
import com.ironlog.app.ui.components.AchievementRow
import com.ironlog.app.ui.components.EmptyState
import com.ironlog.app.ui.components.ErrorBanner
import com.ironlog.app.ui.components.HeatmapDay
import com.ironlog.app.ui.components.IronCard
import com.ironlog.app.ui.components.LevelCard
import com.ironlog.app.ui.components.LoadingBlock
import com.ironlog.app.ui.components.MonthHeatmap
import com.ironlog.app.ui.components.ScreenTitle
import com.ironlog.app.ui.components.StatTile
import com.ironlog.app.ui.components.StreakCard
import com.ironlog.app.ui.format.formatDuration
import com.ironlog.app.ui.format.formatRelativeDay
import com.ironlog.app.ui.format.formatTime
import com.ironlog.app.ui.format.formatVolume
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.ChalkFaint
import com.ironlog.app.ui.theme.PrGold
import com.ironlog.app.ui.theme.Space
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import com.ironlog.app.data.entity.SetType
import com.ironlog.app.data.entity.WorkoutSetEntity
import com.ironlog.app.ui.components.Hairline
import com.ironlog.app.ui.components.PrBadge
import com.ironlog.app.ui.format.formatWeight
import com.ironlog.app.ui.theme.NumericType
import com.ironlog.app.ui.theme.Slate800

/** A finished workout together with what that session earned. */
data class HistoryEntry(
    val detail: WorkoutWithDetail,
    val achievements: List<Gamification.Achievement>
)

data class MonthGroup(
    val label: String,
    val entries: List<HistoryEntry>,
    val days: List<HeatmapDay>,
    /** Blank cells before the 1st, so the grid lines up under its weekday column. */
    val firstDayOffset: Int
)

data class HistoryUiState(
    val isLoading: Boolean = true,
    val unit: UnitSystem = UnitSystem.METRIC,
    val months: List<MonthGroup> = emptyList(),
    val level: Gamification.LevelProgress = Gamification.levelFor(0),
    val streakWeeks: Int = 0,
    val finishedCount: Int = 0,
    val nextMilestone: Gamification.Milestone? = null,
    val error: String? = null
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    workoutRepository: WorkoutRepository,
    profileRepository: ProfileRepository
) : ViewModel() {

    private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

    val state: StateFlow<HistoryUiState> = combine(
        workoutRepository.observeHistory(),
        profileRepository.observeUser()
    ) { history, user ->
        val zone = ZoneId.systemDefault()

        // Progression is derived from the same history the screen already shows,
        // so there is no XP column to migrate and nothing that can fall out of
        // step with the workouts themselves. The ceiling is the repository's
        // history limit, which is far beyond what a coursework log reaches.
        val ascending = history.map { it.workout }.asReversed()
        val achievements = ascending.mapIndexed { index, workout ->
            workout.id to Gamification.achievementsFor(workout, ascending.subList(0, index))
        }.toMap()

        val months = history
            // History arrives newest first and groupBy keeps encounter order, so the
            // months come out newest first without a second sort.
            .groupBy { YearMonth.from(Instant.ofEpochMilli(it.workout.startedAt).atZone(zone)) }
            .map { (month, workouts) ->
                val byDay = workouts
                    .groupBy { Instant.ofEpochMilli(it.workout.startedAt).atZone(zone).dayOfMonth }
                MonthGroup(
                    label = "${monthFormat.format(month)} · ${workouts.size}",
                    entries = workouts.map { detail ->
                        HistoryEntry(detail, achievements[detail.workout.id].orEmpty())
                    },
                    days = (1..month.lengthOfMonth()).map { day ->
                        val trained = byDay[day].orEmpty()
                        HeatmapDay(
                            dayOfMonth = day,
                            volumeKg = trained.sumOf { it.workout.totalVolumeKg },
                            workouts = trained.size
                        )
                    },
                    // DayOfWeek counts Monday as 1, and the grid starts on Monday.
                    firstDayOffset = month.atDay(1).dayOfWeek.value - 1
                )
            }

        HistoryUiState(
            isLoading = false,
            unit = user?.unitSystem ?: UnitSystem.METRIC,
            months = months,
            level = Gamification.levelFor(Gamification.totalXp(ascending)),
            streakWeeks = TrainingMath.weeklyStreak(ascending.map { it.startedAt }),
            finishedCount = ascending.size,
            nextMilestone = Gamification.nextMilestone(ascending.size)
        )
    }
        .catch { e -> emit(HistoryUiState(isLoading = false, error = e.userMessage("Could not load your history"))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())
}

@Composable
fun HistoryScreen(
    onOpenWorkout: (String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    /** The calendar day whose workouts are open in the sheet, if any. */
    var openDay by remember { mutableStateOf<OpenDay?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.md)
    ) {
        item { ScreenTitle("History") }
        item { ErrorBanner(state.error) }

        when {
            state.isLoading -> item { LoadingBlock() }
            state.months.isEmpty() && state.error == null -> item {
                EmptyState(
                    icon = Icons.Default.History,
                    title = "No workouts yet",
                    body = "Finished workouts appear here with their sets, volume and any records you set."
                )
            }
            else -> {
                item(key = "level") { LevelCard(state.level) }
                item(key = "streak") {
                    StreakCard(
                        streakWeeks = state.streakWeeks,
                        finishedCount = state.finishedCount,
                        nextMilestone = state.nextMilestone
                    )
                }

                state.months.forEach { month ->
                    item(key = "header-${month.label}") {
                        Column(Modifier.padding(top = Space.md)) {
                            Text(
                                month.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = ChalkDim,
                                modifier = Modifier.semantics { heading() }
                            )
                            Spacer(Modifier.height(Space.md))
                            MonthHeatmap(
                                days = month.days,
                                firstDayOffset = month.firstDayOffset,
                                onDayClick = { day -> openDay = OpenDay(month, day.dayOfMonth) }
                            )
                        }
                    }
                    items(month.entries, key = { it.detail.workout.id }) { entry ->
                        WorkoutHistoryCard(entry, state.unit) { onOpenWorkout(entry.detail.workout.id) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(Space.xl)) }
    }

    openDay?.let { day ->
        DaySheet(
            day = day,
            unit = state.unit,
            onOpenWorkout = { id ->
                openDay = null
                onOpenWorkout(id)
            },
            onDismiss = { openDay = null }
        )
    }
}

/** A day pressed on the calendar: which month, and which date within it. */
private data class OpenDay(val month: MonthGroup, val dayOfMonth: Int)

/**
 * Everything trained on one day, opened from the calendar.
 *
 * The full log rather than a summary: the name, the time, the totals, and
 * every set of every exercise with its weight and reps. This is the page of
 * the notebook for that day, and the point of pressing the day is to read it
 * without scrolling the month for the card. Warm-ups are shown but marked, so
 * the sets on screen still add up to the working-set count in the totals.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DaySheet(
    day: OpenDay,
    unit: UnitSystem,
    onOpenWorkout: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val zone = ZoneId.systemDefault()
    val entries = day.month.entries.filter {
        Instant.ofEpochMilli(it.detail.workout.startedAt).atZone(zone).dayOfMonth == day.dayOfMonth
    }
    val title = entries.firstOrNull()?.let {
        DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())
            .format(Instant.ofEpochMilli(it.detail.workout.startedAt).atZone(zone))
    } ?: "Day ${day.dayOfMonth}"
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Slate800) {
        LazyColumn(
            contentPadding = PaddingValues(start = Space.lg, end = Space.lg, bottom = Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            item {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Chalk)
                Text(
                    if (entries.size == 1) "1 workout" else "${entries.size} workouts",
                    style = MaterialTheme.typography.bodySmall,
                    color = ChalkFaint
                )
            }
            items(entries, key = { it.detail.workout.id }) { entry ->
                DayWorkout(entry, unit) { onOpenWorkout(entry.detail.workout.id) }
            }
        }
    }
}

@Composable
private fun DayWorkout(entry: HistoryEntry, unit: UnitSystem, onOpen: () -> Unit) {
    val detail = entry.detail
    val w = detail.workout
    IronCard(onClick = onOpen) {
        Column {
            Text(w.name, style = MaterialTheme.typography.titleMedium, color = Chalk)
            Text(
                "${formatTime(w.startedAt)} · ${formatDuration(w.durationSeconds)}",
                style = MaterialTheme.typography.bodySmall,
                color = ChalkFaint
            )
            Spacer(Modifier.height(Space.md))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatTile(unit.formatVolume(w.totalVolumeKg), "Volume")
                StatTile(w.totalSets.toString(), "Sets")
                StatTile(w.prCount.toString(), "PRs", accent = if (w.prCount > 0) PrGold else Chalk)
            }
            if (entry.achievements.isNotEmpty()) {
                Spacer(Modifier.height(Space.md))
                AchievementRow(entry.achievements)
            }

            detail.exercises.sortedBy { it.workoutExercise.orderIndex }.forEach { exercise ->
                val done = exercise.sets.filter { it.isCompleted }.sortedBy { it.setNumber }
                if (done.isEmpty()) return@forEach
                Spacer(Modifier.height(Space.md))
                Hairline()
                Spacer(Modifier.height(Space.sm))
                Text(exercise.exercise.name, style = MaterialTheme.typography.bodyLarge, color = Chalk)
                Spacer(Modifier.height(Space.xs))
                done.forEach { set -> SetLine(set, unit) }
            }

            Spacer(Modifier.height(Space.md))
            Text("Open the full workout", style = MaterialTheme.typography.labelLarge, color = ChalkDim)
        }
    }
}

/**
 * One set as a lifter writes it down: the weight, a cross, the reps. A warm-up
 * carries its label so it is not mistaken for work, and a record carries the
 * badge that earned it.
 */
@Composable
private fun SetLine(set: WorkoutSetEntity, unit: UnitSystem) {
    val load = when {
        set.weightKg != null && set.reps != null -> unit.formatWeight(set.weightKg) + " \u00d7 " + set.reps
        set.reps != null -> "${set.reps} reps"
        set.durationSeconds != null -> formatDuration(set.durationSeconds)
        else -> "\u2014"
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            set.setNumber.toString(),
            style = NumericType.small,
            color = ChalkFaint,
            modifier = Modifier.width(24.dp)
        )
        Text(load, style = NumericType.setCell.copy(textAlign = TextAlign.Start), color = Chalk)
        if (set.type != SetType.NORMAL) {
            Spacer(Modifier.width(Space.sm))
            Text(set.type.label, style = MaterialTheme.typography.labelSmall, color = ChalkFaint)
        }
        set.prType?.let {
            Spacer(Modifier.width(Space.sm))
            PrBadge(it)
        }
    }
}

@Composable
private fun WorkoutHistoryCard(entry: HistoryEntry, unit: UnitSystem, onClick: () -> Unit) {
    val detail = entry.detail
    val w = detail.workout
    IronCard(onClick = onClick) {
        Column {
            Text(w.name, style = MaterialTheme.typography.titleMedium, color = Chalk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${formatRelativeDay(w.startedAt)} · ${formatTime(w.startedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = ChalkFaint
            )
            Spacer(Modifier.height(Space.md))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatTile(formatDuration(w.durationSeconds), "Duration")
                StatTile(unit.formatVolume(w.totalVolumeKg), "Volume")
                StatTile(w.totalSets.toString(), "Sets")
                StatTile(w.prCount.toString(), "PRs", accent = if (w.prCount > 0) PrGold else Chalk)
            }
            if (entry.achievements.isNotEmpty()) {
                Spacer(Modifier.height(Space.md))
                AchievementRow(entry.achievements)
            }
            // Warm-ups are left out so this line adds up to the Sets tile above it.
            val names = detail.exercises
                .sortedBy { it.workoutExercise.orderIndex }
                .mapNotNull { exercise ->
                    val working = TrainingMath.workingSetCount(exercise.sets)
                    if (working > 0) "$working × ${exercise.exercise.name}" else null
                }
                .joinToString(" · ")
            if (names.isNotBlank()) {
                Spacer(Modifier.height(Space.md))
                Text(
                    names,
                    style = MaterialTheme.typography.bodySmall,
                    color = ChalkDim,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
