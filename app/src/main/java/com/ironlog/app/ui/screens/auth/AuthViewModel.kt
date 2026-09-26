package com.ironlog.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.core.security.Validators
import com.ironlog.app.data.repository.AuthField
import com.ironlog.app.data.repository.AuthRepository
import android.net.Uri
import com.ironlog.app.core.security.wipe
import com.ironlog.app.data.repository.BackupRepository
import com.ironlog.app.data.repository.userMessage
import com.ironlog.app.data.repository.AuthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val nameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val generalError: String? = null,
    val isSubmitting: Boolean = false,
    val isComplete: Boolean = false,
    /** A backup chosen on the sign-in screen, waiting on a passphrase or a confirmation. */
    val restoreUri: Uri? = null,
    val restoreNeedsPassphrase: Boolean = false,
    val isRestoring: Boolean = false,
    /** Shown once an account has been brought across from another phone. */
    val restoreMessage: String? = null
) {
    val canSubmitSignIn: Boolean
        get() = email.isNotBlank() && password.isNotBlank() && !isSubmitting

    val canSubmitRegister: Boolean
        get() = name.isNotBlank() && email.isNotBlank() &&
            password.isNotBlank() && confirmPassword.isNotBlank() && !isSubmitting
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val backupRepository: BackupRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onNameChange(value: String) =
        _state.update { it.copy(name = value, nameError = null, generalError = null) }

    fun onEmailChange(value: String) =
        _state.update { it.copy(email = value, emailError = null, generalError = null) }

    fun onPasswordChange(value: String) =
        _state.update { it.copy(password = value, passwordError = null, generalError = null) }

    fun onConfirmPasswordChange(value: String) =
        _state.update { it.copy(confirmPassword = value, confirmPasswordError = null, generalError = null) }

    /**
     * Validated on blur rather than on every keystroke: flagging "that doesn't
     * look like a valid email address" while someone is still typing the domain
     * is noise, not help.
     */
    fun onEmailBlur() {
        val email = _state.value.email
        if (email.isNotBlank()) {
            _state.update { it.copy(emailError = Validators.validateEmail(Validators.sanitiseEmail(email))) }
        }
    }

    fun onPasswordBlur() {
        val password = _state.value.password
        if (password.isNotBlank()) {
            _state.update { it.copy(passwordError = Validators.validatePassword(password.toCharArray())) }
        }
    }

    /**
     * Sets a new password after the device lock has been satisfied.
     *
     * The screen runs the biometric prompt; this only performs the reset, so
     * the check and the write stay in the layers that own them.
     */
    fun resetPasswordAfterDeviceAuth(newPassword: String) {
        if (_state.value.isSubmitting) return
        _state.update { it.copy(isSubmitting = true, generalError = null) }
        viewModelScope.launch {
            val result = authRepository.resetPasswordAfterDeviceAuth(
                _state.value.email,
                newPassword.toCharArray()
            )
            handleResult(result)
        }
    }

    fun showError(message: String) = _state.update { it.copy(generalError = message) }

    // ------------------------------------------------------------ restore from another phone

    /** A backup file was picked: find out whether it is sealed before asking for a passphrase. */
    fun onRestoreFileChosen(uri: Uri) {
        viewModelScope.launch {
            backupRepository.isEncrypted(uri)
                .onSuccess { sealed -> _state.update { it.copy(restoreUri = uri, restoreNeedsPassphrase = sealed, generalError = null) } }
                .onFailure { e -> _state.update { it.copy(generalError = e.userMessage("Could not open that file")) } }
        }
    }

    fun cancelRestore() = _state.update { it.copy(restoreUri = null, restoreNeedsPassphrase = false) }

    /**
     * Recreates the account in the backup and its data. The email is filled
     * in afterwards; the password is the one the user has always used, which
     * this phone learns only when they type it.
     */
    fun restoreAccount(passphrase: CharArray?) {
        val uri = _state.value.restoreUri ?: run { passphrase?.wipe(); return }
        _state.update { it.copy(isRestoring = true, generalError = null) }
        viewModelScope.launch {
            backupRepository.restoreAccount(uri, passphrase)
                .onSuccess { restored ->
                    val s = restored.summary
                    val parts = listOf(
                        s.workouts to "workouts", s.routines to "routines", s.cardio to "cardio sessions",
                        s.measurements to "measurements", s.foodLogs to "food entries", s.goals to "goals"
                    ).filter { (n, _) -> n > 0 }.map { (n, label) -> "$n $label" }
                    val brought = if (parts.isEmpty()) "your settings" else parts.joinToString(", ")
                    _state.update {
                        it.copy(
                            isRestoring = false, restoreUri = null, restoreNeedsPassphrase = false,
                            email = restored.email, emailError = null,
                            restoreMessage = "Welcome back, ${restored.displayName}. Your account is on this phone with " +
                                "$brought. Sign in with your usual password."
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(isRestoring = false, restoreUri = null, restoreNeedsPassphrase = false, generalError = e.userMessage("Restore failed"))
                    }
                }
        }
    }

    fun signIn() {
        if (!_state.value.canSubmitSignIn) return
        _state.update { it.copy(isSubmitting = true, generalError = null) }

        viewModelScope.launch {
            val snapshot = _state.value
            val result = authRepository.signIn(snapshot.email, snapshot.password.toCharArray())
            handleResult(result)
        }
    }

    fun register() {
        if (!_state.value.canSubmitRegister) return
        _state.update { it.copy(isSubmitting = true, generalError = null) }

        viewModelScope.launch {
            val snapshot = _state.value
            val result = authRepository.register(
                name = snapshot.name,
                email = snapshot.email,
                password = snapshot.password.toCharArray(),
                confirmPassword = snapshot.confirmPassword.toCharArray()
            )
            handleResult(result)
        }
    }

    /**
     * Failures are routed back to the field that caused them where the repository
     * identified one, so the error appears under the input the user has to fix
     * rather than in a generic banner at the top of the form.
     */
    private fun handleResult(result: AuthResult) {
        when (result) {
            is AuthResult.Success -> _state.update {
                // Password fields are cleared on success so they do not sit in
                // ViewModel state for the life of the process.
                it.copy(
                    isSubmitting = false,
                    isComplete = true,
                    password = "",
                    confirmPassword = ""
                )
            }

            is AuthResult.Failure -> _state.update {
                when (result.field) {
                    AuthField.NAME -> it.copy(isSubmitting = false, nameError = result.message)
                    AuthField.EMAIL -> it.copy(isSubmitting = false, emailError = result.message)
                    AuthField.PASSWORD -> it.copy(isSubmitting = false, passwordError = result.message)
                    AuthField.CONFIRM_PASSWORD ->
                        it.copy(isSubmitting = false, confirmPasswordError = result.message)
                    null -> it.copy(isSubmitting = false, generalError = result.message)
                }
            }
        }
    }

    /** Clears form state when moving between sign-in and registration. */
    fun reset() {
        _state.value = AuthUiState()
    }
}
