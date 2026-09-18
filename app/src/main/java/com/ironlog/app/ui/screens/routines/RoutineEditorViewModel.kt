package com.ironlog.app.ui.screens.routines

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.data.repository.ExerciseRepository
import com.ironlog.app.data.repository.RoutineDraft
import com.ironlog.app.data.repository.RoutineExerciseDraft
import com.ironlog.app.data.repository.RoutineRepository
import com.ironlog.app.data.repository.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RoutineEditorUiState(
    val draft: RoutineDraft = RoutineDraft(),
    val isNew: Boolean = true,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val nameError: String? = null,
    val error: String? = null
)

@HiltViewModel
class RoutineEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val routineRepository: RoutineRepository,
    private val exerciseRepository: ExerciseRepository
) : ViewModel() {

    private val routineId: String? = savedStateHandle.get<String>("id")?.takeIf { it.isNotBlank() }

    private val _state = MutableStateFlow(RoutineEditorUiState(isNew = routineId == null, isLoading = routineId != null))
    val state: StateFlow<RoutineEditorUiState> = _state.asStateFlow()

    init {
        routineId?.let { id ->
            viewModelScope.launch {
                routineRepository.loadDraft(id)
                    .onSuccess { draft -> _state.update { it.copy(draft = draft, isLoading = false) } }
                    .onFailure { e ->
                        _state.update { it.copy(isLoading = false, error = e.userMessage("Couldn't load the routine")) }
                    }
            }
        }
    }

    fun onNameChange(value: String) =
        _state.update { it.copy(draft = it.draft.copy(name = value), nameError = null, error = null) }

    fun onFolderChange(value: String) = _state.update { it.copy(draft = it.draft.copy(folder = value)) }

    fun onNotesChange(value: String) = _state.update { it.copy(draft = it.draft.copy(notes = value)) }

    /** Picked ids are resolved to names here; unknown ids are skipped and reported. */
    fun addExercises(ids: List<String>) {
        viewModelScope.launch {
            val added = mutableListOf<RoutineExerciseDraft>()
            var missing = 0
            ids.forEach { id ->
                val exercise = runCatching { exerciseRepository.observeExercise(id).first() }.getOrNull()
                if (exercise == null) {
                    missing++
                } else {
                    added += RoutineExerciseDraft(exerciseId = exercise.id, exerciseName = exercise.name)
                }
            }
            _state.update {
                it.copy(
                    draft = it.draft.copy(exercises = it.draft.exercises + added),
                    error = if (missing > 0) "$missing exercise(s) could not be found" else it.error
                )
            }
        }
    }

    fun updateExercise(index: Int, transform: (RoutineExerciseDraft) -> RoutineExerciseDraft) =
        _state.update { state ->
            val list = state.draft.exercises.toMutableList()
            if (index !in list.indices) return@update state
            list[index] = transform(list[index])
            state.copy(draft = state.draft.copy(exercises = list), error = null)
        }

    fun move(index: Int, delta: Int) = _state.update { state ->
        val list = state.draft.exercises.toMutableList()
        val target = index + delta
        if (index !in list.indices || target !in list.indices) return@update state
        val item = list.removeAt(index)
        list.add(target, item)
        state.copy(draft = state.draft.copy(exercises = list))
    }

    fun remove(index: Int) = _state.update { state ->
        val list = state.draft.exercises.toMutableList()
        if (index !in list.indices) return@update state
        list.removeAt(index)
        state.copy(draft = state.draft.copy(exercises = list))
    }

    fun save() {
        val current = _state.value
        if (current.isSaving) return
        if (current.draft.name.isBlank()) {
            _state.update { it.copy(nameError = "Give the routine a name") }
            return
        }
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            routineRepository.save(current.draft)
                .onSuccess { _state.update { it.copy(isSaving = false, isSaved = true) } }
                .onFailure { e ->
                    _state.update { it.copy(isSaving = false, error = e.userMessage("Couldn't save the routine")) }
                }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }
}
