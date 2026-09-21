package com.ironlog.app.ui.screens.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.data.dao.WorkoutWithDetail
import com.ironlog.app.data.entity.UnitSystem
import com.ironlog.app.data.repository.ProfileRepository
import com.ironlog.app.data.repository.WorkoutRepository
import com.ironlog.app.data.repository.userMessage
import com.ironlog.app.domain.usecase.Gamification
import com.ironlog.app.ui.format.formatDuration
import com.ironlog.app.ui.format.formatFullDate
import com.ironlog.app.ui.format.formatVolume
import com.ironlog.app.ui.format.formatWeight
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorkoutDetailUiState(
    val isLoading: Boolean = true,
    val justFinished: Boolean = false,
    val detail: WorkoutWithDetail? = null,
    val unit: UnitSystem = UnitSystem.METRIC,
    val achievements: List<Gamification.Achievement> = emptyList(),
    val xpEarned: Int = 0,
    val isDeleting: Boolean = false,
    val isDeleted: Boolean = false,
    val error: String? = null
) {
    val totalReps: Int
        get() = detail?.exercises?.sumOf { ex ->
            ex.sets.filter { it.isCompleted && it.type.countsTowardVolume }.sumOf { it.reps ?: 0 }
        } ?: 0
}

@HiltViewModel
class WorkoutDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    profileRepository: ProfileRepository
) : ViewModel() {

    private val workoutId: String = checkNotNull(savedStateHandle.get<String>("id"))

    /** What one pass of the three source flows produced. */
    private data class Loaded(
        val detail: WorkoutWithDetail?,
        val unit: UnitSystem,
        val achievements: List<Gamification.Achievement>,
        val xpEarned: Int
    )

    private val _state = MutableStateFlow(
        WorkoutDetailUiState(justFinished = savedStateHandle.get<Boolean>("justFinished") ?: false)
    )
    val state: StateFlow<WorkoutDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                workoutRepository.observeWorkout(workoutId),
                profileRepository.observeUser(),
                workoutRepository.observeHistory()
            ) { detail, user, history ->
                Loaded(
                    detail = detail,
                    unit = user?.unitSystem ?: UnitSystem.METRIC,
                    // Only the sessions that came before this one can be beaten by it,
                    // so a workout opened from deep in the history is judged against
                    // what the user had done by then, not against everything since.
                    achievements = detail?.workout?.let { workout ->
                        val earlier = history
                            .map { it.workout }
                            .filter { it.startedAt < workout.startedAt }
                            .sortedBy { it.startedAt }
                        Gamification.achievementsFor(workout, earlier)
                    }.orEmpty(),
                    xpEarned = detail?.workout?.let { Gamification.workoutXp(it) } ?: 0
                )
            }
                .catch { e -> _state.update { it.copy(isLoading = false, error = e.userMessage("Could not load this workout")) } }
                .collect { loaded ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            detail = loaded.detail,
                            unit = loaded.unit,
                            achievements = loaded.achievements,
                            xpEarned = loaded.xpEarned
                        )
                    }
                }
        }
    }

    fun delete() {
        if (_state.value.isDeleting) return
        _state.update { it.copy(isDeleting = true, error = null) }
        viewModelScope.launch {
            workoutRepository.delete(workoutId)
                .onSuccess { _state.update { it.copy(isDeleting = false, isDeleted = true) } }
                .onFailure { e ->
                    _state.update { it.copy(isDeleting = false, error = e.userMessage("Could not delete the workout")) }
                }
        }
    }

    /** Plain-text summary for the share sheet. No health data beyond what the user sees. */
    fun shareText(): String? {
        val s = _state.value
        val detail = s.detail ?: return null
        val w = detail.workout
        return buildString {
            appendLine(w.name)
            appendLine(formatFullDate(w.startedAt))
            appendLine("${formatDuration(w.durationSeconds)} · ${s.unit.formatVolume(w.totalVolumeKg)} · ${w.totalSets} sets")
            appendLine()
            detail.exercises.sortedBy { it.workoutExercise.orderIndex }.forEach { ex ->
                appendLine(ex.exercise.name)
                ex.sets.sortedBy { it.setNumber }.forEach { set ->
                    val load = set.weightKg?.let { s.unit.formatWeight(it) }
                    val reps = set.reps?.let { "$it reps" }
                    appendLine("  ${set.setNumber}. ${listOfNotNull(load, reps).joinToString(" × ")}")
                }
            }
            append("Logged with IronLog")
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }
}
