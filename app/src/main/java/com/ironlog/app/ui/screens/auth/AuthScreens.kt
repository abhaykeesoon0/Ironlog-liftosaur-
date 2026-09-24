package com.ironlog.app.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.ui.components.ErrorBanner
import com.ironlog.app.ui.components.IronTextField
import com.ironlog.app.ui.components.PrimaryButton
import androidx.fragment.app.FragmentActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ironlog.app.core.security.BiometricGate
import com.ironlog.app.ui.theme.Slate700
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.platform.LocalContext
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.Space

@Composable
fun SignInScreen(
    onSignedIn: () -> Unit,
    onNavigateToRegister: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findFragmentActivity()
    val biometricGate = remember(activity) { activity?.let { BiometricGate(it) } }
    var resetting by remember { mutableStateOf(false) }

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) onSignedIn()
    }

    AuthScaffold(
        title = "Welcome back",
        subtitle = "Pick up where you left off."
    ) {
        ErrorBanner(state.generalError)
        if (state.generalError != null) Spacer(Modifier.height(Space.lg))

        IronTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = "Email",
            error = state.emailError,
            enabled = !state.isSubmitting,
            keyboardType = KeyboardType.Email,
            modifier = Modifier.onFocusChanged { if (!it.isFocused) viewModel.onEmailBlur() }
        )
        Spacer(Modifier.height(Space.md))

        IronTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = "Password",
            error = state.passwordError,
            enabled = !state.isSubmitting,
            isPassword = true,
            imeAction = ImeAction.Done
        )
        Spacer(Modifier.height(Space.xl))

        PrimaryButton(
            text = "Sign in",
            onClick = viewModel::signIn,
            enabled = state.canSubmitSignIn,
            loading = state.isSubmitting
        )
        Spacer(Modifier.height(Space.md))

        TextButton(
            onClick = {
                // The email is what the reset applies to, so it has to be
                // filled in before the device lock is worth asking for.
                when {
                    state.email.isBlank() ->
                        viewModel.showError("Enter your email first, then reset the password")
                    biometricGate == null || !biometricGate.isAvailable() ->
                        viewModel.showError(
                            "Set a screen lock, fingerprint or face unlock on this phone to reset a password"
                        )
                    else -> biometricGate.prompt(
                        activity = activity!!,
                        title = "Reset your password",
                        subtitle = "Unlock this phone to set a new password",
                        onSuccess = { resetting = true },
                        onFailure = { viewModel.showError(it) },
                        onCancelled = { }
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Forgotten your password?", color = ChalkDim)
        }

        TextButton(
            onClick = {
                viewModel.reset()
                onNavigateToRegister()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("No account yet? Create one", color = ChalkDim)
        }
    }

    if (resetting) {
        NewPasswordDialog(
            email = state.email,
            isSubmitting = state.isSubmitting,
            onDismiss = { resetting = false },
            onConfirm = { password ->
                viewModel.resetPasswordAfterDeviceAuth(password)
                resetting = false
            }
        )
    }
}

/**
 * The hosting activity, which BiometricPrompt needs to show its dialog.
 *
 * Walks the ContextWrapper chain rather than casting: a Compose LocalContext
 * can be a themed wrapper around the activity rather than the activity itself.
 */
private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

/**
 * Sets the replacement password once the device lock has been satisfied.
 *
 * The old password is never asked for, and never shown: it cannot be, because
 * only a salted hash of it was ever stored.
 */
@Composable
private fun NewPasswordDialog(
    email: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val mismatch = confirm.isNotEmpty() && password != confirm

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New password", color = Chalk) },
        text = {
            Column {
                Text(
                    "For $email. Your training history is untouched.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ChalkDim
                )
                Spacer(Modifier.height(Space.md))
                IronTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "New password",
                    isPassword = true,
                    enabled = !isSubmitting
                )
                Spacer(Modifier.height(Space.md))
                IronTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = "Confirm password",
                    isPassword = true,
                    enabled = !isSubmitting,
                    error = if (mismatch) "Passwords don't match" else null,
                    imeAction = ImeAction.Done
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = !isSubmitting && password.isNotBlank() && password == confirm
            ) { Text("Save", color = Chalk) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = ChalkDim) } },
        containerColor = Slate700
    )
}

@Composable
fun RegisterScreen(
    onRegistered: () -> Unit,
    onNavigateToSignIn: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) onRegistered()
    }

    AuthScaffold(
        title = "Start your log",
        subtitle = "Everything stays on this phone. Nothing is uploaded."
    ) {
        ErrorBanner(state.generalError)
        if (state.generalError != null) Spacer(Modifier.height(Space.lg))

        IronTextField(
            value = state.name,
            onValueChange = viewModel::onNameChange,
            label = "Name",
            error = state.nameError,
            enabled = !state.isSubmitting
        )
        Spacer(Modifier.height(Space.md))

        IronTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = "Email",
            error = state.emailError,
            enabled = !state.isSubmitting,
            keyboardType = KeyboardType.Email,
            modifier = Modifier.onFocusChanged { if (!it.isFocused) viewModel.onEmailBlur() }
        )
        Spacer(Modifier.height(Space.md))

        IronTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = "Password",
            placeholder = "At least 8 characters",
            error = state.passwordError,
            enabled = !state.isSubmitting,
            isPassword = true,
            modifier = Modifier.onFocusChanged { if (!it.isFocused) viewModel.onPasswordBlur() }
        )
        Spacer(Modifier.height(Space.md))

        IronTextField(
            value = state.confirmPassword,
            onValueChange = viewModel::onConfirmPasswordChange,
            label = "Confirm password",
            error = state.confirmPasswordError,
            enabled = !state.isSubmitting,
            isPassword = true,
            imeAction = ImeAction.Done
        )
        Spacer(Modifier.height(Space.xl))

        PrimaryButton(
            text = "Create account",
            onClick = viewModel::register,
            enabled = state.canSubmitRegister,
            loading = state.isSubmitting
        )
        Spacer(Modifier.height(Space.md))

        TextButton(
            onClick = {
                viewModel.reset()
                onNavigateToSignIn()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Already have an account? Sign in", color = ChalkDim)
        }
    }
}

/**
 * Shared frame for both auth screens. `imePadding` keeps the submit button above
 * the keyboard, and the scroll container stops short phones from clipping the
 * form when the keyboard is up.
 */
@Composable
private fun AuthScaffold(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = Space.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(Modifier.height(72.dp))
        Text(
            text = "LIFTOSAUR",
            style = MaterialTheme.typography.labelMedium,
            color = ChalkDim
        )
        Spacer(Modifier.height(Space.xl))
        Text(title, style = MaterialTheme.typography.displayMedium, color = Chalk)
        Spacer(Modifier.height(Space.sm))
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = ChalkDim)
        Spacer(Modifier.height(Space.section))

        content()

        Spacer(Modifier.height(Space.section))
    }
}
