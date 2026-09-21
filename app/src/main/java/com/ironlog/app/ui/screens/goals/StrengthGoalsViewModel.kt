package com.ironlog.app.ui.screens.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.data.entity.GoalMetric
import com.ironlog.app.data.entity.UnitSystem
import com.ironlog.app.data.repository.ProfileRepository
import com.ironlog.app.data.repository.StrengthGoalRepository
import com.ironlog.app.data.repository.userMessage
import com.ironlog.app.domain.usecase.StrengthGoalMath
import com.ironlog.app.domain.usecase.TrainingMath
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One goal with everything the card needs to draw itself. */
data class GoalCard(
    val id: String,
    val exerciseId: String,
    val exerciseName: String,
    val metric: GoalMetric,
    val targetKg: Double,
    val currentKg: Double,
    val progress: Float,
    val remainingKg: Double,
    val isAchieved: Boolean,
    val series: List<StrengthGoalMath.SessionValue>
)

data class GoalsUiState(
    val isLoading: Boolean = true,
    val unit: UnitSystem = UnitSystem.METRIC,
    val goals: List<GoalCard> = emptyList(),
    val isSaving: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class StrengthGoalsViewModel @Inject constructor(
    private val goalRepository: StrengthGoalRepository,
    profileRepository: ProfileRepository
) : ViewModel() {

    private val _state = MutableStateFlow(GoalsUiState())
    val state: StateFlow<GoalsUiState> = _state.asStateFlow()

    /**
     * Goals joined with the sets behind each one.
     *
     * flatMapLatest so that adding or removing a goal re-subscribes to exactly
     * the exercises now on screen: a goal that has gone should stop costing a
     * query, and a new one should start updating without waiting for a reload.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val cards = goalRepository.observeGoals().flatMapLatest { goals ->
        if (goals.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(
                goals.map { row -> goalRepository.observeSetsFor(row.goal.exerciseId) }
            ) { setsPerGoal ->
                goals.mapIndexed { index, row ->
                    val sets = setsPerGoal[index]
                    val metric = row.goal.metric
                    val current = StrengthGoalMath.best(sets, metric)
                    GoalCard(
                        id = row.goal.id,
                        exerciseId = row.goal.exerciseId,
                        exerciseName = row.exerciseName,
                        metric = metric,
                        targetKg = row.goal.targetKg,
                        currentKg = current,
                        progress = StrengthGoalMath.progress(current, row.goal.targetKg),
                        remainingKg = StrengthGoalMath.remainingKg(current, row.goal.targetKg),
                        isAchieved = StrengthGoalMath.isAchieved(current, row.goal.targetKg),
                        series = StrengthGoalMath.series(sets, metric)
                    )
                }
            }
        }
    }

    init {
        viewModelScope.launch {
            combine(cards, profileRepository.observeUser()) { goals, user ->
                goals to (user?.unitSystem ?: UnitSystem.METRIC)
            }
                .catch { e -> _state.update { it.copy(isLoading = false, error = e.userMessage("Could not load your goals")) } }
                .collect { (goals, unit) ->
                    _state.update { it.copy(isLoading = false, goals = goals, unit = unit) }
                    stampAchieved(goals)
                }
        }
    }

    /**
     * Records the date a goal was first met. Progress itself is always derived,
     * but the moment it was reached is a fact about the past that recomputing
     * cannot recover once the lifter goes on to lift more.
     */
    private fun stampAchieved(goals: List<GoalCard>) {
        val now = System.currentTimeMillis()
        goals.filter { it.isAchieved }.forEach { goal ->
            viewModelScope.launch { goalRepository.markAchieved(goal.id, now) }
        }
    }

    /**
     * Adds a goal. [target] arrives in the user's display unit and is converted
     * here, because everything below this line is stored in kilograms.
     */
    fun addGoal(exerciseId: String, metric: GoalMetric, target: String) {
        if (_state.value.isSaving) return
        val entered = target.trim().replace(',', '.').toDoubleOrNull()
        if (entered == null) {
            _state.update { it.copy(error = "Enter the target as a number") }
            return
        }
        val targetKg = when (_state.value.unit) {
            UnitSystem.METRIC -> entered
            UnitSystem.IMPERIAL -> TrainingMath.lbToKg(entered)
        }

        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            goalRepository.addGoal(exerciseId, metric, targetKg)
                .onSuccess { _state.update { it.copy(isSaving = false) } }
                .onFailure { e ->
                    _state.update { it.copy(isSaving = false, error = e.userMessage("Could not save the goal")) }
                }
        }
    }

    fun deleteGoal(goalId: String) {
        viewModelScope.launch {
            goalRepository.deleteGoal(goalId).onFailure { e ->
                _state.update { it.copy(error = e.userMessage("Could not delete the goal")) }
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }
}

/** Goals for the Home card: the few most in progress, without their charts. */
@HiltViewModel
class HomeGoalsViewModel @Inject constructor(
    goalRepository: StrengthGoalRepository
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val goals: StateFlow<List<GoalCard>> = goalRepository.observeGoals()
        .flatMapLatest { rows ->
            if (rows.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(rows.map { goalRepository.observeSetsFor(it.goal.exerciseId) }) { setsPerGoal ->
                    rows.mapIndexed { index, row ->
                        val sets = setsPerGoal[index]
                        val current = StrengthGoalMath.best(sets, row.goal.metric)
                        GoalCard(
                            id = row.goal.id,
                            exerciseId = row.goal.exerciseId,
                            exerciseName = row.exerciseName,
                            metric = row.goal.metric,
                            targetKg = row.goal.targetKg,
                            currentKg = current,
                            progress = StrengthGoalMath.progress(current, row.goal.targetKg),
                            remainingKg = StrengthGoalMath.remainingKg(current, row.goal.targetKg),
                            isAchieved = StrengthGoalMath.isAchieved(current, row.goal.targetKg),
                            series = emptyList()
                        )
                    }
                }
            }
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
