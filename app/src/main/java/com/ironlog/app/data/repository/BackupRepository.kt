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

/** What a restore brought in, so the user is told rather than left to look. */
data class RestoreSummary(
    val workouts: Int,
    val routines: Int,
    val measurements: Int,
    val cardio: Int,
    val exercisesCreated: Int,
    val skipped: Int
) {
    val isEmpty: Boolean get() = workouts + routines + measurements + cardio == 0
}

interface BackupRepository {
    /** The full backup, sealed under a passphrase the caller wipes afterwards. */
    suspend fun exportEncrypted(passphrase: CharArray): Result<File>

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
    private val currentUser: CurrentUser
) : BackupRepository {

    private val json = Json { ignoreUnknownKeys = true }

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

        return RestoreSummary(workouts, routines, measurements, cardio, created, skipped)
    }

    private fun read(uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val bytes = stream.readBytes()
            if (bytes.size > MAX_BACKUP_BYTES) throw UserFacingException("That file is too large to be a backup")
            bytes
        } ?: throw UserFacingException("Could not open that file")

    private fun readHead(uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val head = ByteArray(BackupCrypto.MAGIC.size + 1)
            val n = stream.read(head)
            head.copyOf(n.coerceAtLeast(0))
        } ?: throw UserFacingException("Could not open that file")

    private companion object {
        /** A log is text; a hundred megabytes of it is not a backup. */
        const val MAX_BACKUP_BYTES = 100L * 1024 * 1024
    }
}
