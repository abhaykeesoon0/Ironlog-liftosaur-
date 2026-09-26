package com.ironlog.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ironlog.app.data.repository.AuthRepository
import com.ironlog.app.data.repository.userMessage
import com.ironlog.app.ui.components.Caption
import com.ironlog.app.ui.components.ErrorBanner
import com.ironlog.app.ui.components.PrimaryButton
import com.ironlog.app.ui.components.SecondaryButton
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.Space
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VerifyEmailUiState(
    val email: String = "",
    val isChecking: Boolean = false,
    val isResending: Boolean = false,
    val notice: String? = null,
    val error: String? = null
)

@HiltViewModel
class VerifyEmailViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(VerifyEmailUiState())
    val state: StateFlow<VerifyEmailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(email = authRepository.currentUser()?.email.orEmpty()) }
        }
    }

    /** Asks the service whether the link has been opened. The gate lifts on its own when it has. */
    fun confirm() {
        if (_state.value.isChecking) return
        _state.update { it.copy(isChecking = true, error = null, notice = null) }
        viewModelScope.launch {
            authRepository.confirmEmailVerified()
                .onSuccess { verified ->
                    _state.update {
                        it.copy(isChecking = false, notice = if (verified) null else "Not verified yet. Open the link in the email, then try again.")
                    }
                }
                .onFailure { e -> _state.update { it.copy(isChecking = false, error = e.userMessage("Could not check with the verification service")) } }
        }
    }

    fun resend() {
        if (_state.value.isResending) return
        _state.update { it.copy(isResending = true, error = null, notice = null) }
        viewModelScope.launch {
            authRepository.resendVerificationEmail()
                .onSuccess { _state.update { it.copy(isResending = false, notice = "Sent. Check your inbox and the spam folder.") } }
                .onFailure { e -> _state.update { it.copy(isResending = false, error = e.userMessage("Could not resend the email")) } }
        }
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }
}

/**
 * Drawn over the app, like the biometric lock, until the address has been
 * confirmed. The account exists and its data is safe underneath; what is
 * withheld is use of it until the person proves the inbox is theirs.
 */
@Composable
fun VerifyEmailScreen(viewModel: VerifyEmailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(Space.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.MarkEmailRead, contentDescription = null, tint = ChalkDim, modifier = Modifier.height(40.dp))
        Spacer(Modifier.height(Space.lg))
        Text("Check your email", style = MaterialTheme.typography.headlineMedium, color = Chalk)
        Spacer(Modifier.height(Space.sm))
        Text(
            "We sent a verification link to ${state.email.ifBlank { "your address" }}. Open it on any device, " +
                "then come back here.",
            style = MaterialTheme.typography.bodyMedium,
            color = ChalkDim,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Space.xl))
        ErrorBanner(state.error)
        state.notice?.let { Caption(it) }
        Spacer(Modifier.height(Space.lg))
        PrimaryButton(text = "I've opened the link", onClick = viewModel::confirm, loading = state.isChecking)
        Spacer(Modifier.height(Space.md))
        SecondaryButton(text = if (state.isResending) "Sending…" else "Send the email again", onClick = viewModel::resend)
        Spacer(Modifier.height(Space.md))
        TextButton(onClick = viewModel::signOut) {
            Text("Sign out", style = MaterialTheme.typography.labelMedium, color = ChalkDim)
        }
    }
}
