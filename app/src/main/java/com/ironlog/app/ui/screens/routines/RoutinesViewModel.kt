package com.ironlog.app.ui.screens.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.data.dao.RoutineWithExercises
import com.ironlog.app.data.repository.RoutineRepository
import com.ironlog.app.data.repository.WorkoutRepository
import com.ironlog.app.data.repository.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RoutinesUiState(
    val isLoading: Boolean = true,
    /** Folder name to its routines, folders in alphabetical order. */
    val folders: List<Pair<String, List<RoutineWithExercises>>> = emptyList(),
    val activeWorkoutName: String? = null
)

@HiltViewModel
class RoutinesViewModel @Inject constructor(
    private val routineRepository: RoutineRepository,
    private val workoutRepository: WorkoutRepository
) : ViewModel() {

    val state: StateFlow<RoutinesUiState> = combine(
        routineRepository.observeRoutines(),
        workoutRepository.observeActive()
    ) { routines, active ->
        RoutinesUiState(
            isLoading = false,
            folders = routines.groupBy { it.routine.folder }.toSortedMap().map { it.key to it.value },
            activeWorkoutName = active?.workout?.name
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutinesUiState())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Set once a session is running, consumed by the screen to navigate. */
    private val _started = MutableStateFlow(false)
    val started: StateFlow<Boolean> = _started.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun start(routineId: String?) {
        if (_busy.value) return
        _busy.value = true
        _error.value = null
        viewModelScope.launch {
            workoutRepository.start(routineId)
                .onSuccess { _started.value = true }
                .onFailure { _error.value = it.userMessage("Couldn't start the workout") }
            _busy.value = false
        }
    }

    fun consumeStarted() = _started.update { false }

    fun delete(routineId: String) {
        viewModelScope.launch {
            routineRepository.delete(routineId)
                .onFailure { _error.value = it.userMessage("Couldn't delete the routine") }
        }
    }

    fun dismissError() {
        _error.value = null
    }
}
