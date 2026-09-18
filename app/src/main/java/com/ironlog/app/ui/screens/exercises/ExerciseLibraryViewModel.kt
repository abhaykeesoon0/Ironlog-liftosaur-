package com.ironlog.app.ui.screens.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.data.entity.ExerciseCategory
import com.ironlog.app.data.entity.ExerciseEntity
import com.ironlog.app.data.entity.MuscleGroup
import com.ironlog.app.data.repository.ExerciseRepository
import com.ironlog.app.data.repository.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateExerciseState(
    val name: String = "",
    val muscle: MuscleGroup = MuscleGroup.CHEST,
    val category: ExerciseCategory = ExerciseCategory.BARBELL,
    val nameError: String? = null,
    val isSaving: Boolean = false
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExerciseLibraryViewModel @Inject constructor(
    private val repository: ExerciseRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _muscle = MutableStateFlow<MuscleGroup?>(null)
    val muscle: StateFlow<MuscleGroup?> = _muscle.asStateFlow()

    /** Kept in pick order, so exercises are added to the workout in the order tapped. */
    private val _selected = MutableStateFlow<List<String>>(emptyList())
    val selected: StateFlow<List<String>> = _selected.asStateFlow()

    private val _create = MutableStateFlow<CreateExerciseState?>(null)
    val create: StateFlow<CreateExerciseState?> = _create.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Null until the first query returns, so the screen can show a loader. */
    val exercises: StateFlow<List<ExerciseEntity>?> =
        combine(_query.debounce(150), _muscle) { q, m -> q to m }
            .flatMapLatest { (q, m) -> repository.observeLibrary(q, m, null) }
            .map<List<ExerciseEntity>, List<ExerciseEntity>?> { it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun onMuscleSelect(value: MuscleGroup?) {
        _muscle.value = value
    }

    fun toggle(id: String) = _selected.update { if (id in it) it - id else it + id }

    fun openCreate() {
        _create.value = CreateExerciseState(name = _query.value.trim(), muscle = _muscle.value ?: MuscleGroup.CHEST)
    }

    fun dismissCreate() {
        _create.value = null
    }

    fun updateCreate(transform: (CreateExerciseState) -> CreateExerciseState) =
        _create.update { it?.let(transform)?.copy(nameError = null) }

    /** Saves, and in pick mode selects the new exercise straight away. */
    fun saveCreate(pickMode: Boolean) {
        val form = _create.value ?: return
        if (form.isSaving) return
        _create.value = form.copy(isSaving = true)
        viewModelScope.launch {
            repository.createCustom(form.name, form.muscle, form.category, instructions = "")
                .onSuccess { exercise ->
                    _create.value = null
                    if (pickMode) _selected.update { it + exercise.id }
                }
                .onFailure { e ->
                    _create.update {
                        it?.copy(isSaving = false, nameError = e.userMessage("Couldn't create the exercise"))
                    }
                }
        }
    }

    fun dismissError() {
        _error.value = null
    }
}
