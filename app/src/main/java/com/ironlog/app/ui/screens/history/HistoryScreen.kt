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
import com.ironlog.app.ui.components.EmptyState
import com.ironlog.app.ui.components.ErrorBanner
import com.ironlog.app.ui.components.IronCard
import com.ironlog.app.ui.components.LoadingBlock
import com.ironlog.app.ui.components.ScreenTitle
import com.ironlog.app.ui.components.StatTile
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

data class MonthGroup(val label: String, val workouts: List<WorkoutWithDetail>)

data class HistoryUiState(
    val isLoading: Boolean = true,
    val unit: UnitSystem = UnitSystem.METRIC,
    val months: List<MonthGroup> = emptyList(),
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
        // History arrives newest first and groupBy keeps encounter order, so the
        // months come out newest first without a second sort.
        val months = history
            .groupBy { YearMonth.from(Instant.ofEpochMilli(it.workout.startedAt).atZone(zone)) }
            .map { (month, workouts) ->
                MonthGroup("${monthFormat.format(month)} · ${workouts.size}", workouts)
            }
        HistoryUiState(
            isLoading = false,
            unit = user?.unitSystem ?: UnitSystem.METRIC,
            months = months
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
            else -> state.months.forEach { month ->
                item(key = "header-${month.label}") {
                    Text(
                        month.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = ChalkDim,
                        modifier = Modifier
                            .padding(top = Space.md)
                            .semantics { heading() }
                    )
                }
                items(month.workouts, key = { it.workout.id }) { detail ->
                    WorkoutHistoryCard(detail, state.unit) { onOpenWorkout(detail.workout.id) }
                }
            }
        }
        item { Spacer(Modifier.height(Space.xl)) }
    }
}

@Composable
private fun WorkoutHistoryCard(detail: WorkoutWithDetail, unit: UnitSystem, onClick: () -> Unit) {
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
            val names = detail.exercises
                .sortedBy { it.workoutExercise.orderIndex }
                .joinToString(" · ") { "${it.sets.size} × ${it.exercise.name}" }
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
