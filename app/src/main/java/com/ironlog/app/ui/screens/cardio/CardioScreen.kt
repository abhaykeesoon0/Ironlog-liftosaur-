package com.ironlog.app.ui.screens.cardio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ironlog.app.core.location.CardioProgress
import com.ironlog.app.core.location.CardioTrackingService
import com.ironlog.app.data.entity.CardioSessionEntity
import com.ironlog.app.data.entity.UnitSystem
import com.ironlog.app.data.repository.CardioRepository
import com.ironlog.app.data.repository.ProfileRepository
import com.ironlog.app.ui.components.EmptyState
import com.ironlog.app.ui.components.IronCard
import com.ironlog.app.ui.components.IronTopBar
import com.ironlog.app.ui.components.LoadingBlock
import com.ironlog.app.ui.components.PrimaryButton
import com.ironlog.app.ui.components.SectionHeader
import com.ironlog.app.ui.components.StatTile
import com.ironlog.app.ui.format.formatClock
import com.ironlog.app.ui.format.formatDistance
import com.ironlog.app.ui.format.formatDuration
import com.ironlog.app.ui.format.formatPace
import com.ironlog.app.ui.format.formatRelativeDay
import com.ironlog.app.ui.format.formatTime
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.NumericType
import com.ironlog.app.ui.theme.Space
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class CardioListState(
    val unit: UnitSystem = UnitSystem.METRIC,
    val sessions: List<CardioSessionEntity> = emptyList(),
    val isLoading: Boolean = true
) {
    val totalDistanceMeters: Double get() = sessions.sumOf { it.distanceMeters }
    val totalSeconds: Int get() = sessions.sumOf { it.durationSeconds }
}

@HiltViewModel
class CardioListViewModel @Inject constructor(
    cardioRepository: CardioRepository,
    profileRepository: ProfileRepository
) : ViewModel() {

    val state: StateFlow<CardioListState> = combine(
        profileRepository.observeUser(),
        cardioRepository.observeSessions()
    ) { user, sessions ->
        // The in-progress row (no finish time) is shown by the live banner instead.
        CardioListState(user?.unitSystem ?: UnitSystem.METRIC, sessions.filter { it.finishedAt != null }, false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CardioListState())

    val live: StateFlow<CardioProgress> = CardioTrackingService.progress
}

@Composable
fun CardioScreen(
    onBack: () -> Unit,
    onStartTracking: () -> Unit,
    onOpenSession: (String) -> Unit,
    viewModel: CardioListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val live by viewModel.live.collectAsStateWithLifecycle()
    val unit = state.unit

    Column(Modifier.fillMaxSize()) {
        IronTopBar(title = "Outdoor cardio", onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            item {
                if (live.isTracking) {
                    IronCard(onClick = onStartTracking) {
                        Column {
                            Text(
                                if (live.isPaused) "Session paused" else "Recording now",
                                style = MaterialTheme.typography.titleMedium,
                                color = Chalk
                            )
                            Text(
                                "${unit.formatDistance(live.distanceMeters)} · ${formatClock(live.elapsedSeconds)} — tap to return",
                                style = NumericType.small,
                                color = ChalkDim
                            )
                        }
                    }
                } else {
                    PrimaryButton(
                        text = "Start outdoor session",
                        onClick = onStartTracking,
                        leadingIcon = Icons.Default.PlayArrow
                    )
                }
            }

            when {
                state.isLoading -> item { LoadingBlock() }
                state.sessions.isEmpty() -> item {
                    EmptyState(
                        icon = Icons.AutoMirrored.Filled.DirectionsRun,
                        title = "No sessions yet",
                        body = "Record a run, walk, ride or hike with GPS. Your route stays on this phone."
                    )
                }
                else -> {
                    item {
                        IronCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                StatTile(state.sessions.size.toString(), "Sessions")
                                StatTile(unit.formatDistance(state.totalDistanceMeters), "Distance")
                                StatTile(formatDuration(state.totalSeconds), "Time")
                            }
                        }
                    }
                    item { SectionHeader("History") }
                    items(state.sessions, key = { it.id }) { session ->
                        SessionRow(session, unit, onClick = { onOpenSession(session.id) })
                    }
                }
            }
            item { Spacer(Modifier.height(Space.section)) }
        }
    }
}

@Composable
private fun SessionRow(session: CardioSessionEntity, unit: UnitSystem, onClick: () -> Unit) {
    IronCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${session.type.label} · ${formatRelativeDay(session.startedAt)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Chalk
                )
                Text(
                    "${formatTime(session.startedAt)} · ${formatClock(session.durationSeconds)} · ${unit.formatPace(session.avgPaceSecondsPerKm)}",
                    style = NumericType.small,
                    color = ChalkDim
                )
            }
            Text(unit.formatDistance(session.distanceMeters), style = NumericType.stat, color = Chalk)
        }
    }
}
