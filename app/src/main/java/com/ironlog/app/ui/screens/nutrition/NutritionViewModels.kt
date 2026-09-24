package com.ironlog.app.ui.screens.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.data.dao.FoodLogWithFood
import com.ironlog.app.data.entity.FoodEntity
import com.ironlog.app.data.entity.Meal
import com.ironlog.app.data.entity.NutritionGoalEntity
import com.ironlog.app.data.entity.UserEntity
import com.ironlog.app.data.repository.FoodInput
import com.ironlog.app.data.repository.GoalInput
import com.ironlog.app.data.repository.NutritionRepository
import com.ironlog.app.data.repository.ProfileRepository
import com.ironlog.app.data.repository.userMessage
import com.ironlog.app.domain.usecase.DietPlanner
import com.ironlog.app.domain.usecase.NutritionMath
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.ironlog.app.data.repository.ScanResult
import com.ironlog.app.ui.components.Undo
import android.util.Log
import com.ironlog.app.core.sensors.HealthConnectWriter

data class NutritionUiState(
    val isLoading: Boolean = true,
    val goal: NutritionGoalEntity? = null,
    val today: List<FoodLogWithFood> = emptyList(),
    val foods: List<FoodEntity> = emptyList(),
    val profile: UserEntity? = null,
    val isScanning: Boolean = false,
    val error: String? = null,
    val message: String? = null
) {
    val totals: NutritionMath.Totals
        get() = today.fold(NutritionMath.Totals()) { sum, entry ->
            sum + NutritionMath.Totals(entry.kcal, entry.proteinG, entry.carbsG, entry.fatG)
        }

    fun forMeal(meal: Meal): List<FoodLogWithFood> = today.filter { it.log.meal == meal }
}

@HiltViewModel
class NutritionViewModel @Inject constructor(
    private val repository: NutritionRepository,
    profileRepository: ProfileRepository,
    private val healthConnect: HealthConnectWriter
) : ViewModel() {

    private val _state = MutableStateFlow(NutritionUiState())
    val state: StateFlow<NutritionUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.observeGoal(),
                repository.observeDay(System.currentTimeMillis()),
                repository.observeFoods(),
                profileRepository.observeUser()
            ) { goal, today, foods, user ->
                NutritionUiState(isLoading = false, goal = goal, today = today, foods = foods, profile = user)
            }
                .catch { e -> _state.update { it.copy(isLoading = false, error = e.userMessage("Could not load your food log")) } }
                .collect { next -> _state.update { next.copy(error = it.error, message = it.message, isScanning = it.isScanning) } }
        }
    }

    fun logFood(food: FoodEntity, grams: Double, meal: Meal) {
        viewModelScope.launch {
            val at = System.currentTimeMillis()
            repository.logFood(food.id, grams, meal, at)
                .onSuccess {
                    _state.update { it.copy(message = "${food.name} added to ${meal.label.lowercase()}") }
                    val p = NutritionMath.portion(grams, food.kcalPer100g, food.proteinPer100g, food.carbsPer100g, food.fatPer100g)
                    viewModelScope.launch {
                        val outcome = healthConnect.writeNutrition(food.name, at, p.kcal, p.proteinG, p.carbsG, p.fatG, meal)
                        if (outcome is HealthConnectWriter.Outcome.Failed) Log.w("IronLog", "Health Connect: ${outcome.message}")
                    }
                }
                .onFailure { e -> _state.update { it.copy(error = e.userMessage("Could not log that food")) } }
        }
    }

    /** The last entry removed, offered back for a few seconds. */
    private val _undo = MutableStateFlow<Undo?>(null)
    val undo: StateFlow<Undo?> = _undo.asStateFlow()
    fun consumeUndo() { _undo.value = null }

    fun deleteLog(entry: FoodLogWithFood) {
        viewModelScope.launch {
            repository.deleteLog(entry.log.id)
                .onSuccess {
                    _undo.value = Undo("${entry.food.name} removed") {
                        repository.restoreLog(entry.log)
                            .onFailure { e -> _state.update { it.copy(error = e.userMessage("Could not restore that")) } }
                    }
                }
                .onFailure { e -> _state.update { it.copy(error = e.userMessage("Could not remove that")) } }
        }
    }

    fun createFood(input: FoodInput, onCreated: (FoodEntity) -> Unit) {
        viewModelScope.launch {
            repository.createFood(input)
                .onSuccess(onCreated)
                .onFailure { e -> _state.update { it.copy(error = e.userMessage("Could not add that food")) } }
        }
    }

    fun saveGoal(input: GoalInput, onSaved: () -> Unit) {
        viewModelScope.launch {
            repository.saveGoal(input)
                .onSuccess {
                    _state.update { it.copy(message = "Target set: ${it.goal?.targetKcal ?: 0} kcal a day") }
                    onSaved()
                }
                .onFailure { e -> _state.update { it.copy(error = e.userMessage("Could not save the goal")) } }
        }
    }

    /** Resolves a barcode and hands back either the known food or a form to confirm. */
    fun scan(barcode: String, onResult: (ScanResult) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isScanning = true) }
            repository.scan(barcode)
                .onSuccess(onResult)
                .onFailure { e -> _state.update { it.copy(error = e.userMessage("Could not look that barcode up")) } }
            _state.update { it.copy(isScanning = false) }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }
    fun dismissMessage() = _state.update { it.copy(message = null) }
}

data class PlanUiState(
    val isLoading: Boolean = true,
    val goal: NutritionGoalEntity? = null,
    val plan: DietPlanner.Plan? = null,
    val seed: Int = 0,
    val isLogging: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

/**
 * A day of meals built from the library against the saved targets.
 *
 * The seed is the only state that is the screen's own: a new one is a new
 * plan, and the same one is the same plan, which is what makes the shuffle
 * button honest.
 */
@HiltViewModel
class PlanViewModel @Inject constructor(
    private val repository: NutritionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PlanUiState(seed = (System.currentTimeMillis() / 1000L).toInt()))
    val state: StateFlow<PlanUiState> = _state.asStateFlow()

    private val seedFlow = MutableStateFlow(_state.value.seed)

    init {
        viewModelScope.launch {
            combine(repository.observeGoal(), repository.observeFoods(), seedFlow) { goal, foods, seed ->
                val plan = goal?.let {
                    DietPlanner.plan(
                        foods = foods,
                        targets = NutritionMath.Targets(it.targetKcal, it.proteinG, it.carbsG, it.fatG),
                        seed = seed
                    )
                }
                Triple(goal, plan, seed)
            }
                .catch { e -> _state.update { it.copy(isLoading = false, error = e.userMessage("Could not build a plan")) } }
                .collect { (goal, plan, seed) ->
                    _state.update { it.copy(isLoading = false, goal = goal, plan = plan, seed = seed) }
                }
        }
    }

    fun shuffle() {
        seedFlow.value = seedFlow.value + 1
    }

    /** Writes every portion of the plan into today's log, meal by meal. */
    fun logPlan() {
        val plan = _state.value.plan ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLogging = true) }
            val now = System.currentTimeMillis()
            val failure = plan.meals.flatMap { meal -> meal.portions.map { meal.meal to it } }
                .map { (meal, portion) -> repository.logFood(portion.food.id, portion.grams, meal, now) }
                .firstOrNull { it.isFailure }
            _state.update {
                it.copy(
                    isLogging = false,
                    message = if (failure == null) "Today's plan is in your log" else null,
                    error = failure?.exceptionOrNull()?.userMessage("Could not log the plan")
                )
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }
    fun dismissMessage() = _state.update { it.copy(message = null) }
}
