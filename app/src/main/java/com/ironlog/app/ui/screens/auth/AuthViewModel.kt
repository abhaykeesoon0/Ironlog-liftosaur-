package com.ironlog.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.core.security.Validators
import com.ironlog.app.data.repository.AuthField
import com.ironlog.app.data.repository.AuthRepository
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
    val isComplete: Boolean = false
) {
    val canSubmitSignIn: Boolean
        get() = email.isNotBlank() && password.isNotBlank() && !isSubmitting

    val canSubmitRegister: Boolean
        get() = name.isNotBlank() && email.isNotBlank() &&
            password.isNotBlank() && confirmPassword.isNotBlank() && !isSubmitting
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
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
