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
import com.ironlog.app.data.dao.RoutineWithExercises
import com.ironlog.app.data.repository.RoutineRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import com.ironlog.app.core.media.DataExporter
import com.ironlog.app.core.media.WorkoutCardRenderer
import com.ironlog.app.data.repository.UserFacingException

data class WorkoutDetailUiState(
    val isLoading: Boolean = true,
    val justFinished: Boolean = false,
    val detail: WorkoutWithDetail? = null,
    val unit: UnitSystem = UnitSystem.METRIC,
    val achievements: List<Gamification.Achievement> = emptyList(),
    val xpEarned: Int = 0,
    /** What the routine now prescribes for each movement it progresses, and which way it moved. */
    val nextTargets: List<NextTarget> = emptyList(),
    val isDeleting: Boolean = false,
    val isDeleted: Boolean = false,
    val error: String? = null
) {
    val totalReps: Int
        get() = detail?.exercises?.sumOf { ex ->
            ex.sets.filter { it.isCompleted && it.type.countsTowardVolume }.sumOf { it.reps ?: 0 }
        } ?: 0
}

data class NextTarget(val exerciseName: String, val targetKg: Double, val direction: Direction) {
    enum class Direction { UP, BACK, HOLD }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkoutDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    private val routineRepository: RoutineRepository,
    profileRepository: ProfileRepository,
    private val exporter: DataExporter
) : ViewModel() {

    private val workoutId: String = checkNotNull(savedStateHandle.get<String>("id"))

    /** What one pass of the three source flows produced. */
    private data class Loaded(
        val detail: WorkoutWithDetail?,
        val unit: UnitSystem,
        val achievements: List<Gamification.Achievement>,
        val xpEarned: Int,
        val nextTargets: List<NextTarget>
    )

    private val _state = MutableStateFlow(
        WorkoutDetailUiState(justFinished = savedStateHandle.get<Boolean>("justFinished") ?: false)
    )
    val state: StateFlow<WorkoutDetailUiState> = _state.asStateFlow()

    init {
        // The routine this session came from, if any: its targets are what the
        // finish just moved, so they are read live rather than copied.
        val routine = workoutRepository.observeWorkout(workoutId)
            .map { it?.workout?.routineId }
            .distinctUntilChanged()
            .flatMapLatest { id -> if (id == null) flowOf(null) else routineRepository.observeRoutine(id) }
        viewModelScope.launch {
            combine(
                workoutRepository.observeWorkout(workoutId),
                profileRepository.observeUser(),
                workoutRepository.observeHistory(),
                routine
            ) { detail, user, history, routine ->
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
                    xpEarned = detail?.workout?.let { Gamification.workoutXp(it) } ?: 0,
                    nextTargets = nextTargets(detail, routine)
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
                            xpEarned = loaded.xpEarned,
                            nextTargets = loaded.nextTargets
                        )
                    }
                }
        }
    }

    /**
     * Which way each progressing target moved is read off the session itself:
     * a target above everything lifted went up, one below everything lifted
     * came back, and one inside the range held. No extra column, and it stays
     * true for as long as the routine is not edited by hand.
     */
    private fun nextTargets(detail: WorkoutWithDetail?, routine: RoutineWithExercises?): List<NextTarget> {
        detail ?: return emptyList()
        routine ?: return emptyList()
        val lifted = detail.exercises.groupBy { it.exercise.id }.mapValues { (_, items) ->
            items.flatMap { it.sets }.filter { it.isCompleted && it.type.countsTowardVolume }.mapNotNull { it.weightKg }
        }
        return routine.exercises.sortedBy { it.routineExercise.orderIndex }.mapNotNull { item ->
            val plan = item.routineExercise
            val target = plan.targetWeightKg ?: return@mapNotNull null
            if (plan.progressionKg <= 0.0) return@mapNotNull null
            val weights = lifted[plan.exerciseId]?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val direction = when {
                target > weights.max() + EPS -> NextTarget.Direction.UP
                target < weights.min() - EPS -> NextTarget.Direction.BACK
                else -> NextTarget.Direction.HOLD
            }
            NextTarget(item.exercise.name, target, direction)
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
            append("Logged with Liftosaur")
        }
    }

    /**
     * The same summary as a picture, ready for the share sheet. Written to
     * the app's exports folder and handed over with a temporary grant, the
     * way the CSV export is.
     */
    suspend fun shareImage(): Result<android.content.Intent> {
        val s = _state.value
        val detail = s.detail ?: return Result.failure(UserFacingException("Nothing to share yet"))
        val w = detail.workout
        val lines = detail.exercises.sortedBy { it.workoutExercise.orderIndex }.flatMap { ex ->
            listOf(ex.exercise.name) + ex.sets.sortedBy { it.setNumber }.map { set ->
                val load = set.weightKg?.let { s.unit.formatWeight(it) }
                val reps = set.reps?.let { "$it reps" }
                "  ${set.setNumber}. ${listOfNotNull(load, reps).joinToString(" \u00d7 ")}"
            }
        }
        val card = WorkoutCardRenderer.Card(
            title = w.name,
            subtitle = formatFullDate(w.startedAt),
            stats = listOf(
                formatDuration(w.durationSeconds) to "Duration",
                s.unit.formatVolume(w.totalVolumeKg) to "Volume",
                w.totalSets.toString() to "Sets",
                w.prCount.toString() to "PRs"
            ),
            lines = lines
        )
        return runCatching { WorkoutCardRenderer.render(card) }
            .mapCatching { bytes -> exporter.exportBinary(bytes, "png").getOrThrow() }
            .map { file -> exporter.share(file, "image/png") }
    }

    fun showError(message: String) = _state.update { it.copy(error = message) }

    fun dismissError() = _state.update { it.copy(error = null) }
}

private const val EPS = 1e-6
