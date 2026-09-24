package com.ironlog.app.data.dao

import androidx.room.Embedded
import androidx.room.Relation
import com.ironlog.app.data.entity.ExerciseEntity
import com.ironlog.app.data.entity.MuscleGroup
import com.ironlog.app.data.entity.StrengthGoalEntity
import com.ironlog.app.data.entity.PersonalRecordEntity
import com.ironlog.app.data.entity.RoutineEntity
import com.ironlog.app.data.entity.RoutineExerciseEntity
import com.ironlog.app.data.entity.WorkoutEntity
import com.ironlog.app.data.entity.WorkoutExerciseEntity
import com.ironlog.app.data.entity.WorkoutSetEntity

data class RoutineExerciseWithExercise(
    @Embedded val routineExercise: RoutineExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity
)

data class RoutineWithExercises(
    @Embedded val routine: RoutineEntity,
    @Relation(
        entity = RoutineExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "routineId"
    )
    val exercises: List<RoutineExerciseWithExercise>
)

data class WorkoutExerciseWithDetail(
    @Embedded val workoutExercise: WorkoutExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "workoutExerciseId")
    val sets: List<WorkoutSetEntity>
)

data class WorkoutWithDetail(
    @Embedded val workout: WorkoutEntity,
    @Relation(
        entity = WorkoutExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "workoutId"
    )
    val exercises: List<WorkoutExerciseWithDetail>
)

/** Aggregate rows returned by analytics queries. */
data class VolumePoint(val bucket: Long, val volume: Double)
data class MuscleVolume(val primaryMuscle: String, val volume: Double, val sets: Int)
data class ExerciseSessionPoint(
    val workoutId: String,
    val startedAt: Long,
    val bestWeight: Double,
    val bestReps: Int,
    val volume: Double
)
data class ExerciseFrequency(val exerciseId: String, val name: String, val sessions: Int)

/** How many working sets were performed at a given number of repetitions. */
data class RepCount(val reps: Int, val sets: Int)

/**
 * Everything that was lifted inside one window of time.
 *
 * Read for two windows of equal length -- the period on screen and the period
 * before it -- so the app can say whether the training is going anywhere rather
 * than only what it currently is.
 */
data class TrainingTotals(
    val workouts: Int,
    val volumeKg: Double,
    val sets: Int,
    val reps: Int,
    val averageWeightKg: Double,
    val bestWeightKg: Double
) {
    val isEmpty: Boolean get() = workouts == 0 && sets == 0

    companion object {
        /** A window with nothing in it, for a signed-out user or an unused period. */
        val NONE = TrainingTotals(0, 0.0, 0, 0, 0.0, 0.0)
    }
}

data class HistoricSet(
    @Embedded val set: WorkoutSetEntity,
    val workoutId: String,
    val startedAt: Long
)

data class GoalWithExercise(
    @Embedded val goal: StrengthGoalEntity,
    val exerciseName: String
)

/** One muscle's working volume within one session. */
data class MuscleSessionRow(
    val muscle: MuscleGroup,
    val trainedAt: Long,
    val volume: Double
)

/** One exercise in one finished workout: its muscles, its working sets and volume. */
data class ExerciseWorkRow(
    val exerciseName: String,
    val primaryMuscle: MuscleGroup,
    val secondaryMuscles: String,
    val startedAt: Long,
    val sets: Int,
    val volume: Double
)

data class RecordWithExercise(
    @Embedded val record: PersonalRecordEntity,
    val exerciseName: String
)
