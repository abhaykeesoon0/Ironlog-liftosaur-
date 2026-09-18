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
import com.ironlog.app.data.repository.ReminderSettings
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

data class SettingsUiState(
    val exporting: ExportKind? = null,
    /** One-shot: the share sheet to open. Cleared by [SettingsViewModel.onShareLaunched]. */
    val shareIntent: Intent? = null,
    val isDeleting: Boolean = false,
    val deleteError: String? = null,
    val error: String? = null,
    val message: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val exportRepository: ExportRepository,
    private val biometricGate: BiometricGate
) : ViewModel() {

    val user: StateFlow<UserEntity?> = profileRepository.observeUser()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val reminder: StateFlow<ReminderSettings> = profileRepository.reminderSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderSettings())

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
        const val MIN_REST = 15
        const val MAX_REST = 600
        const val REST_STEP = 15
    }
}
