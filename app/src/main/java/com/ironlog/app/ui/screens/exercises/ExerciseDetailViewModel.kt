package com.ironlog.app.ui.screens.exercises

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.data.entity.ExerciseEntity
import com.ironlog.app.data.entity.PersonalRecordEntity
import com.ironlog.app.data.entity.UnitSystem
import com.ironlog.app.data.entity.WorkoutSetEntity
import com.ironlog.app.data.repository.ExerciseRepository
import com.ironlog.app.data.repository.ProfileRepository
import com.ironlog.app.data.repository.WorkoutRepository
import com.ironlog.app.data.repository.userMessage
import com.ironlog.app.domain.usecase.TrainingMath
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class ExerciseMetric(val label: String) {
    BEST_WEIGHT("Top weight"),
    ESTIMATED_1RM("Estimated 1RM (Epley)"),
    VOLUME("Volume")
}

/** One session of this exercise, computed from its completed working sets. */
data class ExerciseSession(
    val workoutId: String,
    val workoutName: String,
    val startedAt: Long,
    val bestWeightKg: Double,
    /** Best Epley estimate from any set within the reliable rep range; 0 when none qualifies. */
    val estimated1RmKg: Double,
    val volumeKg: Double,
    val workingSets: Int,
    val topSetWeightKg: Double?,
    val topSetReps: Int?
)

data class ExerciseDetailUiState(
    val isLoading: Boolean = true,
    val exercise: ExerciseEntity? = null,
    val unit: UnitSystem = UnitSystem.METRIC,
    val records: List<PersonalRecordEntity> = emptyList(),
    /** Oldest first, for charting. */
    val sessions: List<ExerciseSession> = emptyList(),
    val metric: ExerciseMetric = ExerciseMetric.BEST_WEIGHT,
    val error: String? = null
)

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    exerciseRepository: ExerciseRepository,
    workoutRepository: WorkoutRepository,
    profileRepository: ProfileRepository
) : ViewModel() {

    private val exerciseId: String = checkNotNull(savedStateHandle.get<String>("id"))
    private val metric = MutableStateFlow(ExerciseMetric.BEST_WEIGHT)

    /**
     * Sessions are derived from full workout detail rather than the aggregate
     * per-session query: that query takes the heaviest weight and the most reps
     * independently, and they are rarely from the same set, so an Epley estimate
     * built on them would overstate strength. Here each set is estimated on its own.
     */
    val state: StateFlow<ExerciseDetailUiState> = combine(
        exerciseRepository.observeExercise(exerciseId),
        exerciseRepository.observeRecords(exerciseId),
        workoutRepository.observeHistory(HISTORY_LIMIT),
        profileRepository.observeUser(),
        metric
    ) { exercise, records, history, user, selected ->
        val sessions = history.mapNotNull { detail ->
            val sets = detail.exercises
                .filter { it.exercise.id == exerciseId }
                .flatMap { it.sets }
                .filter { it.isCompleted && it.type.countsTowardVolume }
            if (sets.isEmpty()) return@mapNotNull null
            val top = sets.filter { (it.weightKg ?: 0.0) > 0 }
                .maxWithOrNull(compareBy<WorkoutSetEntity>({ it.weightKg ?: 0.0 }, { it.reps ?: 0 }))
            ExerciseSession(
                workoutId = detail.workout.id,
                workoutName = detail.workout.name,
                startedAt = detail.workout.startedAt,
                bestWeightKg = sets.maxOf { it.weightKg ?: 0.0 },
                estimated1RmKg = sets
                    .filter { TrainingMath.isReliableEstimate(it.reps ?: 0) }
                    .maxOfOrNull { TrainingMath.estimatedOneRepMax(it.weightKg ?: 0.0, it.reps ?: 0) } ?: 0.0,
                volumeKg = TrainingMath.totalVolume(sets),
                workingSets = sets.size,
                topSetWeightKg = top?.weightKg,
                topSetReps = top?.reps ?: sets.maxOfOrNull { it.reps ?: 0 }
            )
        }.sortedBy { it.startedAt }

        ExerciseDetailUiState(
            isLoading = false,
            exercise = exercise,
            unit = user?.unitSystem ?: UnitSystem.METRIC,
            records = records.sortedBy { it.recordType.ordinal },
            sessions = sessions,
            metric = selected
        )
    }
        .catch { e -> emit(ExerciseDetailUiState(isLoading = false, error = e.userMessage("Could not load this exercise"))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseDetailUiState())

    fun selectMetric(value: ExerciseMetric) {
        metric.value = value
    }

    private companion object {
        const val HISTORY_LIMIT = 500
    }
}
