package com.ironlog.app.ui.screens.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ironlog.app.core.security.Validators
import com.ironlog.app.core.security.wipe
import com.ironlog.app.data.repository.AuthField
import com.ironlog.app.data.repository.AuthRepository
import com.ironlog.app.data.repository.AuthResult
import com.ironlog.app.data.repository.CurrentUser
import com.ironlog.app.data.repository.userMessage
import com.ironlog.app.ui.components.EmptyState
import com.ironlog.app.ui.components.ErrorBanner
import com.ironlog.app.ui.components.IronTextField
import com.ironlog.app.ui.components.IronTopBar
import com.ironlog.app.ui.components.PrimaryButton
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.Space
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChangePasswordState(
    val current: String = "",
    val new: String = "",
    val confirm: String = "",
    val currentError: String? = null,
    val newError: String? = null,
    val confirmError: String? = null,
    val generalError: String? = null,
    val isSubmitting: Boolean = false,
    val isDone: Boolean = false
) {
    val canSubmit: Boolean
        get() = current.isNotEmpty() && new.isNotEmpty() && confirm.isNotEmpty() && !isSubmitting
}

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val currentUser: CurrentUser
) : ViewModel() {

    private val _state = MutableStateFlow(ChangePasswordState())
    val state: StateFlow<ChangePasswordState> = _state.asStateFlow()

    fun onCurrentChange(v: String) = _state.update { it.copy(current = v, currentError = null, generalError = null) }
    fun onNewChange(v: String) = _state.update { it.copy(new = v, newError = null, generalError = null) }
    fun onConfirmChange(v: String) = _state.update { it.copy(confirm = v, confirmError = null, generalError = null) }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit) return

        val newChars = s.new.toCharArray()
        val confirmChars = s.confirm.toCharArray()
        val newError = Validators.validatePassword(newChars)
        val confirmError = Validators.validatePasswordMatch(newChars, confirmChars)
        newChars.wipe()
        confirmChars.wipe()
        if (newError != null || confirmError != null) {
            _state.update { it.copy(newError = newError, confirmError = confirmError) }
            return
        }
        if (s.current == s.new) {
            _state.update { it.copy(newError = "Choose a password different from the current one") }
            return
        }

        val userId = runCatching { currentUser.require() }.getOrElse { e ->
            _state.update { it.copy(generalError = e.userMessage("You've been signed out")) }
            return
        }

        _state.update { it.copy(isSubmitting = true, generalError = null) }
        viewModelScope.launch {
            val result = authRepository.changePassword(userId, s.current.toCharArray(), s.new.toCharArray())
            // Password text leaves ViewModel state whatever the outcome.
            _state.update { it.copy(current = "", new = "", confirm = "", isSubmitting = false) }
            when (result) {
                is AuthResult.Success -> _state.update { it.copy(isDone = true) }
                is AuthResult.Failure -> _state.update {
                    when {
                        result.field == AuthField.PASSWORD && result.message.startsWith("Current") ->
                            it.copy(currentError = result.message)
                        result.field == AuthField.PASSWORD -> it.copy(newError = result.message)
                        else -> it.copy(generalError = result.message)
                    }
                }
            }
        }
    }
}

@Composable
fun ChangePasswordScreen(
    onBack: () -> Unit,
    viewModel: ChangePasswordViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        IronTopBar(title = "Change password", onBack = onBack)

        if (state.isDone) {
            EmptyState(
                icon = Icons.Default.CheckCircle,
                title = "Password changed",
                body = "Your session has been renewed. Use the new password next time you sign in.",
                actionLabel = "Done",
                onAction = onBack
            )
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Space.lg)
        ) {
            Text(
                "At least ${Validators.MIN_PASSWORD_LENGTH} characters, with a letter and a number.",
                style = MaterialTheme.typography.bodyMedium,
                color = ChalkDim
            )
            Spacer(Modifier.height(Space.lg))
            ErrorBanner(state.generalError)
            if (state.generalError != null) Spacer(Modifier.height(Space.md))

            IronTextField(
                value = state.current,
                onValueChange = viewModel::onCurrentChange,
                label = "Current password",
                isPassword = true,
                error = state.currentError,
                enabled = !state.isSubmitting
            )
            Spacer(Modifier.height(Space.md))
            IronTextField(
                value = state.new,
                onValueChange = viewModel::onNewChange,
                label = "New password",
                isPassword = true,
                error = state.newError,
                enabled = !state.isSubmitting
            )
            Spacer(Modifier.height(Space.md))
            IronTextField(
                value = state.confirm,
                onValueChange = viewModel::onConfirmChange,
                label = "Confirm new password",
                isPassword = true,
                error = state.confirmError,
                enabled = !state.isSubmitting,
                imeAction = ImeAction.Done
            )
            Spacer(Modifier.height(Space.xl))
            PrimaryButton(
                text = "Change password",
                onClick = viewModel::submit,
                enabled = state.canSubmit,
                loading = state.isSubmitting
            )
            Spacer(Modifier.height(Space.section))
        }
    }
}
