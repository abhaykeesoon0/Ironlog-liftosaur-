package com.ironlog.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Credentials never store a plaintext password. [passwordHash] is a PBKDF2
 * derivation over [passwordSalt]; see core.security.PasswordHasher.
 */
@Entity(
    tableName = "users",
    indices = [Index(value = ["email"], unique = true)]
)
data class UserEntity(
    @PrimaryKey val id: String,
    val email: String,
    val displayName: String,
    val passwordHash: String,
    val passwordSalt: String,
    val createdAt: Long,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val bodyweightKg: Double? = null,
    val heightCm: Double? = null,
    val avatarPath: String? = null,
    val biometricLockEnabled: Boolean = false,
    val restTimerDefaultSeconds: Int = 120,
    val autoRestTimerEnabled: Boolean = true,
    val weeklyGoalWorkouts: Int = 4
)

@Entity(
    tableName = "exercises",
    indices = [
        Index(value = ["name"]),
        Index(value = ["primaryMuscle"]),
        Index(value = ["ownerUserId"])
    ]
)
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val primaryMuscle: MuscleGroup,
    val secondaryMuscles: String = "",   // comma-separated MuscleGroup names
    val category: ExerciseCategory,
    val instructions: String = "",
    /** Custom exercises belong to one user; seeded exercises have a null owner. */
    val ownerUserId: String? = null,
    val isCustom: Boolean = false,
    val isArchived: Boolean = false,
    /** Default bar weight for barbell movements, used by the plate calculator. */
    val defaultBarWeightKg: Double? = null
)

@Entity(
    tableName = "routines",
    foreignKeys = [ForeignKey(
        entity = UserEntity::class,
        parentColumns = ["id"],
        childColumns = ["userId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["userId"])]
)
data class RoutineEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val folder: String = "My routines",
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val lastPerformedAt: Long? = null,
    val orderIndex: Int = 0
)

@Entity(
    tableName = "routine_exercises",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["routineId"]), Index(value = ["exerciseId"])]
)
data class RoutineExerciseEntity(
    @PrimaryKey val id: String,
    val routineId: String,
    val exerciseId: String,
    val orderIndex: Int,
    val targetSets: Int = 3,
    val targetRepsLow: Int = 8,
    val targetRepsHigh: Int = 12,
    val restSeconds: Int = 120,
    /** Exercises sharing a non-null group are performed back to back. */
    val supersetGroup: Int? = null,
    val notes: String = ""
)
