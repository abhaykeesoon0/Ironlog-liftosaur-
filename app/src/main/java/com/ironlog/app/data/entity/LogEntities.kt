package com.ironlog.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A training session. Exactly one workout per user may have [isActive] = true;
 * that row is what the app restores if the process is killed mid-session.
 */
@Entity(
    tableName = "workouts",
    foreignKeys = [ForeignKey(
        entity = UserEntity::class,
        parentColumns = ["id"],
        childColumns = ["userId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["userId"]), Index(value = ["startedAt"]), Index(value = ["isActive"])]
)
data class WorkoutEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val routineId: String? = null,
    val name: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val notes: String = "",
    val isActive: Boolean = false,
    /** Denormalised so history and charts do not have to re-aggregate every row. */
    val totalVolumeKg: Double = 0.0,
    val totalSets: Int = 0,
    val totalReps: Int = 0,
    val durationSeconds: Int = 0,
    val prCount: Int = 0
)

@Entity(
    tableName = "workout_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["workoutId"]), Index(value = ["exerciseId"])]
)
data class WorkoutExerciseEntity(
    @PrimaryKey val id: String,
    val workoutId: String,
    val exerciseId: String,
    val orderIndex: Int,
    val notes: String = "",
    val restSeconds: Int = 120,
    val supersetGroup: Int? = null
)

@Entity(
    tableName = "workout_sets",
    foreignKeys = [ForeignKey(
        entity = WorkoutExerciseEntity::class,
        parentColumns = ["id"],
        childColumns = ["workoutExerciseId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["workoutExerciseId"])]
)
data class WorkoutSetEntity(
    @PrimaryKey val id: String,
    val workoutExerciseId: String,
    val setNumber: Int,
    val type: SetType = SetType.NORMAL,
    val weightKg: Double? = null,
    val reps: Int? = null,
    /** Rate of perceived exertion, 6.0-10.0 in half steps. A premium field. */
    val rpe: Double? = null,
    val distanceMeters: Double? = null,
    val durationSeconds: Int? = null,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    /** Set when this set beat a stored record; drives the PR badge in the row. */
    val prType: RecordType? = null,
    /** Populated by the accelerometer rep counter when it is running. */
    val autoCountedReps: Int? = null
)

@Entity(
    tableName = "personal_records",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["userId", "exerciseId", "recordType"], unique = true),
        Index(value = ["exerciseId"])
    ]
)
data class PersonalRecordEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val exerciseId: String,
    val recordType: RecordType,
    val value: Double,
    /** Context so the PR screen can show "100 kg x 5" rather than a bare number. */
    val weightKg: Double? = null,
    val reps: Int? = null,
    val workoutId: String? = null,
    val achievedAt: Long
)

@Entity(
    tableName = "body_measurements",
    foreignKeys = [ForeignKey(
        entity = UserEntity::class,
        parentColumns = ["id"],
        childColumns = ["userId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["userId", "takenAt"])]
)
data class BodyMeasurementEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val takenAt: Long,
    val weightKg: Double? = null,
    val bodyFatPercent: Double? = null,
    val chestCm: Double? = null,
    val waistCm: Double? = null,
    val hipsCm: Double? = null,
    val leftArmCm: Double? = null,
    val rightArmCm: Double? = null,
    val thighCm: Double? = null,
    val calfCm: Double? = null,
    val neckCm: Double? = null,
    val note: String = ""
)

/** Camera output. Only the on-device file path is stored; nothing is uploaded. */
@Entity(
    tableName = "progress_photos",
    foreignKeys = [ForeignKey(
        entity = UserEntity::class,
        parentColumns = ["id"],
        childColumns = ["userId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["userId", "takenAt"])]
)
data class ProgressPhotoEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val filePath: String,
    val takenAt: Long,
    val pose: PhotoPose = PhotoPose.FRONT,
    val bodyweightKg: Double? = null,
    val note: String = ""
)

/**
 * GPS output. [routeJson] is a serialised list of RoutePoint; it is written once
 * on completion rather than on every fix, to keep the write rate down.
 */
@Entity(
    tableName = "cardio_sessions",
    foreignKeys = [ForeignKey(
        entity = UserEntity::class,
        parentColumns = ["id"],
        childColumns = ["userId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["userId", "startedAt"])]
)
data class CardioSessionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val type: CardioType,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val distanceMeters: Double = 0.0,
    val durationSeconds: Int = 0,
    val avgPaceSecondsPerKm: Int = 0,
    val elevationGainMeters: Double = 0.0,
    val estimatedCalories: Int = 0,
    val steps: Int = 0,
    val routeJson: String = "[]",
    val note: String = "",
    /** From a Bluetooth strap, when one was connected; null otherwise. */
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null
)

/** Daily step totals from the hardware step counter, keyed by local date. */
@Entity(
    tableName = "daily_activity",
    primaryKeys = ["userId", "dateEpochDay"],
    foreignKeys = [ForeignKey(
        entity = UserEntity::class,
        parentColumns = ["id"],
        childColumns = ["userId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class DailyActivityEntity(
    val userId: String,
    val dateEpochDay: Long,
    val steps: Int = 0,
    /** Raw sensor reading at first sample of the day, used to compute a delta. */
    val sensorBaseline: Float? = null,
    val updatedAt: Long = 0L
)

/**
 * A strength target on one exercise: "squat 180 kg".
 *
 * The goal stores only the target. Progress is worked out from the training
 * log whenever it is shown, so a goal can never disagree with the sets behind
 * it, and editing or deleting a workout moves the goal with it.
 */
@Entity(
    tableName = "strength_goals",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        // One goal per exercise per metric: two targets on the same measure
        // would just be the harder one with a distraction beside it.
        Index(value = ["userId", "exerciseId", "metric"], unique = true),
        Index(value = ["exerciseId"])
    ]
)
data class StrengthGoalEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val exerciseId: String,
    val metric: GoalMetric,
    /** Stored in kilograms, like every other weight in the app. */
    val targetKg: Double,
    val createdAt: Long,
    /** Set the first time the target is met, so a reached goal stays reached. */
    val achievedAt: Long? = null
)

/**
 * A claimed quest reward.
 *
 * The row is the receipt: it records that this quest was paid out for this
 * period, which is what stops a daily quest paying twice in a day and lets it
 * come back tomorrow. The coin amount is stored with the claim rather than read
 * from the catalogue at spend time, so re-pricing a quest later cannot
 * retroactively change what someone was already paid.
 */
@Entity(
    tableName = "quest_claims",
    foreignKeys = [ForeignKey(
        entity = UserEntity::class,
        parentColumns = ["id"],
        childColumns = ["userId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["userId", "questId", "periodKey"], unique = true)]
)
data class QuestClaimEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val questId: String,
    /** The day or ISO week the claim belongs to, e.g. "2026-09-22" or "2026-W39". */
    val periodKey: String,
    val coins: Int,
    val claimedAt: Long
)

/**
 * Something bought from the store.
 *
 * The price paid is kept on the row for the same reason as a quest's payout: a
 * later price change must not rewrite history. [isEquipped] is at most true for
 * one item per category, which the repository enforces when equipping.
 */
@Entity(
    tableName = "store_purchases",
    foreignKeys = [ForeignKey(
        entity = UserEntity::class,
        parentColumns = ["id"],
        childColumns = ["userId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["userId", "itemId"], unique = true)]
)
data class StorePurchaseEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val itemId: String,
    val coinsSpent: Int,
    val purchasedAt: Long,
    val isEquipped: Boolean = false
)
