package com.ironlog.app.ui.screens.profile

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.ironlog.app.data.entity.MuscleTint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.ironlog.app.core.security.BackupCrypto
import com.ironlog.app.data.repository.LOCK_GRACE_OPTIONS
import com.ironlog.app.ui.components.ConfirmDialog
import com.ironlog.app.ui.components.PassphraseDialog
import com.ironlog.app.data.repository.AutoBackupState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.material.icons.filled.Info
import com.ironlog.app.data.entity.TrainingFocus

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onChangePassword: () -> Unit,
    onOpenOnboarding: () -> Unit,
    onOpenPrivacy: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val reminder by viewModel.reminder.collectAsStateWithLifecycle()
    val welcomeSound by viewModel.welcomeSound.collectAsStateWithLifecycle()
    val equipment by viewModel.equipment.collectAsStateWithLifecycle()
    val muscleTint by viewModel.muscleTint.collectAsStateWithLifecycle()
    val trainingFocus by viewModel.trainingFocus.collectAsStateWithLifecycle()
    val stepSource by viewModel.stepSource.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notifications = rememberPermissionState(AppPermission.NOTIFICATIONS)
    var showDelete by remember { mutableStateOf(false) }
    val lockGrace by viewModel.lockGrace.collectAsStateWithLifecycle()
    val screenshotsBlocked by viewModel.screenshotsBlocked.collectAsStateWithLifecycle()
    var askBackupPassphrase by remember { mutableStateOf(false) }
    val autoBackup by viewModel.autoBackup.collectAsStateWithLifecycle()
    var askAutoBackupPassphrase by remember { mutableStateOf(false) }
    var confirmAutoRestore by remember { mutableStateOf(false) }
    var confirmCloudRestore by remember { mutableStateOf(false) }
    // The system folder picker: any cloud provider installed on the phone
    // appears in it, and the grant it returns outlives the app process.
    val pickCloudFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::setCloudFolder)
    }
    if (confirmCloudRestore) {
        ConfirmDialog(
            title = "Restore from the cloud folder?",
            body = "The newest sealed backup in the folder is opened with your stored passphrase and its workouts, " +
                "routines, measurements and cardio are added to this account. Nothing already here is changed.",
            confirmLabel = "Restore",
            onConfirm = { confirmCloudRestore = false; viewModel.restoreLatestCloud() },
            onDismiss = { confirmCloudRestore = false },
            destructive = false
        )
    }
    if (askAutoBackupPassphrase) {
        PassphraseDialog(
            title = "Turn on weekly backup",
            body = "Choose a passphrase of at least ${BackupCrypto.MIN_PASSPHRASE} characters. It is kept in this phone's " +
                "encrypted storage so backups can be sealed while you are not looking, and you will need it to open " +
                "one on another phone.",
            confirmLabel = "Turn on",
            minLength = BackupCrypto.MIN_PASSPHRASE,
            onConfirm = { pass -> askAutoBackupPassphrase = false; viewModel.enableAutoBackup(pass) },
            onDismiss = { askAutoBackupPassphrase = false }
        )
    }
    state.importUri?.let {
        ConfirmDialog(
            title = "Import this file?",
            body = "It looks like a Strong or Hevy export. Its workouts are added to this account, matched to the " +
                "library by exercise name. Nothing already here is changed.",
            confirmLabel = "Import",
            onConfirm = viewModel::confirmImport,
            onDismiss = viewModel::cancelImport,
            destructive = false
        )
    }
    if (confirmAutoRestore) {
        ConfirmDialog(
            title = "Restore the latest backup?",
            body = "Workouts, routines, measurements and cardio in the newest local copy are added to this account. " +
                "Nothing already here is changed.",
            confirmLabel = "Restore",
            onConfirm = { confirmAutoRestore = false; viewModel.restoreLatestAutomatic() },
            onDismiss = { confirmAutoRestore = false },
            destructive = false
        )
    }
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::onRestoreFileChosen)
    }
    val pickCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importCsv)
    }

    if (askBackupPassphrase) {
        PassphraseDialog(
            title = "Seal the backup",
            body = "Choose a passphrase of at least ${BackupCrypto.MIN_PASSPHRASE} characters. It is not stored anywhere: " +
                "lose it and the backup cannot be opened, by you or by anyone.",
            confirmLabel = "Create backup",
            minLength = BackupCrypto.MIN_PASSPHRASE,
            onConfirm = { pass -> askBackupPassphrase = false; viewModel.backupEncrypted(pass) },
            onDismiss = { askBackupPassphrase = false }
        )
    }
    state.restoreUri?.let {
        if (state.restoreNeedsPassphrase) {
            PassphraseDialog(
                title = "Open the backup",
                body = "Enter the passphrase this backup was sealed with.",
                confirmLabel = "Restore",
                minLength = 1,
                onConfirm = { pass -> viewModel.restore(pass) },
                onDismiss = viewModel::cancelRestore
            )
        } else {
            ConfirmDialog(
                title = "Restore this backup?",
                body = "Workouts, routines, measurements and cardio in the file are added to this account. Nothing already here is changed.",
                confirmLabel = "Restore",
                onConfirm = { viewModel.restore(null) },
                onDismiss = viewModel::cancelRestore,
                destructive = false
            )
        }
    }

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

    // Outcomes of the data actions -- an import, a restore, a backup -- are
    // one-shot notices, shown where the user is looking rather than at the
    // top of a long page they have scrolled away from.
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { text ->
            snackbar.showSnackbar(text)
            viewModel.dismissMessage()
        }
    }

    Box(Modifier.fillMaxSize()) {
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

            // ---------------------------------------------------------- training
            Spacer(Modifier.height(Space.xl))
            SectionHeader("Training")
            IronCard {
                Column {
                    Text("Training for", style = MaterialTheme.typography.bodyLarge, color = Chalk)
                    Spacer(Modifier.height(Space.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        TrainingFocus.entries.forEach { option ->
                            IronChip(
                                text = option.label,
                                selected = trainingFocus == option,
                                onClick = { viewModel.setTrainingFocus(option) }
                            )
                        }
                    }
                    Caption(
                        "${trainingFocus.blurb}. Predictions of your next sets are written for " +
                        "${trainingFocus.reps} reps and worked out from your own sessions, on this phone.",
                        Modifier.padding(top = Space.sm)
                    )
                }
            }

            // --------------------------------------------------------- appearance
            Spacer(Modifier.height(Space.xl))
            SectionHeader("Appearance")
            IronCard {
                Column {
                    Text("Trained muscles", style = MaterialTheme.typography.bodyLarge, color = Chalk)
                    Spacer(Modifier.height(Space.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        MuscleTint.entries.forEach { option ->
                            IronChip(
                                text = option.label,
                                selected = muscleTint == option,
                                onClick = { viewModel.setMuscleTint(option) }
                            )
                        }
                    }
                    Caption(
                        "The colour the figure on the workout screen fills a worked muscle with.",
                        Modifier.padding(top = Space.xs)
                    )
                }
            }

            // --------------------------------------------------------- home screen
            Spacer(Modifier.height(Space.xl))
            SectionHeader("Home screen")
            IronCard {
                Column {
                    Text("Widget", style = MaterialTheme.typography.bodyLarge, color = Chalk)
                    Caption("This week's sessions, your streak and today's food, on the home screen.")
                    Spacer(Modifier.height(Space.sm))
                    SecondaryButton(text = "Add to home screen", onClick = { viewModel.addWidget(context) })
                    Hairline(Modifier.padding(vertical = Space.sm))
                    NavRow(Icons.Default.Info, "Replay the introduction", onOpenOnboarding, "The three screens from first launch")
                    Hairline(Modifier.padding(vertical = Space.sm))
                    NavRow(Icons.Default.Lock, "Privacy & your data", onOpenPrivacy, "What is stored, how it is protected, what ever leaves the phone")
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
                            "Fingerprint, face or screen lock when you open Liftosaur"
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
                    if (lockOn) {
                        Spacer(Modifier.height(Space.sm))
                        Text("Ask again after", style = MaterialTheme.typography.bodyLarge, color = Chalk)
                        Spacer(Modifier.height(Space.xs))
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            LOCK_GRACE_OPTIONS.forEach { seconds ->
                                IronChip(
                                    text = when {
                                        seconds == 0 -> "Straight away"
                                        seconds < 60 -> "${seconds}s"
                                        else -> "${seconds / 60} min"
                                    },
                                    selected = lockGrace == seconds,
                                    onClick = { viewModel.setLockGrace(seconds) }
                                )
                            }
                        }
                        Caption("How long Liftosaur can be in the background before the lock asks again.", Modifier.padding(top = Space.xs))
                    }
                    Hairline(Modifier.padding(vertical = Space.sm))
                    // The photos screen is always uncapturable; this extends that
                    // to the whole app, including the thumbnail in recents.
                    SwitchRow(
                        title = "Block screenshots",
                        caption = "No screenshots, screen recording or recents preview of any screen. Photos are always protected.",
                        checked = screenshotsBlocked,
                        onCheckedChange = viewModel::setScreenshotsBlocked
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
                    Hairline(Modifier.padding(vertical = Space.sm))
                    // The two that carry the whole log across phones. The sealed
                    // backup is the one to keep; the plain JSON above is for reading.
                    ExportRow(
                        label = "Encrypted backup (.liftosaur)",
                        busy = state.isBackingUp,
                        enabled = !state.isBackingUp && !state.isRestoring,
                        onClick = { askBackupPassphrase = true }
                    )
                    ExportRow(
                        label = "Restore from a backup",
                        busy = state.isRestoring,
                        enabled = !state.isBackingUp && !state.isRestoring,
                        onClick = { pickBackup.launch(arrayOf("*/*")) }
                    )
                    ExportRow(
                        label = "Import from Strong or Hevy (CSV)",
                        busy = state.isRestoring,
                        enabled = !state.isBackingUp && !state.isRestoring,
                        onClick = { pickCsv.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values", "application/csv", "*/*")) }
                    )
                    Caption(
                        "A backup is sealed with a passphrase of your choosing and can be restored on any phone with " +
                            "Liftosaur. Restoring adds what is missing and leaves what is already here.",
                        Modifier.padding(top = Space.xs)
                    )
                    Hairline(Modifier.padding(vertical = Space.sm))
                    // The weekly copy: the one backup that exists without the user
                    // remembering to make it.
                    SwitchRow(
                        title = "Weekly automatic backup",
                        checked = autoBackup.enabled,
                        onCheckedChange = { on ->
                            if (on) askAutoBackupPassphrase = true else viewModel.disableAutoBackup()
                        },
                        caption = autoBackupCaption(autoBackup),
                        enabled = !state.isBackingUp && !state.isRestoring
                    )
                    if (autoBackup.enabled) {
                        ExportRow(
                            label = "Back up now",
                            busy = state.isBackingUp,
                            enabled = !state.isBackingUp && !state.isRestoring,
                            onClick = viewModel::backupNow
                        )
                        if (autoBackup.hasLocalCopy) {
                            ExportRow(
                                label = "Restore the latest automatic backup",
                                busy = state.isRestoring,
                                enabled = !state.isBackingUp && !state.isRestoring,
                                onClick = { confirmAutoRestore = true }
                            )
                        }
                        Caption(
                            "Sealed copies stay in Liftosaur's private storage on this phone; the last four are kept. " +
                                "Turning this off deletes them and forgets the passphrase.",
                            Modifier.padding(top = Space.xs)
                        )
                        Hairline(Modifier.padding(vertical = Space.sm))
                        // The copy that survives a lost phone. The folder is the
                        // user's own, in whatever cloud app they already use.
                        if (autoBackup.cloudFolderName == null) {
                            ExportRow(
                                label = "Also keep a copy in a cloud folder",
                                busy = false,
                                enabled = !state.isBackingUp && !state.isRestoring,
                                onClick = { pickCloudFolder.launch(null) }
                            )
                            Caption(
                                "Pick a folder in Google Drive, OneDrive, Dropbox or any other storage app on this phone. " +
                                    "Only the sealed file goes there; the passphrase never leaves the phone, so the service " +
                                    "cannot read your log.",
                                Modifier.padding(top = Space.xs)
                            )
                        } else {
                            Text("Cloud folder: ${autoBackup.cloudFolderName}", style = MaterialTheme.typography.titleMedium, color = Chalk)
                            Caption(cloudCaption(autoBackup))
                            ExportRow(
                                label = "Restore the latest cloud copy",
                                busy = state.isRestoring,
                                enabled = !state.isBackingUp && !state.isRestoring,
                                onClick = { confirmCloudRestore = true }
                            )
                            ExportRow(
                                label = "Change the cloud folder",
                                busy = false,
                                enabled = !state.isBackingUp && !state.isRestoring,
                                onClick = { pickCloudFolder.launch(null) }
                            )
                            ExportRow(
                                label = "Stop copying to the cloud",
                                busy = false,
                                enabled = !state.isBackingUp && !state.isRestoring,
                                onClick = viewModel::clearCloudFolder
                            )
                        }
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
    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(Space.lg))
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

/** Under the cloud folder's name: when the last copy reached it, or why it did not. */
private fun cloudCaption(state: AutoBackupState): String = when {
    state.lastCloudError != null -> state.lastCloudError
    state.lastCloudBackupAt == null -> "No copy has reached the folder yet."
    else -> "Last copy " + DateTimeFormatter.ofPattern("d MMM, HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(state.lastCloudBackupAt))
}

/** One line under the switch: when the last copy was made, or why it was not. */
private fun autoBackupCaption(state: AutoBackupState): String = when {
    !state.enabled -> "A sealed copy of your log, made every week and kept on this phone."
    state.lastError != null -> state.lastError
    state.lastBackupAt == null -> "The first backup is on its way."
    else -> "Last backup " + DateTimeFormatter.ofPattern("d MMM, HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(state.lastBackupAt))
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
