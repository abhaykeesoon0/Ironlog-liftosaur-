package com.ironlog.app.ui.screens.profile

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.core.permissions.AppPermission
import com.ironlog.app.core.permissions.openAppSettings
import com.ironlog.app.core.permissions.rememberPermissionState
import com.ironlog.app.core.security.BiometricAvailability
import com.ironlog.app.data.entity.PlateSet
import com.ironlog.app.data.entity.StepSource
import com.ironlog.app.data.repository.EquipmentSettings
import com.ironlog.app.data.entity.UnitSystem
import com.ironlog.app.data.repository.ExportKind
import com.ironlog.app.ui.components.Caption
import com.ironlog.app.ui.components.ErrorBanner
import com.ironlog.app.ui.components.Hairline
import com.ironlog.app.ui.components.IronCard
import com.ironlog.app.ui.components.IronChip
import com.ironlog.app.ui.components.IronTextField
import com.ironlog.app.ui.components.IronTopBar
import com.ironlog.app.ui.components.SecondaryButton
import com.ironlog.app.domain.usecase.TrainingMath
import com.ironlog.app.ui.components.SectionHeader
import com.ironlog.app.ui.format.formatClock
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.ChalkFaint
import com.ironlog.app.ui.theme.Danger
import com.ironlog.app.ui.theme.Slate800
import com.ironlog.app.ui.theme.Space
import com.ironlog.app.ui.theme.Touch

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onChangePassword: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val reminder by viewModel.reminder.collectAsStateWithLifecycle()
    val welcomeSound by viewModel.welcomeSound.collectAsStateWithLifecycle()
    val equipment by viewModel.equipment.collectAsStateWithLifecycle()
    val stepSource by viewModel.stepSource.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notifications = rememberPermissionState(AppPermission.NOTIFICATIONS)
    var showDelete by remember { mutableStateOf(false) }

    // Permission and biometric state can change in system Settings while the app
    // is in the background, so both are re-read on every resume.
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }

    LaunchedEffect(state.shareIntent) {
        val intent = state.shareIntent ?: return@LaunchedEffect
        runCatching { context.startActivity(intent) }
            .onSuccess { viewModel.onShareLaunched() }
            .onFailure { viewModel.onShareFailed() }
    }

    Column(Modifier.fillMaxSize()) {
        IronTopBar(title = "Settings", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg)
        ) {
            ErrorBanner(state.error)
            if (state.error != null) {
                TextButton(onClick = viewModel::dismissError) { Text("Dismiss", color = ChalkDim) }
            }

            val u = user
            // ---------------------------------------------------------- units
            SectionHeader("Units")
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                UnitSystem.entries.forEach { unit ->
                    IronChip(unit.label, u?.unitSystem == unit) { viewModel.setUnit(unit) }
                }
            }
            Caption("Everything is stored in kilograms; this only changes what you see.", Modifier.padding(top = Space.sm))

            // ---------------------------------------------------------- training
            Spacer(Modifier.height(Space.xl))
            SectionHeader("Training")
            IronCard {
                Column {
                    val rest = u?.restTimerDefaultSeconds ?: 120
                    Stepper(
                        label = "Default rest",
                        value = formatClock(rest),
                        onDecrement = { viewModel.changeRest(-SettingsViewModel.REST_STEP) },
                        onIncrement = { viewModel.changeRest(SettingsViewModel.REST_STEP) },
                        caption = "Used for exercises added outside a routine",
                        canDecrement = rest > SettingsViewModel.MIN_REST,
                        canIncrement = rest < SettingsViewModel.MAX_REST
                    )
                    Hairline(Modifier.padding(vertical = Space.sm))
                    SwitchRow(
                        title = "Start rest timer automatically",
                        caption = "Counts down when you tick off a set",
                        checked = u?.autoRestTimerEnabled ?: true,
                        onCheckedChange = viewModel::setAutoRest
                    )
                    Hairline(Modifier.padding(vertical = Space.sm))
                    val goal = u?.weeklyGoalWorkouts ?: 4
                    Stepper(
                        label = "Weekly goal",
                        value = "$goal",
                        onDecrement = { viewModel.changeGoal(-1) },
                        onIncrement = { viewModel.changeGoal(1) },
                        caption = "Workouts per week",
                        canDecrement = goal > 1,
                        canIncrement = goal < 14
                    )
                }
            }

            // -------------------------------------------------------------- steps
            Spacer(Modifier.height(Space.xl))
            SectionHeader("Steps")
            IronCard {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        StepSource.entries.forEach { option ->
                            IronChip(
                                text = option.label,
                                selected = stepSource == option,
                                onClick = { viewModel.setStepSource(option) }
                            )
                        }
                    }
                    Spacer(Modifier.height(Space.sm))
                    Caption(stepSource.detail)
                    if (stepSource == StepSource.HEALTH_CONNECT) {
                        Caption(
                            "A watch cannot be read over Bluetooth directly, so its steps are " +
                                "collected from Health Connect, where your watch's own app writes them.",
                            Modifier.padding(top = Space.xs)
                        )
                    }
                }
            }

            // ---------------------------------------------------------- equipment
            Spacer(Modifier.height(Space.xl))
            SectionHeader("Equipment")
            IronCard {
                Column {
                    Text(
                        "Plates in the gym",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Chalk
                    )
                    Spacer(Modifier.height(Space.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        PlateSet.entries.forEach { option ->
                            IronChip(
                                text = option.label,
                                selected = equipment.plateSet == option,
                                onClick = { viewModel.setPlateSet(option) }
                            )
                        }
                    }
                    Spacer(Modifier.height(Space.sm))
                    Caption(
                        TrainingMath.platesFor(equipment.plateSet)
                            .joinToString(" · ") { TrainingMath.plateLabel(equipment.plateSet, it) }
                    )
                    Caption(
                        "Follows the unit above when you change it. Set it back here if you read in one unit and load the other.",
                        Modifier.padding(top = Space.xs)
                    )
                    Hairline(Modifier.padding(vertical = Space.sm))
                    Stepper(
                        label = "Default bar",
                        value = TrainingMath.plateLabel(equipment.plateSet, equipment.barWeightKg),
                        onDecrement = { viewModel.changeBar(-1) },
                        onIncrement = { viewModel.changeBar(1) },
                        caption = "Used when an exercise has no bar weight of its own",
                        canDecrement = equipment.barWeightKg > EquipmentSettings.MIN_BAR_KG,
                        canIncrement = equipment.barWeightKg < EquipmentSettings.MAX_BAR_KG
                    )
                }
            }

            // -------------------------------------------------------------- sound
            Spacer(Modifier.height(Space.xl))
            SectionHeader("Sound")
            IronCard {
                SwitchRow(
                    title = "Welcome sound",
                    caption = "A short chime when you open the app. Stays quiet on silent, and never plays over music you are already listening to.",
                    checked = welcomeSound,
                    onCheckedChange = viewModel::setWelcomeSound
                )
            }

            // ---------------------------------------------------------- reminders
            Spacer(Modifier.height(Space.xl))
            SectionHeader("Reminders")
            IronCard {
                Column {
                    SwitchRow(
                        title = "Daily training reminder",
                        caption = "Only if you haven't trained that day",
                        checked = reminder.enabled,
                        onCheckedChange = { on ->
                            if (on) {
                                notifications.runWithPermission(onDenied = viewModel::onNotificationsDeclined) {
                                    viewModel.setReminderEnabled(true)
                                }
                            } else {
                                viewModel.setReminderEnabled(false)
                            }
                        }
                    )
                    if (reminder.enabled) {
                        Hairline(Modifier.padding(vertical = Space.sm))
                        Stepper(
                            label = "Hour",
                            value = "%02d".format(reminder.hour),
                            onDecrement = { viewModel.changeReminderHour(-1) },
                            onIncrement = { viewModel.changeReminderHour(1) }
                        )
                        Stepper(
                            label = "Minute",
                            value = "%02d".format(reminder.minute),
                            onDecrement = { viewModel.changeReminderMinute(-15) },
                            onIncrement = { viewModel.changeReminderMinute(15) },
                            caption = "Reminder at %02d:%02d".format(reminder.hour, reminder.minute)
                        )
                    }
                }
            }

            // ---------------------------------------------------------- security
            Spacer(Modifier.height(Space.xl))
            SectionHeader("Security")
            IronCard {
                Column {
                    val availability = remember(resumeTick) { viewModel.biometricAvailability }
                    val available = availability == BiometricAvailability.AVAILABLE
                    val lockOn = u?.biometricLockEnabled == true
                    SwitchRow(
                        title = "App lock",
                        caption = if (available || lockOn) {
                            "Fingerprint, face or screen lock when you open IronLog"
                        } else {
                            availabilityMessage(availability)
                        },
                        checked = lockOn,
                        // Always allowed to turn the lock *off*, even if the sensor has gone away.
                        enabled = available || lockOn,
                        onCheckedChange = { on ->
                            if (on) {
                                context.findFragmentActivity()?.let(viewModel::enableBiometricLock)
                            } else {
                                viewModel.disableBiometricLock()
                            }
                        }
                    )
                    Hairline(Modifier.padding(vertical = Space.sm))
                    NavRow(Icons.Default.Key, "Change password", onChangePassword)
                }
            }

            // ---------------------------------------------------------- permissions
            Spacer(Modifier.height(Space.xl))
            SectionHeader("Permissions")
            IronCard {
                Column {
                    AppPermission.entries.forEachIndexed { index, permission ->
                        val granted = remember(resumeTick) { permission.isGranted(context) }
                        PermissionRow(permission.title, granted, permission.rationale)
                        if (index < AppPermission.entries.lastIndex) Hairline(Modifier.padding(vertical = Space.sm))
                    }
                    Spacer(Modifier.height(Space.md))
                    SecondaryButton(text = "Manage in system settings", onClick = { openAppSettings(context) })
                }
            }

            // ---------------------------------------------------------- export
            Spacer(Modifier.height(Space.xl))
            SectionHeader("Your data")
            IronCard {
                Column {
                    Caption("All data stays on this device. Exports are created only when you ask, and shared through the app you choose.")
                    Spacer(Modifier.height(Space.sm))
                    ExportKind.entries.forEach { kind ->
                        ExportRow(
                            label = kind.label,
                            busy = state.exporting == kind,
                            enabled = state.exporting == null,
                            onClick = { viewModel.export(kind) }
                        )
                    }
                }
            }

            // ---------------------------------------------------------- danger
            Spacer(Modifier.height(Space.xl))
            SectionHeader("Account")
            SecondaryButton(text = "Delete account", onClick = { showDelete = true })
            Caption(
                "Permanently removes your workouts, routines, measurements and photos from this phone.",
                Modifier.padding(top = Space.sm)
            )
            Spacer(Modifier.height(Space.section))
        }
    }

    if (showDelete) {
        DeleteAccountDialog(
            isDeleting = state.isDeleting,
            error = state.deleteError,
            onConfirm = { password -> viewModel.deleteAccount(password) },
            onDismiss = {
                showDelete = false
                viewModel.clearDeleteError()
            }
        )
    }
}

@Composable
private fun PermissionRow(title: String, granted: Boolean, rationale: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Chalk)
            Text(rationale, style = MaterialTheme.typography.bodySmall, color = ChalkFaint, maxLines = 2)
        }
        Spacer(Modifier.width(Space.md))
        Text(
            if (granted) "Allowed" else "Off",
            style = MaterialTheme.typography.labelMedium,
            color = if (granted) Chalk else ChalkFaint
        )
    }
}

@Composable
private fun ExportRow(label: String, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Touch.min)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Export $label", onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = if (enabled || busy) Chalk else ChalkFaint, modifier = Modifier.weight(1f))
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), color = ChalkDim, strokeWidth = 2.dp)
        } else {
            Icon(Icons.Default.Download, contentDescription = null, tint = ChalkDim)
        }
    }
}

@Composable
private fun DeleteAccountDialog(
    isDeleting: Boolean,
    error: String?,
    onConfirm: (CharArray) -> Unit,
    onDismiss: () -> Unit
) {
    // A TextField can only hold a String; it is converted to a CharArray on
    // submit and cleared here, and the repository wipes the array.
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        containerColor = Slate800,
        icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Danger) },
        title = { Text("Delete your account?", color = Chalk) },
        text = {
            Column {
                Text(
                    "This erases every workout, routine, measurement, cardio session and progress photo. It cannot be undone. Enter your password to confirm.",
                    color = ChalkDim
                )
                Spacer(Modifier.height(Space.md))
                IronTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    isPassword = true,
                    error = error,
                    enabled = !isDeleting,
                    imeAction = ImeAction.Done
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = password.isNotEmpty() && !isDeleting,
                onClick = {
                    onConfirm(password.toCharArray())
                    password = ""
                }
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Danger, strokeWidth = 2.dp)
                } else {
                    Text("Delete everything", color = Danger)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDeleting) { Text("Cancel", color = ChalkDim) }
        }
    )
}

private fun availabilityMessage(availability: BiometricAvailability): String = when (availability) {
    BiometricAvailability.NONE_ENROLLED -> "Set up a fingerprint, face or screen lock in system settings first"
    BiometricAvailability.NO_HARDWARE -> "This phone has no biometric sensor or screen lock"
    BiometricAvailability.HARDWARE_UNAVAILABLE -> "The biometric sensor is unavailable right now"
    BiometricAvailability.UNSUPPORTED -> "App lock isn't supported on this phone"
    BiometricAvailability.AVAILABLE -> ""
}

/** Compose hands out the activity wrapped in context wrappers; walk down to it. */
private fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}
