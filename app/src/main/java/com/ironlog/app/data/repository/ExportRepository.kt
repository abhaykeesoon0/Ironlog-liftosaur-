package com.ironlog.app.data.repository

import android.content.Intent
import com.ironlog.app.core.media.DataExporter
import com.ironlog.app.data.dao.BodyDao
import com.ironlog.app.data.dao.CardioDao
import com.ironlog.app.data.dao.RoutineDao
import com.ironlog.app.data.dao.WorkoutDao
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.ironlog.app.data.dao.UserDao
import com.ironlog.app.data.dao.ExerciseDao
import com.ironlog.app.data.dao.NutritionDao
import com.ironlog.app.data.dao.StrengthGoalDao
import com.ironlog.app.data.dao.RewardsDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class ExportKind(val label: String, val mimeType: String) {
    WORKOUTS_CSV("Workouts (CSV)", "text/csv"),
    MEASUREMENTS_CSV("Body measurements (CSV)", "text/csv"),
    CARDIO_CSV("Cardio sessions (CSV)", "text/csv"),
    FULL_JSON("Full backup (JSON)", "application/json")
}

interface ExportRepository {
    suspend fun export(kind: ExportKind): Result<File>
    fun shareIntent(file: File, kind: ExportKind): Intent

    /** The full backup as JSON text, for a caller that will seal or store it itself. */
    suspend fun backupJson(userId: String): Result<String>
}

@Singleton
class ExportRepositoryImpl @Inject constructor(
    private val exporter: DataExporter,
    private val workoutDao: WorkoutDao,
    private val routineDao: RoutineDao,
    private val bodyDao: BodyDao,
    private val cardioDao: CardioDao,
    private val userDao: UserDao,
    private val exerciseDao: ExerciseDao,
    private val nutritionDao: NutritionDao,
    private val goalDao: StrengthGoalDao,
    private val rewardsDao: RewardsDao,
    private val dataStore: DataStore<Preferences>,
    private val currentUser: CurrentUser
) : ExportRepository {

    private val json = Json { prettyPrint = true; encodeDefaults = true }

    override suspend fun export(kind: ExportKind): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = currentUser.require()
            exporter.pruneOldExports()
            when (kind) {
                ExportKind.WORKOUTS_CSV -> exporter.exportWorkoutsCsv(workoutDao.allFinished(userId))
                ExportKind.MEASUREMENTS_CSV -> exporter.exportMeasurementsCsv(bodyDao.observeMeasurements(userId).first())
                ExportKind.CARDIO_CSV -> exporter.exportCardioCsv(cardioDao.observeSessions(userId).first())
                ExportKind.FULL_JSON -> exporter.exportJson(json.encodeToString(Backup.serializer(), backup(userId)))
            }.getOrThrow()
        }
    }

    override fun shareIntent(file: File, kind: ExportKind): Intent = exporter.share(file, kind.mimeType)

    override suspend fun backupJson(userId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching { json.encodeToString(Backup.serializer(), backup(userId)) }
    }

    private suspend fun backup(userId: String): Backup = Backup(
        exportedAt = System.currentTimeMillis(),
        workouts = workoutDao.allFinished(userId).map { detail ->
            BackupWorkout(
                name = detail.workout.name,
                startedAt = detail.workout.startedAt,
                finishedAt = detail.workout.finishedAt,
                notes = detail.workout.notes,
                exercises = detail.exercises.sortedBy { it.workoutExercise.orderIndex }.map { item ->
                    BackupExercise(
                        exerciseId = item.exercise.id,
                        name = item.exercise.name,
                        notes = item.workoutExercise.notes,
                        sets = item.sets.sortedBy { it.setNumber }.map { set ->
                            BackupSet(
                                setNumber = set.setNumber,
                                type = set.type.name,
                                weightKg = set.weightKg,
                                reps = set.reps,
                                rpe = set.rpe,
                                distanceMeters = set.distanceMeters,
                                durationSeconds = set.durationSeconds
                            )
                        }
                    )
                }
            )
        },
        routines = routineDao.observeRoutines(userId).first().map { routine ->
            BackupRoutine(
                name = routine.routine.name,
                folder = routine.routine.folder,
                exercises = routine.exercises.sortedBy { it.routineExercise.orderIndex }.map {
                    BackupRoutineExercise(
                        exerciseId = it.exercise.id,
                        name = it.exercise.name,
                        targetSets = it.routineExercise.targetSets,
                        targetRepsLow = it.routineExercise.targetRepsLow,
                        targetRepsHigh = it.routineExercise.targetRepsHigh,
                        restSeconds = it.routineExercise.restSeconds
                    )
                }
            )
        },
        measurements = bodyDao.observeMeasurements(userId).first().map {
            BackupMeasurement(it.takenAt, it.weightKg, it.bodyFatPercent, it.waistCm, it.chestCm, it.note)
        },
        cardio = cardioDao.observeSessions(userId).first().map {
            BackupCardio(it.type.name, it.startedAt, it.distanceMeters, it.durationSeconds, it.steps, it.routeJson)
        },
        // The account row travels too, so a new phone can recreate it and the
        // user signs in with the password they already know. Only the hash and
        // its salt go: the password itself was never stored to begin with.
        account = userDao.findById(userId)?.let { u ->
            BackupAccount(
                id = u.id, email = u.email, displayName = u.displayName,
                passwordHash = u.passwordHash, passwordSalt = u.passwordSalt, createdAt = u.createdAt,
                unitSystem = u.unitSystem.name, bodyweightKg = u.bodyweightKg, heightCm = u.heightCm,
                biometricLockEnabled = u.biometricLockEnabled, restTimerDefaultSeconds = u.restTimerDefaultSeconds,
                autoRestTimerEnabled = u.autoRestTimerEnabled, weeklyGoalWorkouts = u.weeklyGoalWorkouts,
                emailVerified = u.emailVerified
            )
        },
        foods = nutritionDao.customFoods(userId).map { f ->
            BackupFood(
                id = f.id, name = f.name, group = f.group.name, kcalPer100g = f.kcalPer100g,
                proteinPer100g = f.proteinPer100g, carbsPer100g = f.carbsPer100g, fatPer100g = f.fatPer100g,
                servingGrams = f.servingGrams, servingLabel = f.servingLabel, barcode = f.barcode
            )
        },
        foodLogs = run {
            val names = HashMap<String, String>()
            nutritionDao.allLogs(userId).map { log ->
                val name = names.getOrPut(log.foodId) { nutritionDao.findFood(log.foodId)?.name ?: "" }
                BackupFoodLog(log.foodId, name, log.grams, log.meal.name, log.loggedAt, log.note)
            }
        },
        nutritionGoal = nutritionDao.findGoal(userId)?.let { g ->
            BackupNutritionGoal(g.goal.name, g.sex.name, g.ageYears, g.activity.name, g.targetKcal, g.proteinG, g.carbsG, g.fatG, g.updatedAt)
        },
        strengthGoals = goalDao.allForUser(userId).map { g ->
            BackupStrengthGoal(g.exerciseId, exerciseNameOf(g.exerciseId), g.metric.name, g.targetKg, g.createdAt, g.achievedAt)
        },
        questClaims = rewardsDao.allClaims(userId).map { BackupQuestClaim(it.questId, it.periodKey, it.coins, it.claimedAt) },
        purchases = rewardsDao.allPurchases(userId).map { BackupPurchase(it.itemId, it.coinsSpent, it.purchasedAt, it.isEquipped) },
        settings = SettingsBackup.snapshot(dataStore.data.first())
    )

    private suspend fun exerciseNameOf(exerciseId: String): String =
        exerciseDao.findById(exerciseId)?.name.orEmpty()
}

// Progress photos are deliberately left out of the backup: the JSON file is meant
// to be shared, and a share sheet is the wrong place for body photos.

/**
 * Version 2 adds the account and everything else that belongs to a person:
 * food, goals, rewards and settings. Every addition has a default, so a
 * version-1 file still opens; a version-2 file on an old build is read for
 * the fields it knows.
 */
@Serializable
data class Backup(
    val format: String = "ironlog-backup",
    val version: Int = 2,
    val exportedAt: Long,
    val workouts: List<BackupWorkout>,
    val routines: List<BackupRoutine>,
    val measurements: List<BackupMeasurement>,
    val cardio: List<BackupCardio>,
    val account: BackupAccount? = null,
    val foods: List<BackupFood> = emptyList(),
    val foodLogs: List<BackupFoodLog> = emptyList(),
    val nutritionGoal: BackupNutritionGoal? = null,
    val strengthGoals: List<BackupStrengthGoal> = emptyList(),
    val questClaims: List<BackupQuestClaim> = emptyList(),
    val purchases: List<BackupPurchase> = emptyList(),
    val settings: Map<String, String> = emptyMap()
)

/** The user row as stored: the password is not here because it never was anywhere. */
@Serializable
data class BackupAccount(
    val id: String,
    val email: String,
    val displayName: String,
    val passwordHash: String,
    val passwordSalt: String,
    val createdAt: Long,
    val unitSystem: String,
    val bodyweightKg: Double?,
    val heightCm: Double?,
    val biometricLockEnabled: Boolean,
    val restTimerDefaultSeconds: Int,
    val autoRestTimerEnabled: Boolean,
    val weeklyGoalWorkouts: Int,
    val emailVerified: Boolean = true
)

@Serializable
data class BackupFood(
    val id: String,
    val name: String,
    val group: String,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val servingGrams: Double,
    val servingLabel: String,
    val barcode: String?
)

@Serializable
data class BackupFoodLog(val foodId: String, val foodName: String, val grams: Double, val meal: String, val loggedAt: Long, val note: String)

@Serializable
data class BackupNutritionGoal(
    val goal: String, val sex: String, val ageYears: Int, val activity: String,
    val targetKcal: Int, val proteinG: Int, val carbsG: Int, val fatG: Int, val updatedAt: Long
)

@Serializable
data class BackupStrengthGoal(val exerciseId: String, val exerciseName: String, val metric: String, val targetKg: Double, val createdAt: Long, val achievedAt: Long?)

@Serializable
data class BackupQuestClaim(val questId: String, val periodKey: String, val coins: Int, val claimedAt: Long)

@Serializable
data class BackupPurchase(val itemId: String, val coinsSpent: Int, val purchasedAt: Long, val isEquipped: Boolean)

@Serializable
data class BackupWorkout(
    val name: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val notes: String,
    val exercises: List<BackupExercise>
)

@Serializable
data class BackupExercise(val exerciseId: String, val name: String, val notes: String, val sets: List<BackupSet>)

@Serializable
data class BackupSet(
    val setNumber: Int,
    val type: String,
    val weightKg: Double?,
    val reps: Int?,
    val rpe: Double?,
    val distanceMeters: Double?,
    val durationSeconds: Int?
)

@Serializable
data class BackupRoutine(val name: String, val folder: String, val exercises: List<BackupRoutineExercise>)

@Serializable
data class BackupRoutineExercise(
    val exerciseId: String,
    val name: String,
    val targetSets: Int,
    val targetRepsLow: Int,
    val targetRepsHigh: Int,
    val restSeconds: Int
)

@Serializable
data class BackupMeasurement(
    val takenAt: Long,
    val weightKg: Double?,
    val bodyFatPercent: Double?,
    val waistCm: Double?,
    val chestCm: Double?,
    val note: String
)

@Serializable
data class BackupCardio(
    val type: String,
    val startedAt: Long,
    val distanceMeters: Double,
    val durationSeconds: Int,
    val steps: Int,
    val routeJson: String
)
