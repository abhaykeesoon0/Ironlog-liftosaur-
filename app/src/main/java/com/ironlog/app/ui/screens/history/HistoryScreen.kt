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
                val volumeByDay = workouts
                    .groupBy { Instant.ofEpochMilli(it.workout.startedAt).atZone(zone).dayOfMonth }
                    .mapValues { (_, dayWorkouts) -> dayWorkouts.sumOf { it.workout.totalVolumeKg } }
                MonthGroup(
                    label = "${monthFormat.format(month)} · ${workouts.size}",
                    entries = workouts.map { detail ->
                        HistoryEntry(detail, achievements[detail.workout.id].orEmpty())
                    },
                    days = (1..month.lengthOfMonth()).map { day ->
                        HeatmapDay(day, volumeByDay[day] ?: 0.0)
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
                            MonthHeatmap(days = month.days, firstDayOffset = month.firstDayOffset)
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
