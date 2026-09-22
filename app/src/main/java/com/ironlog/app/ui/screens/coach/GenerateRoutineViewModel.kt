package com.ironlog.app.ui.screens.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.data.entity.ExerciseEntity
import com.ironlog.app.data.repository.ExerciseRepository
import com.ironlog.app.data.repository.RoutineDraft
import com.ironlog.app.data.repository.RoutineExerciseDraft
import com.ironlog.app.data.repository.RoutineRepository
import com.ironlog.app.data.repository.userMessage
import com.ironlog.app.domain.usecase.RoutineGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GenerateUiState(
    val request: RoutineGenerator.Request = RoutineGenerator.Request(),
    val plan: RoutineGenerator.Plan = RoutineGenerator.Plan(RoutineGenerator.Request(), emptyList()),
    val isSaving: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class GenerateRoutineViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val routineRepository: RoutineRepository
) : ViewModel() {

    private val _state = MutableStateFlow(GenerateUiState())
    val state: StateFlow<GenerateUiState> = _state.asStateFlow()

    private var library: List<ExerciseEntity> = emptyList()

    init {
        viewModelScope.launch {
            library = runCatching { exerciseRepository.observeLibrary(query = "", muscle = null, category = null).first() }.getOrDefault(emptyList())
            regenerate(_state.value.request)
        }
    }

    fun setGoal(goal: RoutineGenerator.Goal) = regenerate(_state.value.request.copy(goal = goal))

    fun changeDays(delta: Int) {
        val days = (_state.value.request.daysPerWeek + delta).coerceIn(2, 6)
        regenerate(_state.value.request.copy(daysPerWeek = days))
    }

    fun changeMinutes(delta: Int) {
        val minutes = (_state.value.request.minutesPerSession + delta).coerceIn(30, 120)
        regenerate(_state.value.request.copy(minutesPerSession = minutes))
    }

    /**
     * Rebuilds the plan on every change.
     *
     * Generation is pure and runs in microseconds, so the preview can simply
     * track the controls rather than hiding behind a "generate" button that
     * makes the user guess what a setting will do.
     */
    private fun regenerate(request: RoutineGenerator.Request) {
        _state.update {
            it.copy(request = request, plan = RoutineGenerator.generate(request, library))
        }
    }

    /** Saves each generated day as its own routine, in its own folder. */
    fun save(onSaved: () -> Unit) {
        if (_state.value.isSaving) return
        _state.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            val plan = _state.value.plan
            val folder = "${plan.request.goal.label} · ${plan.request.daysPerWeek} days"

            val failure = plan.days.firstNotNullOfOrNull { day ->
                val draft = RoutineDraft(
                    name = day.name,
                    folder = folder,
                    notes = day.focus,
                    exercises = day.exercises.mapIndexed { _, slot ->
                        RoutineExerciseDraft(
                            exerciseId = slot.exercise.id,
                            exerciseName = slot.exercise.name,
                            targetSets = slot.sets,
                            targetRepsLow = slot.repsLow,
                            targetRepsHigh = slot.repsHigh,
                            restSeconds = slot.restSeconds
                        )
                    }
                )
                routineRepository.save(draft).exceptionOrNull()
            }

            if (failure == null) {
                _state.update { it.copy(isSaving = false) }
                onSaved()
            } else {
                _state.update {
                    it.copy(isSaving = false, error = failure.userMessage("Could not save the routines"))
                }
            }
        }
    }
}
