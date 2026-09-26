package com.ironlog.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.ironlog.app.core.database.AppDatabase
import com.ironlog.app.core.media.DataExporter
import com.ironlog.app.core.security.BackupCrypto
import com.ironlog.app.core.security.Validators
import com.ironlog.app.data.dao.BodyDao
import com.ironlog.app.data.dao.CardioDao
import com.ironlog.app.data.dao.ExerciseDao
import com.ironlog.app.data.dao.RoutineDao
import com.ironlog.app.data.dao.WorkoutDao
import com.ironlog.app.data.dao.UserDao
import com.ironlog.app.data.dao.NutritionDao
import com.ironlog.app.data.dao.StrengthGoalDao
import com.ironlog.app.data.dao.RewardsDao
import com.ironlog.app.data.entity.UserEntity
import com.ironlog.app.data.entity.UnitSystem
import com.ironlog.app.data.entity.FoodEntity
import com.ironlog.app.data.entity.FoodGroup
import com.ironlog.app.data.entity.FoodLogEntity
import com.ironlog.app.data.entity.Meal
import com.ironlog.app.data.entity.NutritionGoalEntity
import com.ironlog.app.data.entity.DietGoal
import com.ironlog.app.data.entity.Sex
import com.ironlog.app.data.entity.ActivityLevel
import com.ironlog.app.data.entity.StrengthGoalEntity
import com.ironlog.app.data.entity.GoalMetric
import com.ironlog.app.data.entity.QuestClaimEntity
import com.ironlog.app.data.entity.StorePurchaseEntity
import com.ironlog.app.data.entity.BodyMeasurementEntity
import com.ironlog.app.data.entity.CardioSessionEntity
import com.ironlog.app.data.entity.CardioType
import com.ironlog.app.data.entity.ExerciseCategory
import com.ironlog.app.data.entity.ExerciseEntity
import com.ironlog.app.data.entity.MuscleGroup
import com.ironlog.app.data.entity.RoutineEntity
import com.ironlog.app.data.entity.RoutineExerciseEntity
import com.ironlog.app.data.entity.SetType
import com.ironlog.app.data.entity.WorkoutEntity
import com.ironlog.app.data.entity.WorkoutExerciseEntity
import com.ironlog.app.data.entity.WorkoutSetEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import com.ironlog.app.core.security.wipe
import com.ironlog.app.core.media.CsvImport
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ironlog.app.core.backup.AutoBackupScheduler
import com.ironlog.app.core.backup.CloudFolder
import com.ironlog.app.core.security.SecureTokenStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** What a restore brought in, so the user is told rather than left to look. */
data class RestoreSummary(
    val workouts: Int,
    val routines: Int,
    val measurements: Int,
    val cardio: Int,
    val exercisesCreated: Int,
    val skipped: Int,
    val foodLogs: Int = 0,
    val goals: Int = 0
) {
    val isEmpty: Boolean get() = workouts + routines + measurements + cardio + foodLogs + goals == 0
}

/** What a backup restored on a fresh phone gave back: the account to sign in to, and its data. */
data class RestoredAccount(val email: String, val displayName: String, val summary: RestoreSummary)

/** Where the weekly backup stands, for the settings screen. */
data class AutoBackupState(
    val enabled: Boolean = false,
    /** When the last automatic backup was written, or null if none has been yet. */
    val lastBackupAt: Long? = null,
    /** Why the last attempt failed, cleared by the next success. */
    val lastError: String? = null,
    /** Whether there is a local copy that could be restored. */
    val hasLocalCopy: Boolean = false,
    /** The cloud folder each backup is also written to, by name, or null when none is chosen. */
    val cloudFolderName: String? = null,
    val lastCloudBackupAt: Long? = null,
    /** Why the last cloud copy failed while the local one succeeded. */
    val lastCloudError: String? = null
)

/** What a file handed to the app turns out to be, decided from its first bytes. */
enum class IncomingKind { SEALED_BACKUP, JSON_BACKUP, CSV }

interface BackupRepository {
    /** The full backup, sealed under a passphrase the caller wipes afterwards. */
    suspend fun exportEncrypted(passphrase: CharArray): Result<File>

    /** Which of the import paths a shared or opened file belongs on. */
    suspend fun classify(uri: Uri): Result<IncomingKind>

    // ------------------------------------------------------------ automatic

    val automatic: Flow<AutoBackupState>

    /**
     * Turns the weekly backup on. The passphrase is kept in keystore-encrypted
     * storage so the scheduled run can seal the file without anyone present;
     * the array is wiped whatever the outcome. The first backup is made right
     * away rather than a week from now.
     */
    suspend fun enableAutomatic(passphrase: CharArray): Result<Unit>

    /** Turns it off, forgets the passphrase and deletes the local copies. */
    suspend fun disableAutomatic(): Result<Unit>

    /** Writes one sealed backup into private storage. Called by the worker, and by "back up now". */
    suspend fun writeAutomaticBackup(): Result<File>

    /** Restores the newest local copy with the stored passphrase. */
    suspend fun restoreLatestAutomatic(): Result<RestoreSummary>

    // ------------------------------------------------------------ cloud copy

    /**
     * Keeps a copy of every automatic backup in a folder the user chose in a
     * cloud storage app. The grant is taken here and held until cleared.
     */
    suspend fun setCloudFolder(treeUri: Uri): Result<Unit>

    /** Stops copying and gives the grant back. Files already in the folder are left alone. */
    suspend fun clearCloudFolder(): Result<Unit>

    /** Restores the newest sealed copy found in the cloud folder, with the stored passphrase. */
    suspend fun restoreLatestCloud(): Result<RestoreSummary>

    /** Whether a chosen file is sealed, so the screen knows to ask for a passphrase. */
    suspend fun isEncrypted(uri: Uri): Result<Boolean>

    /**
     * Restores a backup, sealed or plain, into the signed-in account.
     *
     * Additive and idempotent: a session already in the log -- same start
     * time -- is skipped rather than duplicated, so restoring the same file
     * twice, or restoring over a log that already has half of it, is safe.
     */
    suspend fun restore(uri: Uri, passphrase: CharArray?): Result<RestoreSummary>
    /** Reads a Strong or Hevy CSV export and adds its workouts, matching exercises by name. */
    suspend fun importCsv(uri: Uri): Result<RestoreSummary>

    /**
     * For a phone with no account yet: recreates the account carried in the
     * backup, with the same email and password hash, and restores its data.
     * Needs no session, which is the point. Refused if that email already has
     * an account here, since then the signed-in restore is the right path.
     */
    suspend fun restoreAccount(uri: Uri, passphrase: CharArray?): Result<RestoredAccount>
}

@Singleton
class BackupRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val exportRepository: ExportRepository,
    private val exporter: DataExporter,
    private val crypto: BackupCrypto,
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val routineDao: RoutineDao,
    private val bodyDao: BodyDao,
    private val cardioDao: CardioDao,
    private val userDao: UserDao,
    private val nutritionDao: NutritionDao,
    private val goalDao: StrengthGoalDao,
    private val rewardsDao: RewardsDao,
    private val currentUser: CurrentUser,
    private val dataStore: DataStore<Preferences>,
    private val tokenStore: SecureTokenStore,
    private val scheduler: AutoBackupScheduler,
    private val cloud: CloudFolder
) : BackupRepository {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Separate from the exports folder: that one is pruned to the last few
     * files the user shared, and a weekly backup should neither push those out
     * nor be pushed out by them.
     */
    private val autoDir: File
        get() = File(context.filesDir, "backups").apply { if (!exists()) mkdirs() }

    private fun localCopies(): List<File> =
        autoDir.listFiles { f -> f.isFile && f.name.endsWith(".liftosaur") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    override val automatic: Flow<AutoBackupState> = dataStore.data.map { prefs ->
        AutoBackupState(
            enabled = prefs[KEY_AUTO_ON] ?: false,
            lastBackupAt = prefs[KEY_AUTO_LAST_AT],
            lastError = prefs[KEY_AUTO_LAST_ERROR],
            hasLocalCopy = localCopies().isNotEmpty(),
            cloudFolderName = prefs[KEY_CLOUD_URI]?.let { prefs[KEY_CLOUD_NAME] ?: "Cloud folder" },
            lastCloudBackupAt = prefs[KEY_CLOUD_LAST_AT],
            lastCloudError = prefs[KEY_CLOUD_LAST_ERROR]
        )
    }

    private suspend fun cloudTree(): Uri? = dataStore.data.first()[KEY_CLOUD_URI]?.let(Uri::parse)

    override suspend fun enableAutomatic(passphrase: CharArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            runCatching {
                currentUser.require()
                if (passphrase.size < BackupCrypto.MIN_PASSPHRASE) {
                    throw UserFacingException("Use a passphrase of at least ${BackupCrypto.MIN_PASSPHRASE} characters")
                }
                tokenStore.saveBackupPassphrase(passphrase)
                dataStore.edit { prefs ->
                    prefs[KEY_AUTO_ON] = true
                    prefs.remove(KEY_AUTO_LAST_ERROR)
                }
                scheduler.schedule()
                scheduler.runNow()
            }
        } finally {
            passphrase.wipe()
        }
    }

    override suspend fun disableAutomatic(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            scheduler.cancel()
            tokenStore.saveBackupPassphrase(null)
            localCopies().forEach { it.delete() }
            clearCloudFolder().getOrThrow()
            dataStore.edit { prefs ->
                prefs[KEY_AUTO_ON] = false
                prefs.remove(KEY_AUTO_LAST_AT)
                prefs.remove(KEY_AUTO_LAST_ERROR)
            }
            Unit
        }
    }

    override suspend fun writeAutomaticBackup(): Result<File> = withContext(Dispatchers.IO) {
        val passphrase = tokenStore.backupPassphrase()
        try {
            runCatching {
                if (passphrase == null) throw UserFacingException("Automatic backups are not set up")
                val userId = currentUser.require()
                val payload = exportRepository.backupJson(userId).getOrThrow()
                val sealed = crypto.encrypt(payload.toByteArray(Charsets.UTF_8), passphrase)
                val name = "${CloudFolder.PREFIX}${fileStamp.format(Instant.now())}${CloudFolder.SUFFIX}"
                val file = File(autoDir, name)
                file.writeBytes(sealed)
                localCopies().drop(KEEP_COPIES).forEach { it.delete() }
                dataStore.edit { prefs ->
                    prefs[KEY_AUTO_LAST_AT] = System.currentTimeMillis()
                    prefs.remove(KEY_AUTO_LAST_ERROR)
                }
                copyToCloud(name, sealed)
                file
            }.onFailure { e ->
                // The record of the failure is what the settings screen shows;
                // the failure itself still goes back to the caller.
                if (e !is NotSignedInException) {
                    runCatching {
                        dataStore.edit { prefs -> prefs[KEY_AUTO_LAST_ERROR] = e.userMessage("The last backup failed") }
                    }
                }
            }
        } finally {
            passphrase?.wipe()
        }
    }

    override suspend fun restoreLatestAutomatic(): Result<RestoreSummary> = withContext(Dispatchers.IO) {
        val passphrase = tokenStore.backupPassphrase()
            ?: return@withContext Result.failure(UserFacingException("Automatic backups are not set up"))
        val latest = localCopies().firstOrNull()
            ?: run {
                passphrase.wipe()
                return@withContext Result.failure(UserFacingException("No automatic backup has been made yet"))
            }
        // restore() wipes the passphrase it is given.
        restore(Uri.fromFile(latest), passphrase)
    }

    /**
     * The cloud copy is best effort on top of a local backup that has already
     * succeeded: a provider that is signed out or offline must not turn a
     * good backup into a failed one. The outcome is recorded either way so
     * the settings screen can say what happened.
     */
    private suspend fun copyToCloud(name: String, sealed: ByteArray) {
        val tree = cloudTree() ?: return
        val outcome = runCatching {
            if (!cloud.isGranted(tree)) throw UserFacingException("Access to the cloud folder was revoked. Choose it again.")
            cloud.write(tree, name, sealed)
            cloud.listBackups(tree).drop(KEEP_COPIES).forEach { cloud.delete(it.uri) }
        }
        dataStore.edit { prefs ->
            outcome.fold(
                onSuccess = {
                    prefs[KEY_CLOUD_LAST_AT] = System.currentTimeMillis()
                    prefs.remove(KEY_CLOUD_LAST_ERROR)
                },
                onFailure = { e -> prefs[KEY_CLOUD_LAST_ERROR] = e.userMessage("The copy to the cloud folder failed") }
            )
        }
    }

    override suspend fun setCloudFolder(treeUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            cloud.take(treeUri)
            // Prove the folder can be listed before promising to write to it.
            cloud.listBackups(treeUri)
            cloudTree()?.takeIf { it != treeUri }?.let(cloud::release)
            dataStore.edit { prefs ->
                prefs[KEY_CLOUD_URI] = treeUri.toString()
                prefs[KEY_CLOUD_NAME] = cloud.displayName(treeUri) ?: "Cloud folder"
                prefs.remove(KEY_CLOUD_LAST_AT)
                prefs.remove(KEY_CLOUD_LAST_ERROR)
            }
            // A copy of the newest local backup goes up straight away, so the
            // folder is not empty until next week.
            localCopies().firstOrNull()?.let { copyToCloud(it.name, it.readBytes()) }
            Unit
        }
    }

    override suspend fun clearCloudFolder(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            cloudTree()?.let(cloud::release)
            dataStore.edit { prefs ->
                prefs.remove(KEY_CLOUD_URI)
                prefs.remove(KEY_CLOUD_NAME)
                prefs.remove(KEY_CLOUD_LAST_AT)
                prefs.remove(KEY_CLOUD_LAST_ERROR)
            }
            Unit
        }
    }

    override suspend fun restoreLatestCloud(): Result<RestoreSummary> = withContext(Dispatchers.IO) {
        val tree = cloudTree()
            ?: return@withContext Result.failure(UserFacingException("No cloud folder is set up"))
        val latest = runCatching { cloud.listBackups(tree).firstOrNull() }
            .getOrElse { return@withContext Result.failure(UserFacingException("The cloud folder could not be read. Choose it again.")) }
            ?: return@withContext Result.failure(UserFacingException("No backup has reached the cloud folder yet"))
        val passphrase = tokenStore.backupPassphrase()
            ?: return@withContext Result.failure(UserFacingException("Automatic backups are not set up"))
        restore(latest.uri, passphrase)
    }

    override suspend fun exportEncrypted(passphrase: CharArray): Result<File> = withContext(Dispatchers.IO) {
        try {
            runCatching {
                if (passphrase.size < BackupCrypto.MIN_PASSPHRASE) {
                    throw UserFacingException("Use a passphrase of at least ${BackupCrypto.MIN_PASSPHRASE} characters")
                }
                // The plain backup is built by the exporter and sealed here; the
                // plain file it wrote on the way is removed so only the sealed
                // one is left to share.
                val plain = exportRepository.export(ExportKind.FULL_JSON).getOrThrow()
                val sealed = crypto.encrypt(plain.readBytes(), passphrase)
                plain.delete()
                exporter.exportBinary(sealed, "liftosaur").getOrThrow()
            }
        } finally {
            passphrase.wipe()
        }
    }

    override suspend fun importCsv(uri: Uri): Result<RestoreSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = currentUser.require()
            val text = read(uri).decodeToString()
            val parsed = try {
                CsvImport.parse(text)
            } catch (e: CsvImport.UnrecognisedException) {
                throw UserFacingException("That is not a Strong or Hevy export. Export as CSV from the other app and try again.")
            }
            if (parsed.backup.workouts.isEmpty()) throw UserFacingException("No workouts found in that file")
            db.withTransaction { restoreInto(userId, parsed.backup) }
        }
    }

    override suspend fun isEncrypted(uri: Uri): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching { crypto.looksLikeBackup(readHead(uri)) }
    }

    override suspend fun classify(uri: Uri): Result<IncomingKind> = withContext(Dispatchers.IO) {
        runCatching {
            val head = readHead(uri, HEAD_BYTES)
            when {
                crypto.looksLikeBackup(head) -> IncomingKind.SEALED_BACKUP
                // A JSON backup opens with a brace; anything else with a text
                // header is treated as a CSV and left to the parser to reject.
                head.decodeToString().trimStart(BOM, ' ', '\t', '\r', '\n').startsWith("{") -> IncomingKind.JSON_BACKUP
                else -> IncomingKind.CSV
            }
        }
    }

    override suspend fun restore(uri: Uri, passphrase: CharArray?): Result<RestoreSummary> =
        withContext(Dispatchers.IO) {
            try {
                runCatching {
                    val userId = currentUser.require()
                    val bytes = read(uri)
                    val plain = if (crypto.looksLikeBackup(bytes)) {
                        val pass = passphrase ?: throw UserFacingException("This backup needs its passphrase")
                        try {
                            crypto.decrypt(bytes, pass)
                        } catch (e: BackupCrypto.WrongPassphraseException) {
                            throw UserFacingException(e.message ?: "Wrong passphrase")
                        }
                    } else {
                        bytes
                    }
                    val backup = runCatching { json.decodeFromString(Backup.serializer(), plain.decodeToString()) }
                        .getOrElse { throw UserFacingException("That file is not a Liftosaur backup") }
                    if (backup.format != "ironlog-backup") throw UserFacingException("That file is not a Liftosaur backup")

                    db.withTransaction { restoreInto(userId, backup) }
                }
            } finally {
                passphrase?.wipe()
            }
        }

    private suspend fun restoreInto(userId: String, backup: Backup): RestoreSummary {
        var skipped = 0
        var created = 0
        val knownExercises = HashMap<String, ExerciseEntity>()

        /** The exercise a backed-up row refers to, created as a custom one if it is gone. */
        suspend fun exercise(id: String, name: String): ExerciseEntity {
            knownExercises[id]?.let { return it }
            exerciseDao.findById(id)?.let { knownExercises[id] = it; return it }
            // An imported row carries another app's name, not one of our ids:
            // the library entry of the same name is the same movement.
            exerciseDao.findByName(name.trim(), userId)?.let { knownExercises[id] = it; return it }
            val made = ExerciseEntity(
                id = UUID.randomUUID().toString(),
                name = Validators.sanitiseSingleLine(name, 60).ifBlank { "Restored exercise" },
                primaryMuscle = MuscleGroup.FULL_BODY,
                category = ExerciseCategory.BODYWEIGHT,
                ownerUserId = userId,
                isCustom = true
            )
            exerciseDao.upsert(made)
            created++
            knownExercises[id] = made
            return made
        }

        val existingStarts = workoutDao.allFinished(userId).map { it.workout.startedAt }.toHashSet()
        var workouts = 0
        backup.workouts.forEach { w ->
            if (w.startedAt in existingStarts) { skipped++; return@forEach }
            val workoutId = UUID.randomUUID().toString()
            val finishedAt = w.finishedAt ?: w.startedAt
            var volume = 0.0
            var sets = 0
            var reps = 0
            val exerciseRows = ArrayList<WorkoutExerciseEntity>()
            val setRows = ArrayList<WorkoutSetEntity>()
            w.exercises.forEachIndexed { index, ex ->
                val resolved = exercise(ex.exerciseId, ex.name)
                val rowId = UUID.randomUUID().toString()
                exerciseRows += WorkoutExerciseEntity(
                    id = rowId,
                    workoutId = workoutId,
                    exerciseId = resolved.id,
                    orderIndex = index,
                    notes = Validators.sanitiseText(ex.notes, 500)
                )
                ex.sets.forEach { s ->
                    val type = runCatching { SetType.valueOf(s.type) }.getOrDefault(SetType.NORMAL)
                    setRows += WorkoutSetEntity(
                        id = UUID.randomUUID().toString(),
                        workoutExerciseId = rowId,
                        setNumber = s.setNumber,
                        type = type,
                        weightKg = s.weightKg,
                        reps = s.reps,
                        rpe = s.rpe,
                        distanceMeters = s.distanceMeters,
                        durationSeconds = s.durationSeconds,
                        isCompleted = true,
                        completedAt = finishedAt
                    )
                    if (type.countsTowardVolume) {
                        sets++
                        reps += s.reps ?: 0
                        volume += (s.weightKg ?: 0.0) * (s.reps ?: 0)
                    }
                }
            }
            workoutDao.upsertWorkout(
                WorkoutEntity(
                    id = workoutId,
                    userId = userId,
                    name = Validators.sanitiseSingleLine(w.name, 60).ifBlank { "Restored workout" },
                    startedAt = w.startedAt,
                    finishedAt = finishedAt,
                    notes = Validators.sanitiseText(w.notes, 1000),
                    isActive = false,
                    totalVolumeKg = volume,
                    totalSets = sets,
                    totalReps = reps,
                    durationSeconds = ((finishedAt - w.startedAt) / 1000).toInt().coerceAtLeast(0)
                )
            )
            workoutDao.upsertWorkoutExercises(exerciseRows)
            workoutDao.upsertSets(setRows)
            existingStarts += w.startedAt
            workouts++
        }

        val existingRoutines = routineDao.observeRoutines(userId).first().map { it.routine.name }.toHashSet()
        var routines = 0
        backup.routines.forEach { r ->
            val name = Validators.sanitiseSingleLine(r.name, 60).ifBlank { "Restored routine" }
            if (name in existingRoutines) { skipped++; return@forEach }
            val routineId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            routineDao.upsertRoutine(
                RoutineEntity(id = routineId, userId = userId, name = name, folder = r.folder.ifBlank { "My routines" }, createdAt = now, updatedAt = now)
            )
            routineDao.upsertRoutineExercises(
                r.exercises.mapIndexed { index, e ->
                    RoutineExerciseEntity(
                        id = UUID.randomUUID().toString(),
                        routineId = routineId,
                        exerciseId = exercise(e.exerciseId, e.name).id,
                        orderIndex = index,
                        targetSets = e.targetSets,
                        targetRepsLow = e.targetRepsLow,
                        targetRepsHigh = e.targetRepsHigh,
                        restSeconds = e.restSeconds
                    )
                }
            )
            existingRoutines += name
            routines++
        }

        val existingTaken = bodyDao.observeMeasurements(userId).first().map { it.takenAt }.toHashSet()
        var measurements = 0
        backup.measurements.forEach { m ->
            if (m.takenAt in existingTaken) { skipped++; return@forEach }
            bodyDao.upsertMeasurement(
                BodyMeasurementEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    takenAt = m.takenAt,
                    weightKg = m.weightKg,
                    bodyFatPercent = m.bodyFatPercent,
                    chestCm = m.chestCm,
                    waistCm = m.waistCm,
                    note = Validators.sanitiseText(m.note, 300)
                )
            )
            measurements++
        }

        val existingCardio = cardioDao.observeSessions(userId).first().map { it.startedAt }.toHashSet()
        var cardio = 0
        backup.cardio.forEach { c ->
            if (c.startedAt in existingCardio) { skipped++; return@forEach }
            val km = c.distanceMeters / 1000.0
            cardioDao.upsertSession(
                CardioSessionEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    type = runCatching { CardioType.valueOf(c.type) }.getOrDefault(CardioType.RUN),
                    startedAt = c.startedAt,
                    finishedAt = c.startedAt + c.durationSeconds * 1000L,
                    distanceMeters = c.distanceMeters,
                    durationSeconds = c.durationSeconds,
                    avgPaceSecondsPerKm = if (km > 0) (c.durationSeconds / km).toInt() else 0,
                    steps = c.steps,
                    routeJson = c.routeJson
                )
            )
            cardio++
        }

        // ---------------------------------------------------------- nutrition
        // Custom foods first, so the logs that refer to them have something to
        // point at. Seeded foods share ids across phones; a custom one is
        // matched by name, and created from the backup's own copy if missing.
        val knownFoods = HashMap<String, String>()
        backup.foods.forEach { f ->
            val name = Validators.sanitiseSingleLine(f.name, 80)
            val existing = nutritionDao.findFood(f.id)?.takeIf { it.ownerUserId == null || it.ownerUserId == userId }
                ?: nutritionDao.findFoodByName(userId, name)
            if (existing != null) { knownFoods[f.id] = existing.id; return@forEach }
            val id = UUID.randomUUID().toString()
            nutritionDao.upsertFood(
                FoodEntity(
                    id = id, name = name.ifBlank { "Restored food" },
                    group = runCatching { FoodGroup.valueOf(f.group) }.getOrDefault(FoodGroup.entries.first()),
                    kcalPer100g = f.kcalPer100g, proteinPer100g = f.proteinPer100g, carbsPer100g = f.carbsPer100g,
                    fatPer100g = f.fatPer100g, servingGrams = f.servingGrams,
                    servingLabel = Validators.sanitiseSingleLine(f.servingLabel, 40), ownerUserId = userId,
                    isCustom = true, barcode = f.barcode?.takeIf { it.all(Char::isDigit) && it.length in 6..14 }
                )
            )
            knownFoods[f.id] = id
        }
        val existingLogs = nutritionDao.allLogs(userId).map { it.loggedAt to it.foodId }.toHashSet()
        var foodLogs = 0
        backup.foodLogs.forEach { log ->
            val foodId = knownFoods[log.foodId]
                ?: nutritionDao.findFood(log.foodId)?.takeIf { it.ownerUserId == null || it.ownerUserId == userId }?.id
                ?: nutritionDao.findFoodByName(userId, log.foodName.trim())?.id
                ?: run { skipped++; return@forEach }
            if ((log.loggedAt to foodId) in existingLogs) { skipped++; return@forEach }
            nutritionDao.upsertLog(
                FoodLogEntity(
                    id = UUID.randomUUID().toString(), userId = userId, foodId = foodId,
                    grams = log.grams.coerceIn(0.0, 5000.0),
                    meal = runCatching { Meal.valueOf(log.meal) }.getOrDefault(Meal.entries.first()),
                    loggedAt = log.loggedAt, note = Validators.sanitiseText(log.note, 300)
                )
            )
            existingLogs += log.loggedAt to foodId
            foodLogs++
        }
        backup.nutritionGoal?.let { g ->
            val current = nutritionDao.findGoal(userId)
            if (current == null || current.updatedAt < g.updatedAt) {
                nutritionDao.upsertGoal(
                    NutritionGoalEntity(
                        userId = userId,
                        goal = runCatching { DietGoal.valueOf(g.goal) }.getOrDefault(DietGoal.entries.first()),
                        sex = runCatching { Sex.valueOf(g.sex) }.getOrDefault(Sex.entries.first()),
                        ageYears = g.ageYears.coerceIn(10, 120),
                        activity = runCatching { ActivityLevel.valueOf(g.activity) }.getOrDefault(ActivityLevel.entries.first()),
                        targetKcal = g.targetKcal.coerceIn(0, 20_000), proteinG = g.proteinG.coerceIn(0, 2_000),
                        carbsG = g.carbsG.coerceIn(0, 4_000), fatG = g.fatG.coerceIn(0, 2_000), updatedAt = g.updatedAt
                    )
                )
            }
        }

        // ---------------------------------------------------------- goals, rewards, settings
        var goals = 0
        backup.strengthGoals.forEach { g ->
            val metric = runCatching { GoalMetric.valueOf(g.metric) }.getOrNull() ?: run { skipped++; return@forEach }
            val exerciseId = exercise(g.exerciseId, g.exerciseName).id
            if (goalDao.findFor(userId, exerciseId, metric) != null) { skipped++; return@forEach }
            goalDao.upsert(
                StrengthGoalEntity(
                    id = UUID.randomUUID().toString(), userId = userId, exerciseId = exerciseId, metric = metric,
                    targetKg = g.targetKg.coerceIn(0.0, Validators.MAX_WEIGHT_KG), createdAt = g.createdAt, achievedAt = g.achievedAt
                )
            )
            goals++
        }
        backup.questClaims.forEach { c ->
            if (rewardsDao.claimCount(userId, c.questId, c.periodKey) > 0) return@forEach
            rewardsDao.upsertClaim(
                QuestClaimEntity(
                    id = UUID.randomUUID().toString(), userId = userId, questId = c.questId,
                    periodKey = c.periodKey, coins = c.coins.coerceIn(0, 100_000), claimedAt = c.claimedAt
                )
            )
        }
        backup.purchases.forEach { p ->
            if (rewardsDao.findPurchase(userId, p.itemId) != null) return@forEach
            rewardsDao.upsertPurchase(
                StorePurchaseEntity(
                    id = UUID.randomUUID().toString(), userId = userId, itemId = p.itemId,
                    coinsSpent = p.coinsSpent.coerceIn(0, 100_000), purchasedAt = p.purchasedAt, isEquipped = p.isEquipped
                )
            )
        }
        if (backup.settings.isNotEmpty()) {
            dataStore.edit { prefs -> SettingsBackup.apply(backup.settings, prefs) }
        }

        return RestoreSummary(workouts, routines, measurements, cardio, created, skipped, foodLogs, goals)
    }

    override suspend fun restoreAccount(uri: Uri, passphrase: CharArray?): Result<RestoredAccount> =
        withContext(Dispatchers.IO) {
            try {
                runCatching {
                    val backup = decodeBackup(read(uri), passphrase)
                    val account = backup.account
                        ?: throw UserFacingException("This backup was made before accounts were included. Create an account, then restore it from Settings.")
                    val email = Validators.sanitiseEmail(account.email)
                    if (userDao.countByEmail(email) > 0) {
                        throw UserFacingException("An account for ${account.email} already exists on this phone. Sign in to it and restore from Settings.")
                    }
                    if (account.passwordHash.isBlank() || account.passwordSalt.isBlank()) {
                        throw UserFacingException("The account in this backup cannot be signed in to.")
                    }
                    val user = UserEntity(
                        id = account.id.ifBlank { UUID.randomUUID().toString() },
                        email = email,
                        displayName = Validators.sanitiseSingleLine(account.displayName, 60).ifBlank { "Restored" },
                        passwordHash = account.passwordHash,
                        passwordSalt = account.passwordSalt,
                        createdAt = account.createdAt,
                        unitSystem = runCatching { UnitSystem.valueOf(account.unitSystem) }.getOrDefault(UnitSystem.METRIC),
                        bodyweightKg = account.bodyweightKg?.takeIf { it in 20.0..500.0 },
                        heightCm = account.heightCm?.takeIf { it in 50.0..300.0 },
                        // The lock is this phone's decision, not the old one's.
                        biometricLockEnabled = false,
                        restTimerDefaultSeconds = account.restTimerDefaultSeconds.coerceIn(15, 600),
                        autoRestTimerEnabled = account.autoRestTimerEnabled,
                        weeklyGoalWorkouts = account.weeklyGoalWorkouts.coerceIn(1, 14)
                    )
                    val summary = db.withTransaction {
                        userDao.insert(user)
                        restoreInto(user.id, backup)
                    }
                    RestoredAccount(user.email, user.displayName, summary)
                }
            } finally {
                passphrase?.wipe()
            }
        }

    /** Opens a backup file's bytes: unsealed with the passphrase if it needs one, then parsed. */
    private fun decodeBackup(bytes: ByteArray, passphrase: CharArray?): Backup {
        val plain = if (crypto.looksLikeBackup(bytes)) {
            val pass = passphrase ?: throw UserFacingException("This backup needs its passphrase")
            try {
                crypto.decrypt(bytes, pass)
            } catch (e: BackupCrypto.WrongPassphraseException) {
                throw UserFacingException(e.message ?: "Wrong passphrase")
            }
        } else {
            bytes
        }
        val backup = runCatching { json.decodeFromString(Backup.serializer(), plain.decodeToString()) }
            .getOrElse { throw UserFacingException("That file is not a Liftosaur backup") }
        if (backup.format != "ironlog-backup") throw UserFacingException("That file is not a Liftosaur backup")
        return backup
    }

    private fun read(uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val bytes = stream.readBytes()
            if (bytes.size > MAX_BACKUP_BYTES) throw UserFacingException("That file is too large to be a backup")
            bytes
        } ?: throw UserFacingException("Could not open that file")

    private fun readHead(uri: Uri, bytes: Int = BackupCrypto.MAGIC.size + 1): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val head = ByteArray(bytes)
            val n = stream.read(head)
            head.copyOf(n.coerceAtLeast(0))
        } ?: throw UserFacingException("Could not open that file")

    private val fileStamp: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm").withZone(ZoneId.systemDefault())

    private companion object {
        /** A log is text; a hundred megabytes of it is not a backup. */
        const val MAX_BACKUP_BYTES = 100L * 1024 * 1024

        /** A month of weekly copies: enough to reach back past a bad week, small enough to forget about. */
        const val KEEP_COPIES = 4

        /** Enough of a file to see past a byte-order mark and leading whitespace to its first token. */
        const val HEAD_BYTES = 64

        /** The UTF-8 byte-order mark, built from its code point so no editor can turn it into an invisible literal. */
        val BOM: Char = 0xFEFF.toChar()

        val KEY_AUTO_ON = booleanPreferencesKey("auto_backup_enabled")
        val KEY_AUTO_LAST_AT = longPreferencesKey("auto_backup_last_at")
        val KEY_AUTO_LAST_ERROR = stringPreferencesKey("auto_backup_last_error")
        val KEY_CLOUD_URI = stringPreferencesKey("auto_backup_cloud_uri")
        val KEY_CLOUD_NAME = stringPreferencesKey("auto_backup_cloud_name")
        val KEY_CLOUD_LAST_AT = longPreferencesKey("auto_backup_cloud_last_at")
        val KEY_CLOUD_LAST_ERROR = stringPreferencesKey("auto_backup_cloud_last_error")
    }
}
