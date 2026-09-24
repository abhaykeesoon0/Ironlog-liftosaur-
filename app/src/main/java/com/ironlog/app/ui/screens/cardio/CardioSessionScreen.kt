package com.ironlog.app.ui.screens.cardio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ironlog.app.core.location.GeoMath
import com.ironlog.app.data.entity.CardioSessionEntity
import com.ironlog.app.data.entity.UnitSystem
import com.ironlog.app.data.repository.CardioRepository
import com.ironlog.app.data.repository.ProfileRepository
import com.ironlog.app.data.repository.userMessage
import com.ironlog.app.ui.components.Caption
import com.ironlog.app.ui.components.ConfirmDialog
import com.ironlog.app.ui.components.EmptyState
import com.ironlog.app.ui.components.ErrorBanner
import com.ironlog.app.ui.components.IronCard
import com.ironlog.app.ui.components.IronTopBar
import com.ironlog.app.ui.components.LoadingBlock
import com.ironlog.app.ui.components.RouteShape
import com.ironlog.app.ui.components.StatTile
import com.ironlog.app.ui.format.formatClock
import com.ironlog.app.ui.format.formatDistance
import com.ironlog.app.ui.format.formatFullDate
import com.ironlog.app.ui.format.formatPace
import com.ironlog.app.ui.format.formatTime
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.NumericType
import com.ironlog.app.ui.theme.Radius
import com.ironlog.app.ui.theme.Slate800
import com.ironlog.app.ui.theme.Space
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

data class SessionDetail(
    val session: CardioSessionEntity? = null,
    val route: List<Pair<Float, Float>> = emptyList(),
    val unit: UnitSystem = UnitSystem.METRIC,
    val isLoading: Boolean = true
)

@HiltViewModel
class CardioSessionViewModel @Inject constructor(
    private val cardioRepository: CardioRepository,
    profileRepository: ProfileRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val sessionId: String = checkNotNull(savedStateHandle["id"]) { "Missing session id" }

    val detail: StateFlow<SessionDetail> = combine(
        cardioRepository.observeSession(sessionId),
        profileRepository.observeUser()
    ) { session, user ->
        SessionDetail(
            session = session,
            route = session?.let { GeoMath.normalise(cardioRepository.decodeRoute(it)) }.orEmpty(),
            unit = user?.unitSystem ?: UnitSystem.METRIC,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionDetail())

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun delete() {
        viewModelScope.launch {
            cardioRepository.delete(sessionId)
                .onSuccess { _deleted.value = true }
                .onFailure { e -> _error.value = e.userMessage("Could not delete the session") }
        }
    }
}

@Composable
fun CardioSessionScreen(
    onBack: () -> Unit,
    viewModel: CardioSessionViewModel = hiltViewModel()
) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(deleted) { if (deleted) onBack() }

    val session = detail.session
    val unit = detail.unit

    Column(Modifier.fillMaxSize()) {
        IronTopBar(title = session?.type?.label ?: "Session", onBack = onBack) {
            if (session != null) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete session", tint = Chalk)
                }
            }
        }

        when {
            detail.isLoading -> LoadingBlock(Modifier.fillMaxSize())
            session == null -> EmptyState(
                icon = Icons.AutoMirrored.Filled.DirectionsRun,
                title = "Session not found",
                body = "It may have been deleted.",
                actionLabel = "Back",
                onAction = onBack
            )
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.lg)
            ) {
                ErrorBanner(error)
                Text(
                    "${formatFullDate(session.startedAt)} · ${formatTime(session.startedAt)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ChalkDim
                )
                Spacer(Modifier.height(Space.lg))
                Text(unit.formatDistance(session.distanceMeters), style = NumericType.hero, color = Chalk)
                Caption("Distance")
                Spacer(Modifier.height(Space.lg))

                androidx.compose.foundation.layout.Box(
                    Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .background(Slate800, RoundedCornerShape(Radius.card)),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    if (detail.route.size >= 2) {
                        RouteShape(detail.route, Modifier.fillMaxSize())
                    } else {
                        Caption("No GPS route was recorded for this session")
                    }
                }
                Spacer(Modifier.height(Space.lg))

                IronCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatTile(formatClock(session.durationSeconds), "Duration", Modifier.weight(1f))
                            StatTile(unit.formatPace(session.avgPaceSecondsPerKm), "Avg pace", Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatTile(elevation(session.elevationGainMeters, unit), "Elevation gain", Modifier.weight(1f))
                            StatTile(if (session.steps > 0) "%,d".format(session.steps) else "—", "Steps", Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth()) {
                            StatTile("${session.estimatedCalories} kcal", "Est. calories", Modifier.weight(1f))
                            if (session.avgHeartRate != null) {
                                StatTile("${session.avgHeartRate} bpm", "Avg heart rate", Modifier.weight(1f))
                            }
                        }
                        if (session.maxHeartRate != null) {
                            Row(Modifier.fillMaxWidth()) {
                                StatTile("${session.maxHeartRate} bpm", "Peak heart rate", Modifier.weight(1f))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Space.sm))
                Caption("Calories are an estimate from activity type, time and bodyweight — not a measurement.")
                if (session.note.isNotBlank()) {
                    Spacer(Modifier.height(Space.md))
                    Text(session.note, style = MaterialTheme.typography.bodyMedium, color = ChalkDim)
                }
                Spacer(Modifier.height(Space.section))
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this session?",
            body = "The route and stats will be removed permanently.",
            confirmLabel = "Delete session",
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

private fun elevation(meters: Double, unit: UnitSystem): String =
    if (unit == UnitSystem.METRIC) "${meters.roundToInt()} m" else "${(meters * 3.28084).roundToInt()} ft"
