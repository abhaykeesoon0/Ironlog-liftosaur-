package com.ironlog.app.ui.screens.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.core.notifications.RestTimerController
import com.ironlog.app.core.sensors.RepCounter
import com.ironlog.app.core.sensors.RepSignal
import com.ironlog.app.data.dao.WorkoutExerciseWithDetail
import com.ironlog.app.data.entity.RecordType
import com.ironlog.app.data.entity.SetType
import com.ironlog.app.data.entity.UnitSystem
import com.ironlog.app.data.entity.WorkoutSetEntity
import com.ironlog.app.data.repository.EquipmentSettings
import com.ironlog.app.data.repository.ProfileRepository
import com.ironlog.app.data.repository.WorkoutRepository
import com.ironlog.app.data.repository.userMessage
import com.ironlog.app.domain.usecase.TrainingMath
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/** One exercise card: the live sets plus the last session's sets for the PREVIOUS column. */
data class ExerciseBlock(
    val item: WorkoutExerciseWithDetail,
    val sets: List<WorkoutSetEntity>,
    val previous: List<WorkoutSetEntity>
) {
    val id: String get() = item.workoutExercise.id
}

data class ActiveWorkoutUiState(
    val isLoading: Boolean = true,
    val workoutId: String? = null,
    val name: String = "",
    val startedAt: Long = 0L,
    val exercises: List<ExerciseBlock> = emptyList(),
    val unit: UnitSystem = UnitSystem.METRIC,
    val autoRestTimer: Boolean = true,
    val volumeKg: Double = 0.0,
    val completedSets: Int = 0,
    val isFinishing: Boolean = false,
    val equipment: EquipmentSettings = EquipmentSettings()
)

sealed interface ActiveWorkoutEvent {
    data object Closed : ActiveWorkoutEvent
    data class Finished(val workoutId: String) : ActiveWorkoutEvent
}

@HiltViewModel
class ActiveWorkoutViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    profileRepository: ProfileRepository,
    private val restTimer: RestTimerController,
    private val repCounter: RepCounter
) : ViewModel() {

    private val _state = MutableStateFlow(ActiveWorkoutUiState())
    val state: StateFlow<ActiveWorkoutUiState> = _state.asStateFlow()

    private val _event = MutableStateFlow<ActiveWorkoutEvent?>(null)
    val event: StateFlow<ActiveWorkoutEvent?> = _event.asStateFlow()

    /** One-shot message for the snackbar; the screen clears it once shown. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val restRemaining: StateFlow<Int> = restTimer.remainingSeconds
    val restRunning: StateFlow<Boolean> = restTimer.isRunning

    val repCounterAvailable: Boolean = repCounter.isAvailable()

    /** Previous-session sets per exercise id, fetched once per exercise. */
    private val previousCache = MutableStateFlow<Map<String, List<WorkoutSetEntity>>>(emptyMap())

    /**
     * Field edits not yet confirmed by the database, keyed by set id. A Room
     * emission can arrive carrying an older snapshot than the user's latest
     * keystroke; merging this overlay on every write means such a snapshot can
     * never erase a value the user already typed.
     */
    private val overlay = mutableMapOf<String, WorkoutSetEntity>()

    /** Serialises writes so a tick always lands after any pending field save. */
    private val writeLock = Mutex()

    /** One pass of the four source flows. */
    private data class Loaded(
        val active: com.ironlog.app.data.dao.WorkoutWithDetail?,
        val user: com.ironlog.app.data.entity.UserEntity?,
        val previous: Map<String, List<WorkoutSetEntity>>,
        val equipment: EquipmentSettings
    )

    init {
        viewModelScope.launch {
            combine(
                workoutRepository.observeActive(),
                profileRepository.observeUser(),
                previousCache,
                profileRepository.equipment
            ) { active, user, previous, equipment ->
                Loaded(active, user, previous, equipment)
            }
                .collect { (active, user, previous, equipment) ->
                    if (active == null) {
                        // Null before the first load means there is no session; null
                        // later means it was discarded. Either way the screen closes,
                        // unless a finish is in flight and navigation is handled there.
                        if (!_state.value.isFinishing) _event.value = ActiveWorkoutEvent.Closed
                        return@collect
                    }

                    val missing = active.exercises.map { it.exercise.id }.filter { it !in previous }.distinct()
                    if (missing.isNotEmpty()) loadPrevious(missing, active.workout.id)

                    val blocks = active.exercises
                        .sortedBy { it.workoutExercise.orderIndex }
                        .map { item ->
                            ExerciseBlock(
                                item = item,
                                sets = item.sets.sortedBy { it.setNumber },
                                previous = previous[item.exercise.id].orEmpty()
                            )
                        }
                    // Drop overlay entries the database has caught up with.
                    active.exercises.flatMap { it.sets }.forEach { set ->
                        overlay[set.id]?.let { pending -> if (pending.sameFields(set)) overlay.remove(set.id) }
                    }
                    val allSets = blocks.flatMap { it.sets }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            workoutId = active.workout.id,
                            name = active.workout.name,
                            startedAt = active.workout.startedAt,
                            exercises = blocks,
                            unit = user?.unitSystem ?: UnitSystem.METRIC,
                            autoRestTimer = user?.autoRestTimerEnabled ?: true,
                            volumeKg = TrainingMath.totalVolume(allSets),
                            completedSets = allSets.count { set -> set.isCompleted },
                            equipment = equipment
                        )
                    }
                }
        }
    }

    private fun loadPrevious(exerciseIds: List<String>, workoutId: String) {
        // Mark as requested immediately so a second emission does not refetch.
        previousCache.update { cache -> cache + exerciseIds.associateWith { cache[it].orEmpty() } }
        viewModelScope.launch {
            exerciseIds.forEach { id ->
                workoutRepository.previousSets(id, workoutId)
                    .onSuccess { sets -> previousCache.update { it + (id to sets) } }
                    .onFailure { _message.value = it.userMessage("Couldn't load your previous sets") }
            }
        }
    }

    fun consumeEvent() {
        _event.value = null
    }

    fun consumeMessage() {
        _message.value = null
    }

    // ------------------------------------------------------------ workout level

    fun rename(name: String) = launchResult("Couldn't rename the workout") {
        workoutRepository.rename(_state.value.workoutId ?: return@launchResult Result.success(Unit), name)
    }

    fun addExercises(ids: List<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val workoutId = _state.first { it.workoutId != null }.workoutId ?: return@launch
            workoutRepository.addExercises(workoutId, ids)
                .onFailure { _message.value = it.userMessage("Couldn't add the exercises") }
        }
    }

    fun finish() {
        if (_state.value.isFinishing) return
        _state.update { it.copy(isFinishing = true) }
        viewModelScope.launch {
            writeLock.withLock { workoutRepository.finishActive() }
                .onSuccess { summary ->
                    restTimer.skip()
                    _event.value = ActiveWorkoutEvent.Finished(summary.workoutId)
                }
                .onFailure { e ->
                    _state.update { it.copy(isFinishing = false) }
                    _message.value = e.userMessage("Couldn't finish the workout")
                }
        }
    }

    fun discard() {
        viewModelScope.launch {
            workoutRepository.discardActive()
                .onSuccess {
                    restTimer.skip()
                    _event.value = ActiveWorkoutEvent.Closed
                }
                .onFailure { _message.value = it.userMessage("Couldn't discard the workout") }
        }
    }

    // ----------------------------------------------------------- exercise level

    fun removeExercise(workoutExerciseId: String) = launchResult("Couldn't remove the exercise") {
        workoutRepository.removeExercise(workoutExerciseId)
    }

    fun updateNotes(workoutExerciseId: String, notes: String) = launchResult("Couldn't save the note") {
        workoutRepository.updateExerciseNotes(workoutExerciseId, notes)
    }

    fun updateRest(workoutExerciseId: String, seconds: Int) = launchResult("Couldn't change the rest time") {
        workoutRepository.updateRest(workoutExerciseId, seconds)
    }

    fun addSet(workoutExerciseId: String) = launchResult("Couldn't add a set") {
        writeLock.withLock { workoutRepository.addSet(workoutExerciseId) }
    }

    // ---------------------------------------------------------------- set level

    fun deleteSet(setId: String) {
        overlay.remove(setId)
        launchResult("Couldn't delete the set") { writeLock.withLock { workoutRepository.deleteSet(setId) } }
    }

    /** Warm-up -> working -> drop -> failure -> warm-up. */
    fun cycleType(setId: String) = editSet(setId) {
        val order = SetType.entries
        it.copy(type = order[(order.indexOf(it.type) + 1) % order.size])
    }

    fun setWeight(setId: String, kg: Double?) = editSet(setId) { it.copy(weightKg = kg) }
    fun setReps(setId: String, reps: Int?) = editSet(setId) { it.copy(reps = reps) }
    fun setDuration(setId: String, seconds: Int?) = editSet(setId) { it.copy(durationSeconds = seconds) }
    fun setDistance(setId: String, meters: Double?) = editSet(setId) { it.copy(distanceMeters = meters) }

    private fun editSet(setId: String, transform: (WorkoutSetEntity) -> WorkoutSetEntity) {
        val base = overlay[setId] ?: findSet(setId) ?: return
        val edited = transform(base)
        if (edited.sameFields(findSet(setId) ?: base) && overlay[setId] == null) return
        overlay[setId] = edited
        viewModelScope.launch {
            writeLock.withLock {
                val latest = findSet(setId) ?: return@withLock
                val pending = overlay[setId] ?: return@withLock
                workoutRepository.updateSet(latest.mergeFields(pending))
                    .onFailure { e ->
                        overlay.remove(setId)
                        _message.value = e.userMessage("Couldn't save that set")
                    }
            }
        }
    }

    /**
     * Ticks a set off (or un-ticks it). [fallbackWeightKg]/[fallbackReps] are the
     * greyed-out previous values the user was looking at.
     */
    fun toggleComplete(setId: String, fallbackWeightKg: Double?, fallbackReps: Int?) {
        viewModelScope.launch {
            val result = writeLock.withLock {
                workoutRepository.toggleComplete(setId, fallbackWeightKg, fallbackReps)
            }
            result
                .onSuccess { completion ->
                    if (!completion.isCompleted) return@onSuccess
                    if (_state.value.autoRestTimer && completion.restSeconds > 0) {
                        restTimer.start(completion.restSeconds, nextExerciseName(setId) ?: completion.exerciseName)
                            .onFailure { _message.value = it.userMessage("Couldn't start the rest timer") }
                    }
                    completion.record?.let { _message.value = "New PR: ${recordLabel(it)} on ${completion.exerciseName}" }
                }
                .onFailure { _message.value = it.userMessage("Couldn't update the set") }
        }
    }

    /** Fills reps from the accelerometer counter, after the user confirms the count. */
    fun applyCountedReps(setId: String, reps: Int) = editSet(setId) {
        it.copy(reps = reps, autoCountedReps = reps)
    }

    fun repSignals(): Flow<RepSignal> = repCounter.counts()

    // --------------------------------------------------------------- rest timer

    fun addRestTime() {
        restTimer.addTime().onFailure { _message.value = it.userMessage("Couldn't change the rest timer") }
    }

    fun skipRest() {
        restTimer.skip().onFailure { _message.value = it.userMessage("Couldn't stop the rest timer") }
    }

    // ------------------------------------------------------------------ helpers

    private fun findSet(setId: String): WorkoutSetEntity? =
        _state.value.exercises.firstNotNullOfOrNull { block -> block.sets.firstOrNull { it.id == setId } }

    /** The exercise the user will do next: this one if sets remain, else the next with work left. */
    private fun nextExerciseName(completedSetId: String): String? {
        val blocks = _state.value.exercises
        val index = blocks.indexOfFirst { block -> block.sets.any { it.id == completedSetId } }
        if (index < 0) return null
        val current = blocks[index]
        if (current.sets.any { !it.isCompleted && it.id != completedSetId }) return current.item.exercise.name
        return blocks.drop(index + 1).firstOrNull { b -> b.sets.any { !it.isCompleted } }?.item?.exercise?.name
            ?: current.item.exercise.name
    }

    private fun launchResult(fallback: String, block: suspend () -> Result<*>) {
        viewModelScope.launch {
            block().onFailure { _message.value = it.userMessage(fallback) }
        }
    }

    private fun WorkoutSetEntity.sameFields(other: WorkoutSetEntity): Boolean =
        type == other.type && weightKg == other.weightKg && reps == other.reps &&
            durationSeconds == other.durationSeconds && distanceMeters == other.distanceMeters

    /** Takes the user-editable fields from [pending]; completion and PR state stay as stored. */
    private fun WorkoutSetEntity.mergeFields(pending: WorkoutSetEntity): WorkoutSetEntity = copy(
        type = pending.type,
        weightKg = pending.weightKg,
        reps = pending.reps,
        durationSeconds = pending.durationSeconds,
        distanceMeters = pending.distanceMeters,
        autoCountedReps = pending.autoCountedReps ?: autoCountedReps
    )

    companion object {
        fun recordLabel(type: RecordType): String = when (type) {
            RecordType.HEAVIEST_WEIGHT -> "heaviest weight"
            RecordType.BEST_ESTIMATED_1RM -> "best estimated 1RM"
            RecordType.BEST_SET_VOLUME -> "best set volume"
            RecordType.MOST_REPS -> "most reps"
            RecordType.BEST_SESSION_VOLUME -> "best session volume"
        }
    }
}
