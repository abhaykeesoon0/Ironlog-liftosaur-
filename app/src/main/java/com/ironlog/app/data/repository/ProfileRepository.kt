package com.ironlog.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.ironlog.app.core.media.PhotoStore
import com.ironlog.app.core.notifications.ReminderScheduler
import com.ironlog.app.core.security.PasswordHasher
import com.ironlog.app.core.security.Validators
import com.ironlog.app.core.security.wipe
import com.ironlog.app.data.dao.BodyDao
import com.ironlog.app.data.dao.ExerciseDao
import com.ironlog.app.data.dao.UserDao
import com.ironlog.app.data.entity.UnitSystem
import com.ironlog.app.data.entity.UserEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ReminderSettings(
    val enabled: Boolean = false,
    val hour: Int = 18,
    val minute: Int = 0
)

interface ProfileRepository {
    fun observeUser(): Flow<UserEntity?>
    val reminderSettings: Flow<ReminderSettings>

    suspend fun updateProfile(name: String, bodyweightKg: Double?, heightCm: Double?): Result<Unit>
    suspend fun setUnitSystem(unit: UnitSystem): Result<Unit>
    suspend fun setBiometricLock(enabled: Boolean): Result<Unit>
    suspend fun setRestTimer(defaultSeconds: Int, autoStart: Boolean): Result<Unit>
    suspend fun setWeeklyGoal(workouts: Int): Result<Unit>
    suspend fun setAvatar(path: String?): Result<Unit>
    suspend fun setReminder(settings: ReminderSettings): Result<Unit>

    /** Requires the password again: deletion is irreversible and wipes every row the user owns. */
    suspend fun deleteAccount(password: CharArray): Result<Unit>
}

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userDao: UserDao,
    private val exerciseDao: ExerciseDao,
    private val bodyDao: BodyDao,
    private val hasher: PasswordHasher,
    private val photoStore: PhotoStore,
    private val dataStore: DataStore<Preferences>,
    private val reminderScheduler: ReminderScheduler,
    private val authRepository: AuthRepository,
    private val currentUser: CurrentUser
) : ProfileRepository {

    override fun observeUser(): Flow<UserEntity?> =
        currentUser.idOrNull()?.let { userDao.observeById(it) } ?: flowOf(null)

    override val reminderSettings: Flow<ReminderSettings> = dataStore.data.map { prefs ->
        ReminderSettings(
            enabled = prefs[KEY_REMINDER_ON] ?: false,
            hour = prefs[KEY_REMINDER_HOUR] ?: 18,
            minute = prefs[KEY_REMINDER_MINUTE] ?: 0
        )
    }

    override suspend fun updateProfile(name: String, bodyweightKg: Double?, heightCm: Double?): Result<Unit> =
        updateUser { user ->
            val clean = Validators.sanitiseSingleLine(name)
            Validators.validateDisplayName(clean)?.let { throw UserFacingException(it) }
            bodyweightKg?.let { if (it !in 20.0..400.0) throw UserFacingException("Check the bodyweight") }
            heightCm?.let { if (it !in 80.0..260.0) throw UserFacingException("Check the height") }
            user.copy(displayName = clean, bodyweightKg = bodyweightKg, heightCm = heightCm)
        }

    override suspend fun setUnitSystem(unit: UnitSystem): Result<Unit> = updateUser { it.copy(unitSystem = unit) }

    override suspend fun setBiometricLock(enabled: Boolean): Result<Unit> =
        updateUser { it.copy(biometricLockEnabled = enabled) }

    override suspend fun setRestTimer(defaultSeconds: Int, autoStart: Boolean): Result<Unit> = updateUser {
        if (defaultSeconds !in 15..600) throw UserFacingException("Rest must be between 15 s and 10 min")
        it.copy(restTimerDefaultSeconds = defaultSeconds, autoRestTimerEnabled = autoStart)
    }

    override suspend fun setWeeklyGoal(workouts: Int): Result<Unit> = updateUser {
        if (workouts !in 1..14) throw UserFacingException("Pick a goal between 1 and 14")
        it.copy(weeklyGoalWorkouts = workouts)
    }

    override suspend fun setAvatar(path: String?): Result<Unit> = updateUser { user ->
        user.avatarPath?.takeIf { it != path }?.let { photoStore.delete(it) }
        user.copy(avatarPath = path)
    }

    override suspend fun setReminder(settings: ReminderSettings): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (settings.hour !in 0..23 || settings.minute !in 0..59) throw UserFacingException("Pick a valid time")
            dataStore.edit { prefs ->
                prefs[KEY_REMINDER_ON] = settings.enabled
                prefs[KEY_REMINDER_HOUR] = settings.hour
                prefs[KEY_REMINDER_MINUTE] = settings.minute
            }
            if (settings.enabled) {
                reminderScheduler.schedule(settings.hour, settings.minute)
            } else {
                reminderScheduler.cancel()
            }
        }
    }

    override suspend fun deleteAccount(password: CharArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            runCatching {
                val userId = currentUser.require()
                val user = userDao.findById(userId) ?: throw NotSignedInException()
                if (!hasher.verify(password, user.passwordSalt, user.passwordHash)) {
                    throw UserFacingException("Password is incorrect")
                }
                // Files first: once the rows are gone there is no record of which
                // images belonged to this account.
                bodyDao.observePhotos(userId).first().forEach { photoStore.delete(it.filePath) }
                user.avatarPath?.let { photoStore.delete(it) }
                File(context.filesDir, "exports").listFiles()?.forEach { it.delete() }

                exerciseDao.deleteOwnedBy(userId)
                // Every other table cascades from users.
                userDao.deleteById(userId)
                reminderScheduler.cancel()
                dataStore.edit { it.clear() }
                authRepository.signOut()
            }
        } finally {
            password.wipe()
        }
    }

    private suspend fun updateUser(transform: (UserEntity) -> UserEntity): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val user = userDao.findById(currentUser.require()) ?: throw NotSignedInException()
                userDao.update(transform(user))
            }
        }

    private companion object {
        val KEY_REMINDER_ON = booleanPreferencesKey("reminder_enabled")
        val KEY_REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val KEY_REMINDER_MINUTE = intPreferencesKey("reminder_minute")
    }
}
