package com.ironlog.app.ui.screens.cardio

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.core.location.CardioProgress
import com.ironlog.app.core.location.CardioTrackingService
import com.ironlog.app.core.location.LocationTracker
import com.ironlog.app.core.permissions.AppPermission
import com.ironlog.app.core.sensors.StepCounter
import com.ironlog.app.data.entity.CardioType
import com.ironlog.app.data.entity.UnitSystem
import com.ironlog.app.data.repository.CardioRepository
import com.ironlog.app.data.repository.ProfileRepository
import com.ironlog.app.data.repository.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrackUiState(
    val type: CardioType = CardioType.RUN,
    val steps: Int? = null,
    val isSaving: Boolean = false,
    val savedId: String? = null,
    val discarded: Boolean = false,
    val error: String? = null,
    /** Save failed; the user can retry or throw the session away. */
    val canDiscard: Boolean = false
)

@HiltViewModel
class CardioTrackViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cardioRepository: CardioRepository,
    private val locationTracker: LocationTracker,
    private val stepCounter: StepCounter,
    profileRepository: ProfileRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    /** Owned by the foreground service, so it survives this screen being closed. */
    val progress: StateFlow<CardioProgress> = CardioTrackingService.progress

    val unit: StateFlow<UnitSystem> = profileRepository.observeUser()
        .map { it?.unitSystem ?: UnitSystem.METRIC }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UnitSystem.METRIC)

    private val _state = MutableStateFlow(
        TrackUiState(
            type = savedStateHandle.get<String>(KEY_TYPE)
                ?.let { runCatching { CardioType.valueOf(it) }.getOrNull() } ?: CardioType.RUN
        )
    )
    val state: StateFlow<TrackUiState> = _state.asStateFlow()

    private var stepJob: Job? = null

    init {
        // Reattaching to a session already running (e.g. from its notification):
        // recover the start time from the elapsed clock and resume step counting.
        val current = progress.value
        if (current.isTracking) {
            if (savedStateHandle.get<Long>(KEY_STARTED_AT) == null) {
                savedStateHandle[KEY_STARTED_AT] = System.currentTimeMillis() - current.elapsedSeconds * 1000L
            }
            startStepCounting()
        }
    }

    fun selectType(type: CardioType) {
        savedStateHandle[KEY_TYPE] = type.name
        _state.update { it.copy(type = type) }
    }

    fun isLocationEnabled(): Boolean = locationTracker.isLocationEnabled()

    val canCountSteps: Boolean get() = stepCounter.isAvailable()

    fun onTrackingStarted() {
        savedStateHandle[KEY_STARTED_AT] = System.currentTimeMillis()
        savedStateHandle.remove<Float>(KEY_STEP_BASELINE)
        _state.update { it.copy(error = null, steps = null) }
        startStepCounting()
    }

    /** Counts steps for this session only: the first reading becomes the baseline. */
    fun startStepCounting() {
        if (stepJob != null || !stepCounter.isAvailable()) return
        if (!AppPermission.ACTIVITY_RECOGNITION.isGranted(context)) return
        stepJob = viewModelScope.launch {
            stepCounter.rawCounts()
                .catch { e -> _state.update { it.copy(error = e.userMessage("Step counting stopped")) } }
                .collect { raw ->
                    val baseline = savedStateHandle.get<Float>(KEY_STEP_BASELINE) ?: raw.also {
                        savedStateHandle[KEY_STEP_BASELINE] = it
                    }
                    // A reboot mid-session resets the counter below the baseline.
                    val steps = if (raw < baseline) {
                        savedStateHandle[KEY_STEP_BASELINE] = raw
                        _state.value.steps ?: 0
                    } else {
                        (raw - baseline).toInt()
                    }
                    _state.update { it.copy(steps = steps) }
                }
        }
    }

    fun showError(message: String) = _state.update { it.copy(error = message) }

    fun dismissError() = _state.update { it.copy(error = null) }

    /** Called after the service has been told to stop. */
    fun save() {
        if (_state.value.isSaving) return
        stepJob?.cancel()
        stepJob = null
        val snapshot = progress.value
        val startedAt = savedStateHandle.get<Long>(KEY_STARTED_AT)
            ?: (System.currentTimeMillis() - snapshot.elapsedSeconds * 1000L)
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            cardioRepository.save(_state.value.type, startedAt, snapshot, _state.value.steps ?: 0, "")
                .onSuccess { id ->
                    CardioTrackingService.reset()
                    _state.update { it.copy(isSaving = false, savedId = id) }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(isSaving = false, canDiscard = true, error = e.userMessage("Could not save the session"))
                    }
                }
        }
    }

    fun discard() {
        stepJob?.cancel()
        stepJob = null
        CardioTrackingService.reset()
        _state.update { it.copy(discarded = true, canDiscard = false, error = null) }
    }

    private companion object {
        const val KEY_TYPE = "cardio_type"
        const val KEY_STARTED_AT = "cardio_started_at"
        const val KEY_STEP_BASELINE = "cardio_step_baseline"
    }
}
