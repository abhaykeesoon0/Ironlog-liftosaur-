package com.ironlog.app.ui.screens.profile

import android.content.Intent
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.core.security.BiometricAvailability
import com.ironlog.app.core.security.BiometricGate
import com.ironlog.app.data.entity.UnitSystem
import com.ironlog.app.data.entity.UserEntity
import com.ironlog.app.data.repository.ExportKind
import com.ironlog.app.data.repository.ExportRepository
import com.ironlog.app.data.repository.ProfileRepository
import com.ironlog.app.core.ai.CloudAssistant
import com.ironlog.app.data.entity.PlateSet
import com.ironlog.app.data.entity.StepSource
import com.ironlog.app.data.repository.EquipmentSettings
import com.ironlog.app.data.repository.ReminderSettings
import com.ironlog.app.domain.usecase.TrainingMath
import com.ironlog.app.data.repository.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.ironlog.app.data.entity.MuscleTint
import android.net.Uri
import com.ironlog.app.data.repository.BackupRepository
import com.ironlog.app.data.repository.DEFAULT_LOCK_GRACE
import com.ironlog.app.core.security.wipe
import com.ironlog.app.core.widget.WidgetSummary
import com.ironlog.app.data.entity.TrainingFocus

data class SettingsUiState(
    val exporting: ExportKind? = null,
    /** One-shot: the share sheet to open. Cleared by [SettingsViewModel.onShareLaunched]. */
    val shareIntent: Intent? = null,
    val isDeleting: Boolean = false,
    val isBackingUp: Boolean = false,
    val isRestoring: Boolean = false,
    /** A backup file picked for restore, waiting on a passphrase or confirmation. */
    val restoreUri: Uri? = null,
    val restoreNeedsPassphrase: Boolean = false,
    val deleteError: String? = null,
    val error: String? = null,
    val message: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val cloudAssistant: CloudAssistant,
    private val exportRepository: ExportRepository,
    private val backupRepository: BackupRepository,
    private val biometricGate: BiometricGate,
    private val widgetSummary: WidgetSummary
) : ViewModel() {

    val user: StateFlow<UserEntity?> = profileRepository.observeUser()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val reminder: StateFlow<ReminderSettings> = profileRepository.reminderSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderSettings())

    val equipment: StateFlow<EquipmentSettings> = profileRepository.equipment
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EquipmentSettings())

    val stepSource: StateFlow<StepSource> = profileRepository.stepSource
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StepSource.PHONE)

    val welcomeSound: StateFlow<Boolean> = profileRepository.welcomeSoundEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val muscleTint: StateFlow<MuscleTint> = profileRepository.muscleTint
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MuscleTint.PLUM)

    val trainingFocus: StateFlow<TrainingFocus> = profileRepository.trainingFocus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrainingFocus.GENERAL)

    val lockGrace: StateFlow<Int> = profileRepository.lockGraceSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_LOCK_GRACE)

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    val biometricAvailability: BiometricAvailability get() = biometricGate.availability()

    fun setUnit(unit: UnitSystem) = persist("Could not change units") { profileRepository.setUnitSystem(unit) }

    fun changeRest(deltaSeconds: Int) {
        val u = user.value ?: return
        val next = (u.restTimerDefaultSeconds + deltaSeconds).coerceIn(MIN_REST, MAX_REST)
        persist("Could not update the rest timer") { profileRepository.setRestTimer(next, u.autoRestTimerEnabled) }
    }

    fun setAutoRest(enabled: Boolean) {
        val u = user.value ?: return
        persist("Could not update the rest timer") { profileRepository.setRestTimer(u.restTimerDefaultSeconds, enabled) }
    }

    fun changeGoal(delta: Int) {
        val u = user.value ?: return
        val next = (u.weeklyGoalWorkouts + delta).coerceIn(1, 14)
        persist("Could not update the goal") { profileRepository.setWeeklyGoal(next) }
    }

    /** True when a Claude API key has been saved for the optional assistant. */
    fun hasAssistantKey(): Boolean = cloudAssistant.hasKey()

    fun saveAssistantKey(key: String) {
        cloudAssistant.saveKey(key)
        _state.update { it.copy(error = null) }
    }

    fun clearAssistantKey() = cloudAssistant.saveKey("")

    fun setStepSource(source: StepSource) =
        persist("Could not change the step source") { profileRepository.setStepSource(source) }

    /** Asks the launcher to place the widget; some launchers cannot, and say so. */
    fun addWidget(context: android.content.Context) {
        if (!widgetSummary.requestPin(context)) {
            _state.update { it.copy(message = "This launcher cannot add widgets from an app. Long-press the home screen and choose Widgets.") }
        }
    }

    fun setLockGrace(seconds: Int) =
        persist("Could not change the lock timing") { profileRepository.setLockGrace(seconds) }

    fun setTrainingFocus(focus: TrainingFocus) =
        persist("Could not change the focus") { profileRepository.setTrainingFocus(focus) }

    fun setMuscleTint(tint: MuscleTint) =
        persist("Could not change the colour") { profileRepository.setMuscleTint(tint) }

    fun setPlateSet(plateSet: PlateSet) =
        persist("Could not change the plate set") { profileRepository.setPlateSet(plateSet) }

    /** Steps the bar by the smallest plate on the chosen rack. */
    fun changeBar(direction: Int) {
        val current = equipment.value
        val step = if (current.plateSet == PlateSet.KILOGRAMS) BAR_STEP_KG else TrainingMath.lbToKg(BAR_STEP_LB)
        persist("Could not change the bar weight") {
            profileRepository.setBarWeight(current.barWeightKg + direction * step)
        }
    }

    fun setWelcomeSound(enabled: Boolean) =
        persist("Could not change the welcome sound") { profileRepository.setWelcomeSound(enabled) }

    fun setReminderEnabled(enabled: Boolean) =
        persist("Could not update the reminder") { profileRepository.setReminder(reminder.value.copy(enabled = enabled)) }

    fun changeReminderHour(delta: Int) {
        val r = reminder.value
        persist("Could not update the reminder") {
            profileRepository.setReminder(r.copy(hour = Math.floorMod(r.hour + delta, 24)))
        }
    }

    fun changeReminderMinute(delta: Int) {
        val r = reminder.value
        persist("Could not update the reminder") {
            profileRepository.setReminder(r.copy(minute = Math.floorMod(r.minute + delta, 60)))
        }
    }

    fun onNotificationsDeclined() = _state.update {
        it.copy(error = "Reminders need notification access. You can allow it in the Permissions section below.")
    }

    /**
     * Turning the lock on is gated by a successful prompt: enabling a lock the
     * user cannot pass would shut them out of their own log on the next launch.
     * The activity is only used for this call and is not retained.
     */
    fun enableBiometricLock(activity: FragmentActivity) {
        biometricGate.prompt(
            activity = activity,
            title = "Turn on app lock",
            subtitle = "Confirm it's you",
            onSuccess = { persist("Could not turn on the app lock") { profileRepository.setBiometricLock(true) } },
            onFailure = { message -> _state.update { it.copy(error = message) } },
            onCancelled = { }
        )
    }

    fun disableBiometricLock() = persist("Could not turn off the app lock") { profileRepository.setBiometricLock(false) }

    fun export(kind: ExportKind) {
        if (_state.value.exporting != null) return
        _state.update { it.copy(exporting = kind, error = null) }
        viewModelScope.launch {
            exportRepository.export(kind)
                .onSuccess { file ->
                    _state.update { it.copy(exporting = null, shareIntent = exportRepository.shareIntent(file, kind)) }
                }
                .onFailure { e ->
                    _state.update { it.copy(exporting = null, error = e.userMessage("Export failed. Try again.")) }
                }
        }
    }

    /** Seals the full backup under [passphrase] and hands it to the share sheet. */
    fun backupEncrypted(passphrase: CharArray) {
        if (_state.value.isBackingUp) { passphrase.wipe(); return }
        _state.update { it.copy(isBackingUp = true, error = null) }
        viewModelScope.launch {
            backupRepository.exportEncrypted(passphrase)
                .onSuccess { file ->
                    _state.update {
                        it.copy(isBackingUp = false, shareIntent = exportRepository.shareIntent(file, ExportKind.FULL_JSON))
                    }
                }
                .onFailure { e -> _state.update { it.copy(isBackingUp = false, error = e.userMessage("Backup failed. Try again.")) } }
        }
    }

    /** A file was chosen: find out whether it needs a passphrase before asking for one. */
    fun onRestoreFileChosen(uri: Uri) {
        viewModelScope.launch {
            backupRepository.isEncrypted(uri)
                .onSuccess { sealed -> _state.update { it.copy(restoreUri = uri, restoreNeedsPassphrase = sealed) } }
                .onFailure { e -> _state.update { it.copy(error = e.userMessage("Could not open that file")) } }
        }
    }

    fun cancelRestore() = _state.update { it.copy(restoreUri = null, restoreNeedsPassphrase = false) }

    /** Strong or Hevy CSV: read, matched by exercise name, added like a restore. */
    fun importCsv(uri: Uri) {
        if (_state.value.isRestoring) return
        _state.update { it.copy(isRestoring = true, error = null) }
        viewModelScope.launch {
            backupRepository.importCsv(uri)
                .onSuccess { summary ->
                    _state.update {
                        it.copy(
                            isRestoring = false,
                            message = if (summary.workouts == 0) "Nothing new in that file"
                            else "Imported ${summary.workouts} workout(s)" +
                                (if (summary.exercisesCreated > 0) ", ${summary.exercisesCreated} new exercise(s)" else "") +
                                (if (summary.skipped > 0) "; ${summary.skipped} already here" else "")
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(isRestoring = false, error = e.userMessage("Could not import that file")) } }
        }
    }

    fun restore(passphrase: CharArray?) {
        val uri = _state.value.restoreUri ?: run { passphrase?.wipe(); return }
        _state.update { it.copy(isRestoring = true, error = null) }
        viewModelScope.launch {
            backupRepository.restore(uri, passphrase)
                .onSuccess { summary ->
                    _state.update {
                        it.copy(
                            isRestoring = false, restoreUri = null, restoreNeedsPassphrase = false,
                            message = if (summary.isEmpty) "Nothing new in that backup" +
                                (if (summary.skipped > 0) ": ${summary.skipped} entries were already here." else ".")
                            else "Restored ${summary.workouts} workouts, ${summary.routines} routines, " +
                                "${summary.measurements} measurements and ${summary.cardio} cardio sessions" +
                                (if (summary.skipped > 0) "; ${summary.skipped} already here." else ".")
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(isRestoring = false, error = e.userMessage("Restore failed")) } }
        }
    }

    fun onShareLaunched() = _state.update { it.copy(shareIntent = null) }

    fun onShareFailed() = _state.update {
        it.copy(shareIntent = null, error = "No app on this phone can receive the file.")
    }

    fun deleteAccount(password: CharArray) {
        _state.update { it.copy(isDeleting = true, deleteError = null) }
        viewModelScope.launch {
            // The repository wipes the array whatever the outcome.
            profileRepository.deleteAccount(password)
                .onSuccess { _state.update { it.copy(isDeleting = false) } }
                .onFailure { e ->
                    _state.update { it.copy(isDeleting = false, deleteError = e.userMessage("Could not delete the account")) }
                }
        }
    }

    fun clearDeleteError() = _state.update { it.copy(deleteError = null) }

    fun dismissError() = _state.update { it.copy(error = null) }

    private fun persist(fallback: String, block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            block().onFailure { e -> _state.update { it.copy(error = e.userMessage(fallback)) } }
        }
    }

    companion object {
        /** The bar steps by the smallest plate a rack of that kind has. */
        const val BAR_STEP_KG = 2.5
        const val BAR_STEP_LB = 5.0

        const val MIN_REST = 15
        const val MAX_REST = 600
        const val REST_STEP = 15
    }
}
