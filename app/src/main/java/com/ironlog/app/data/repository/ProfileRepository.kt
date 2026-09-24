package com.ironlog.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ironlog.app.core.media.PhotoStore
import com.ironlog.app.core.notifications.ReminderScheduler
import com.ironlog.app.core.security.PasswordHasher
import com.ironlog.app.core.security.Validators
import com.ironlog.app.core.security.wipe
import com.ironlog.app.data.dao.BodyDao
import com.ironlog.app.data.dao.ExerciseDao
import com.ironlog.app.data.dao.UserDao
import com.ironlog.app.domain.usecase.TrainingMath
import com.ironlog.app.data.entity.PlateSet
import com.ironlog.app.data.entity.StepSource
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
import com.ironlog.app.data.entity.MuscleTint

/**
 * Marks an avatar value as one of the app's built-in figures rather than a
 * photo on disk. Kept here so the repository can tell the two apart without
 * depending on the UI layer that draws them.
 */
const val FIGURE_AVATAR_PREFIX = "figure:"

data class ReminderSettings(
    val enabled: Boolean = false,
    val hour: Int = 18,
    val minute: Int = 0
)

/**
 * The gym's equipment, as opposed to the user's reading preference.
 *
 * Kept on the device rather than the account because it describes where someone
 * trains, and because the plate calculator has to work before any account has
 * loaded.
 */
data class EquipmentSettings(
    val plateSet: PlateSet = PlateSet.KILOGRAMS,
    val barWeightKg: Double = 20.0
) {
    companion object {
        /** A training bar, from a 5 kg technique bar to a 50 kg specialty bar. */
        const val MIN_BAR_KG = 5.0
        const val MAX_BAR_KG = 50.0
    }
}

/** The waits a user can pick before the app lock asks again. Zero is straight away. */
val LOCK_GRACE_OPTIONS = listOf(0, 15, 60, 300, 900)
const val DEFAULT_LOCK_GRACE = 15

interface ProfileRepository {
    fun observeUser(): Flow<UserEntity?>
    val reminderSettings: Flow<ReminderSettings>

    /**
     * Whether the launch sting plays. Kept in DataStore rather than on the user
     * row because it is a property of this phone, not of the account: the sound
     * must be readable before anyone has signed in.
     */
    val welcomeSoundEnabled: Flow<Boolean>

    /** Whether the coach may look a topic up on the open web. Off by default: the app is offline by design. */
    val webLookupsEnabled: Flow<Boolean>
    suspend fun setWebLookups(enabled: Boolean): Result<Unit>

    /** Which plates and bar the gym has, used by the plate calculator. */
    val equipment: Flow<EquipmentSettings>

    /** Where today's steps are read from: this phone, or Health Connect. */
    val stepSource: Flow<StepSource>

    suspend fun updateProfile(name: String, bodyweightKg: Double?, heightCm: Double?): Result<Unit>
    suspend fun setUnitSystem(unit: UnitSystem): Result<Unit>
    suspend fun setBiometricLock(enabled: Boolean): Result<Unit>
    suspend fun setRestTimer(defaultSeconds: Int, autoStart: Boolean): Result<Unit>
    suspend fun setWeeklyGoal(workouts: Int): Result<Unit>
    suspend fun setAvatar(path: String?): Result<Unit>
    suspend fun setReminder(settings: ReminderSettings): Result<Unit>
    suspend fun setWelcomeSound(enabled: Boolean): Result<Unit>
    suspend fun setPlateSet(plateSet: PlateSet): Result<Unit>
    suspend fun setStepSource(source: StepSource): Result<Unit>
    val muscleTint: Flow<MuscleTint>
    /** How long the app may be in the background before the lock asks again. */
    val lockGraceSeconds: Flow<Int>
    /** Whether the introduction has been seen on this device. Device-level: there is no user yet. */
    val onboardingDone: Flow<Boolean>
    suspend fun setOnboardingDone(done: Boolean): Result<Unit>
    suspend fun setLockGrace(seconds: Int): Result<Unit>
    suspend fun setMuscleTint(tint: MuscleTint): Result<Unit>
    suspend fun setBarWeight(barWeightKg: Double): Result<Unit>

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

    override val welcomeSoundEnabled: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_WELCOME_SOUND] ?: true }

    override val webLookupsEnabled: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_WEB_LOOKUPS] ?: false }

    override suspend fun setWebLookups(enabled: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { dataStore.edit { prefs -> prefs[KEY_WEB_LOOKUPS] = enabled }; Unit }
    }

    override val equipment: Flow<EquipmentSettings> = dataStore.data.map { prefs ->
        val set = prefs[KEY_PLATE_SET]
            ?.let { runCatching { PlateSet.valueOf(it) }.getOrNull() }
            ?: PlateSet.KILOGRAMS
        EquipmentSettings(
            plateSet = set,
            // Falls back to the bar that comes with the chosen set, so switching
            // racks does not leave a 20 kg bar sitting under pound plates.
            barWeightKg = prefs[KEY_BAR_WEIGHT] ?: TrainingMath.defaultBarKg(set)
        )
    }

    override val onboardingDone: Flow<Boolean> = dataStore.data.map { prefs -> prefs[KEY_ONBOARDED] ?: false }

    override suspend fun setOnboardingDone(done: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { dataStore.edit { prefs -> prefs[KEY_ONBOARDED] = done }; Unit }
    }

    override val lockGraceSeconds: Flow<Int> = dataStore.data.map { prefs -> prefs[KEY_LOCK_GRACE] ?: DEFAULT_LOCK_GRACE }

    override suspend fun setLockGrace(seconds: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (seconds !in LOCK_GRACE_OPTIONS) throw UserFacingException("Pick one of the listed times")
            dataStore.edit { prefs -> prefs[KEY_LOCK_GRACE] = seconds }
            Unit
        }
    }

    override val muscleTint: Flow<MuscleTint> = dataStore.data.map { prefs ->
        prefs[KEY_MUSCLE_TINT]
            ?.let { runCatching { MuscleTint.valueOf(it) }.getOrNull() }
            ?: MuscleTint.PLUM
    }

    override suspend fun setMuscleTint(tint: MuscleTint): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { dataStore.edit { prefs -> prefs[KEY_MUSCLE_TINT] = tint.name }; Unit }
    }

    override val stepSource: Flow<StepSource> = dataStore.data.map { prefs ->
        prefs[KEY_STEP_SOURCE]
            ?.let { runCatching { StepSource.valueOf(it) }.getOrNull() }
            ?: StepSource.PHONE
    }

    override suspend fun updateProfile(name: String, bodyweightKg: Double?, heightCm: Double?): Result<Unit> =
        updateUser { user ->
            val clean = Validators.sanitiseSingleLine(name)
            Validators.validateDisplayName(clean)?.let { throw UserFacingException(it) }
            bodyweightKg?.let { if (it !in 20.0..400.0) throw UserFacingException("Check the bodyweight") }
            heightCm?.let { if (it !in 80.0..260.0) throw UserFacingException("Check the height") }
            user.copy(displayName = clean, bodyweightKg = bodyweightKg, heightCm = heightCm)
        }

    /**
     * Changing the reading unit takes the equipment with it.
     *
     * Someone switching to pounds is telling the app what their gym is in, and
     * leaving kilo plates behind would contradict the choice they just made.
     * The plate set stays separately adjustable afterwards, for the minority who
     * read in one unit and load the other.
     */
    override suspend fun setUnitSystem(unit: UnitSystem): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            updateUser { it.copy(unitSystem = unit) }.getOrThrow()

            // Only after the user row is saved: if that failed, the equipment
            // must not be left describing a unit the app never switched to.
            val plateSet = when (unit) {
                UnitSystem.METRIC -> PlateSet.KILOGRAMS
                UnitSystem.IMPERIAL -> PlateSet.POUNDS
            }
            dataStore.edit { prefs ->
                prefs[KEY_PLATE_SET] = plateSet.name
                prefs[KEY_BAR_WEIGHT] = TrainingMath.defaultBarKg(plateSet)
            }
            Unit
        }
    }

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
        // Only a real photo has a file behind it; a built-in figure is a token,
        // and handing that to the file store would be a delete on a path that
        // never existed.
        user.avatarPath
            ?.takeIf { it != path && !it.startsWith(FIGURE_AVATAR_PREFIX) }
            ?.let { photoStore.delete(it) }
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

    override suspend fun setWelcomeSound(enabled: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            dataStore.edit { prefs -> prefs[KEY_WELCOME_SOUND] = enabled }
            Unit
        }
    }

    override suspend fun setPlateSet(plateSet: PlateSet): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            dataStore.edit { prefs ->
                prefs[KEY_PLATE_SET] = plateSet.name
                // The bar moves with the rack: a pound gym's bar is 45 lb, and
                // leaving the old number would quietly misreport every total.
                prefs[KEY_BAR_WEIGHT] = TrainingMath.defaultBarKg(plateSet)
            }
            Unit
        }
    }

    override suspend fun setStepSource(source: StepSource): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            dataStore.edit { prefs -> prefs[KEY_STEP_SOURCE] = source.name }
            Unit
        }
    }

    override suspend fun setBarWeight(barWeightKg: Double): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (barWeightKg !in EquipmentSettings.MIN_BAR_KG..EquipmentSettings.MAX_BAR_KG) {
                throw UserFacingException("A bar weighs between 5 and 50 kg")
            }
            dataStore.edit { prefs -> prefs[KEY_BAR_WEIGHT] = barWeightKg }
            Unit
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
        val KEY_WELCOME_SOUND = booleanPreferencesKey("welcome_sound_enabled")
        val KEY_WEB_LOOKUPS = booleanPreferencesKey("coach_web_lookups")
        val KEY_PLATE_SET = stringPreferencesKey("plate_set")
        val KEY_STEP_SOURCE = stringPreferencesKey("step_source")
        val KEY_MUSCLE_TINT = stringPreferencesKey("muscle_tint")
        val KEY_LOCK_GRACE = intPreferencesKey("lock_grace_seconds")
        val KEY_ONBOARDED = booleanPreferencesKey("onboarding_done")
        val KEY_BAR_WEIGHT = doublePreferencesKey("bar_weight_kg")

    }
}
