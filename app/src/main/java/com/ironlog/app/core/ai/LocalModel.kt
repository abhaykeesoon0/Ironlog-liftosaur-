package com.ironlog.app.core.ai

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ironlog.app.core.notifications.NotificationHelper
import com.ironlog.app.data.repository.UserFacingException
import com.ironlog.app.data.repository.userMessage
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The language models the coach can run on the phone itself.
 *
 * A model is a file the user downloads once, from a public repository that
 * needs no account and no key, and keeps in the app's private storage. After
 * that the coach answers on the handset with nothing leaving it. The two
 * sizes are the same family, quantised to eight bits, in the bundle format
 * the on-device runtime reads directly.
 *
 * The checksum is the repository's own for the file, so a download that was
 * cut short, tampered with or swapped is refused before it is ever loaded.
 */
enum class LocalModel(
    val id: String,
    val label: String,
    val blurb: String,
    val fileName: String,
    val url: String,
    val bytes: Long,
    val sha256: String
) {
    COMPACT(
        id = "qwen2.5-0.5b-q8",
        label = "Compact",
        blurb = "550 MB. Quick answers on any recent phone.",
        fileName = "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        url = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/" +
            "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        bytes = 546_660_344L,
        sha256 = "e608953f169aeb1bd7b9155fec2559825e08453fc209b84eda3a781ed0452fd2"
    ),
    BETTER(
        id = "qwen2.5-1.5b-q8",
        label = "Better answers",
        blurb = "1.6 GB. Clearer reasoning; wants a phone with 6 GB of memory or more.",
        fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        url = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/" +
            "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        bytes = 1_597_913_616L,
        sha256 = "8d867a7c93a6acf2892f08e0174e2f6f351ad256b7e3cfb6d6cd9c89794b42e0"
    );

    /** Rounded the way a person would say it: to the nearest ten megabytes, or a tenth of a gigabyte. */
    val sizeLabel: String
        get() = if (bytes >= 1_000_000_000L) "%.1f GB".format(bytes / 1e9)
        else "${Math.round(bytes / 10_000_000.0) * 10} MB"

    companion object {
        fun byId(id: String?): LocalModel? = entries.firstOrNull { it.id == id }
    }
}

/** Where the phone stands with its model, for the coach screen. */
data class LocalModelState(
    val model: LocalModel? = null,
    val status: Status = Status.NONE,
    val percent: Int = 0,
    val error: String? = null
) {
    enum class Status { NONE, DOWNLOADING, READY }

    val isReady: Boolean get() = status == Status.READY && model != null
}

/** Streams a file through SHA-256 without holding it in memory. */
object Sha256 {
    fun of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/**
 * Keeps the downloaded model file and the record of how it got there.
 *
 * Readiness is decided from the file itself, not a flag: a file of the right
 * name and size in the models folder is ready, and anything else is not.
 * Preferences only carry what the file cannot say, which is that a download
 * is in flight and how far it has got.
 */
@Singleton
class LocalModelStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) {
    private val dir: File get() = File(context.filesDir, "models").apply { if (!exists()) mkdirs() }

    fun file(model: LocalModel): File = File(dir, model.fileName)
    fun partFile(model: LocalModel): File = File(dir, model.fileName + ".part")

    private fun isComplete(model: LocalModel): Boolean = file(model).let { it.exists() && it.length() == model.bytes }

    val state: Flow<LocalModelState> = dataStore.data.map { prefs ->
        val model = LocalModel.byId(prefs[KEY_MODEL])
        when {
            model == null -> LocalModelState()
            isComplete(model) -> LocalModelState(model, LocalModelState.Status.READY)
            prefs[KEY_DOWNLOADING] == model.id ->
                LocalModelState(model, LocalModelState.Status.DOWNLOADING, prefs[KEY_PERCENT] ?: 0, prefs[KEY_ERROR])
            else -> LocalModelState(model, LocalModelState.Status.NONE, 0, prefs[KEY_ERROR])
        }
    }

    /** The model the coach may use right now, or null. Cheap: one file stat. */
    suspend fun ready(): LocalModel? {
        val model = LocalModel.byId(dataStore.data.first()[KEY_MODEL]) ?: return null
        return model.takeIf { isComplete(it) }
    }

    /**
     * Starts the download as foreground work. Unmetered only unless the user
     * has said mobile data is fine: a file this size on a data plan should be
     * a choice, not a surprise.
     */
    suspend fun startDownload(model: LocalModel, allowMetered: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // A different model than before: the old file goes, so two never sit side by side.
            LocalModel.entries.filter { it != model }.forEach { file(it).delete(); partFile(it).delete() }
            dataStore.edit { prefs ->
                prefs[KEY_MODEL] = model.id
                prefs[KEY_DOWNLOADING] = model.id
                prefs[KEY_PERCENT] = 0
                prefs.remove(KEY_ERROR)
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (allowMetered) NetworkType.CONNECTED else NetworkType.UNMETERED)
                .setRequiresStorageNotLow(true)
                .build()
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(ModelDownloadWorker.KEY_MODEL_ID to model.id))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
            Unit
        }
    }

    suspend fun cancelDownload(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            LocalModel.entries.forEach { partFile(it).delete() }
            dataStore.edit { prefs ->
                prefs.remove(KEY_DOWNLOADING)
                prefs.remove(KEY_PERCENT)
                prefs.remove(KEY_ERROR)
                prefs.remove(KEY_MODEL)
            }
            Unit
        }
    }

    /** Deletes the model and forgets it; the coach goes back to its rules. */
    suspend fun remove(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            LocalModel.entries.forEach { file(it).delete(); partFile(it).delete() }
            dataStore.edit { prefs ->
                prefs.remove(KEY_MODEL); prefs.remove(KEY_DOWNLOADING); prefs.remove(KEY_PERCENT); prefs.remove(KEY_ERROR)
            }
            Unit
        }
    }

    internal suspend fun recordProgress(percent: Int) {
        dataStore.edit { prefs -> prefs[KEY_PERCENT] = percent }
    }

    internal suspend fun recordDone() {
        dataStore.edit { prefs -> prefs.remove(KEY_DOWNLOADING); prefs.remove(KEY_PERCENT); prefs.remove(KEY_ERROR) }
    }

    internal suspend fun recordFailure(message: String, final: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_ERROR] = message
            if (final) { prefs.remove(KEY_DOWNLOADING); prefs.remove(KEY_PERCENT) }
        }
    }

    private companion object {
        const val WORK_NAME = "coach_model_download"
        val KEY_MODEL = stringPreferencesKey("coach_model_id")
        val KEY_DOWNLOADING = stringPreferencesKey("coach_model_downloading")
        val KEY_PERCENT = intPreferencesKey("coach_model_percent")
        val KEY_ERROR = stringPreferencesKey("coach_model_error")
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ModelDownloadEntryPoint {
    fun store(): LocalModelStore
    fun notifications(): NotificationHelper
}

/**
 * Fetches the model file, resuming a part-finished download where the
 * server allows it, and checks the whole file against its published hash
 * before it is allowed to become the model.
 */
class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication(applicationContext, ModelDownloadEntryPoint::class.java)
        val store = entry.store()
        val notifications = entry.notifications()
        val model = LocalModel.byId(inputData.getString(KEY_MODEL_ID)) ?: return Result.failure()

        setForeground(foregroundInfo(notifications, model, 0))
        return try {
            download(model, store) { percent ->
                store.recordProgress(percent)
                runCatching { setForeground(foregroundInfo(notifications, model, percent)) }
            }
            store.recordDone()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val giveUp = runAttemptCount >= MAX_ATTEMPTS - 1 || e is UserFacingException
            store.recordFailure(e.userMessage("The download failed. Check the connection and try again."), final = giveUp)
            if (giveUp) {
                Log.e("IronLog", "Coach model download failed", e)
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }

    private suspend fun download(model: LocalModel, store: LocalModelStore, onProgress: suspend (Int) -> Unit) {
        val part = store.partFile(model)
        // A stopped attempt -- the network constraint lapsing for a moment is
        // enough -- leaves a part file behind. Whatever is there is picked up
        // rather than fetched again, and a part that already holds the whole
        // file goes straight to the checks.
        if (part.length() > model.bytes) part.delete()
        if (part.length() < model.bytes) fetch(model, part, onProgress)

        if (part.length() != model.bytes) {
            part.delete()
            throw UserFacingException("The download came down the wrong size. Try again.")
        }
        // The published hash is the only proof this is the file it claims to be.
        if (!Sha256.of(part).equals(model.sha256, ignoreCase = true)) {
            part.delete()
            throw UserFacingException("The downloaded file did not match its checksum and was discarded.")
        }
        if (!part.renameTo(store.file(model))) throw UserFacingException("Could not place the model file.")
        onProgress(100)
    }

    /** Streams the remainder of the file into [part], asking the server to continue where the part ends. */
    private suspend fun fetch(model: LocalModel, part: File, onProgress: suspend (Int) -> Unit) {
        val already = part.length()
        val connection = (URL(model.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("user-agent", USER_AGENT)
            if (already > 0) setRequestProperty("Range", "bytes=$already-")
        }
        try {
            val status = connection.responseCode
            if (status == 416) {
                // The server says our part is past its end: the part is not what
                // we think it is. Start over on the next attempt.
                part.delete()
                throw IllegalStateException("Resume rejected; restarting the download")
            }
            if (status !in 200..299) throw UserFacingException("The model server answered with an error ($status).")
            val resuming = status == 206 && already > 0
            if (!resuming) part.delete()
            var written = if (resuming) already else 0L
            var lastPercent = -1
            connection.inputStream.use { input ->
                java.io.FileOutputStream(part, resuming).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        written += n
                        val percent = ((written * 100) / model.bytes).toInt().coerceIn(0, 99)
                        // Every couple of percent: each report is a preference
                        // write and a notification, which is plenty at that rate.
                        if (percent / 2 != lastPercent / 2) { lastPercent = percent; onProgress(percent) }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun foregroundInfo(notifications: NotificationHelper, model: LocalModel, percent: Int): ForegroundInfo {
        val notification = notifications.modelDownload(model.label, percent)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NotificationHelper.ID_MODEL_DOWNLOAD, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NotificationHelper.ID_MODEL_DOWNLOAD, notification)
        }
    }

    companion object {
        const val KEY_MODEL_ID = "model_id"
        private const val MAX_ATTEMPTS = 4
        private const val TIMEOUT_MS = 30_000
        private const val USER_AGENT = "Liftosaur/1.0 (Android)"
    }
}
