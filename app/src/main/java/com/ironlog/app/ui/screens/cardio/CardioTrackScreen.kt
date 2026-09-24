package com.ironlog.app.ui.screens.cardio

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.core.location.CardioProgress
import com.ironlog.app.core.location.CardioTrackingService
import com.ironlog.app.core.location.GeoMath
import com.ironlog.app.core.permissions.AppPermission
import com.ironlog.app.core.permissions.rememberPermissionState
import com.ironlog.app.data.entity.CardioType
import com.ironlog.app.data.entity.UnitSystem
import com.ironlog.app.ui.components.Caption
import com.ironlog.app.ui.components.ConfirmDialog
import com.ironlog.app.ui.components.ErrorBanner
import com.ironlog.app.ui.components.IronCard
import com.ironlog.app.ui.components.IronChip
import com.ironlog.app.ui.components.IronTopBar
import com.ironlog.app.ui.components.PrimaryButton
import com.ironlog.app.ui.components.RouteShape
import com.ironlog.app.ui.components.SecondaryButton
import com.ironlog.app.ui.components.StatTile
import com.ironlog.app.ui.format.formatClock
import com.ironlog.app.ui.format.formatDistance
import com.ironlog.app.ui.format.formatPace
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.ChalkFaint
import com.ironlog.app.ui.theme.NumericType
import com.ironlog.app.ui.theme.Plate10
import com.ironlog.app.ui.theme.Plate15
import com.ironlog.app.ui.theme.Radius
import com.ironlog.app.ui.theme.Slate800
import com.ironlog.app.ui.theme.Space
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.contentDescription
import com.ironlog.app.core.sensors.HeartRateMonitor
import com.ironlog.app.ui.theme.Plate25

private enum class Phase { SETUP, LIVE, STOPPED }

private val CardioProgress.phase: Phase
    get() = when {
        isTracking -> Phase.LIVE
        elapsedSeconds > 0 || points.isNotEmpty() -> Phase.STOPPED
        else -> Phase.SETUP
    }

@Composable
fun CardioTrackScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: CardioTrackViewModel = hiltViewModel()
) {
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unit by viewModel.unit.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmLeave by remember { mutableStateOf(false) }

    val location = rememberPermissionState(AppPermission.LOCATION)
    val notifications = rememberPermissionState(AppPermission.NOTIFICATIONS)
    val activity = rememberPermissionState(AppPermission.ACTIVITY_RECOGNITION)

    LaunchedEffect(state.savedId) { state.savedId?.let(onSaved) }
    LaunchedEffect(state.discarded) { if (state.discarded) onBack() }

    val phase = progress.phase

    fun startTracking() {
        location.runWithPermission {
            if (!viewModel.isLocationEnabled()) {
                viewModel.showError("Location is switched off. Turn on location services in quick settings, then start again.")
            } else {
                runCatching { CardioTrackingService.start(context) }
                    .onSuccess { viewModel.onTrackingStarted() }
                    .onFailure { viewModel.showError("Tracking couldn't start. Keep Liftosaur open on screen and try again.") }
            }
        }
    }

    fun requestStart() {
        // Notifications are optional here: without them the session still records,
        // the ongoing status just isn't visible in the shade.
        notifications.runWithPermission(onDenied = ::startTracking) { startTracking() }
    }

    fun leave() {
        if (phase == Phase.LIVE) confirmLeave = true else onBack()
    }

    BackHandler(enabled = phase == Phase.LIVE) { confirmLeave = true }

    val hrState by viewModel.heartRateState.collectAsStateWithLifecycle()
    val hrDevices by viewModel.heartRateDevices.collectAsStateWithLifecycle()
    val bluetoothScan = rememberPermissionState(AppPermission.BLUETOOTH_SCAN)
    val bluetoothConnect = rememberPermissionState(AppPermission.BLUETOOTH_CONNECT)
    var pickingStrap by remember { mutableStateOf(false) }
    if (pickingStrap) {
        StrapPicker(
            devices = hrDevices,
            scanning = hrState is HeartRateMonitor.State.Scanning,
            onPick = { device -> pickingStrap = false; viewModel.connectStrap(device) },
            onDismiss = { pickingStrap = false; viewModel.stopScanning() }
        )
    }

    Column(Modifier.fillMaxSize()) {
        IronTopBar(title = "Outdoor cardio", onBack = ::leave)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg)
        ) {
            ErrorBanner(state.error ?: progress.error)
            if (state.error != null || progress.error != null) Spacer(Modifier.height(Space.md))

            when (phase) {
                Phase.SETUP -> SetupPanel(
                    selected = state.type,
                    onSelect = viewModel::selectType,
                    onStart = ::requestStart
                )

                Phase.LIVE, Phase.STOPPED -> {
                    LiveReadout(progress, unit)
                    Spacer(Modifier.height(Space.lg))

                    val normalised = remember(progress.points) { GeoMath.normalise(progress.points) }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .background(Slate800, RoundedCornerShape(Radius.card)),
                        contentAlignment = Alignment.Center
                    ) {
                        RouteShape(normalised, Modifier.fillMaxSize())
                        if (normalised.size < 2) {
                            Caption(if (phase == Phase.LIVE) "Your route will appear as you move" else "No route recorded")
                        }
                    }
                    Spacer(Modifier.height(Space.lg))

                    if (viewModel.heartRateMonitor.isAvailable()) {
                        HeartRateRow(
                            bpm = state.heartRate,
                            hrState = hrState,
                            onConnect = {
                                bluetoothScan.runWithPermission {
                                    bluetoothConnect.runWithPermission { pickingStrap = true; viewModel.scanForStrap() }
                                }
                            },
                            onDisconnect = viewModel::disconnectStrap
                        )
                        Spacer(Modifier.height(Space.md))
                    }
                    StepsRow(
                        steps = state.steps,
                        available = viewModel.canCountSteps,
                        granted = activity.isGranted,
                        onEnable = {
                            activity.runWithPermission { viewModel.startStepCounting() }
                        },
                        enabled = phase == Phase.LIVE
                    )
                    Spacer(Modifier.height(Space.xl))

                    if (phase == Phase.LIVE) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            SecondaryButton(
                                text = if (progress.isPaused) "Resume" else "Pause",
                                leadingIcon = if (progress.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                modifier = Modifier.weight(1f).height(56.dp),
                                onClick = {
                                    runCatching {
                                        if (progress.isPaused) CardioTrackingService.resume(context)
                                        else CardioTrackingService.pause(context)
                                    }.onFailure { viewModel.showError("Couldn't reach the tracker. Try again.") }
                                }
                            )
                            PrimaryButton(
                                text = "Finish",
                                leadingIcon = Icons.Default.Stop,
                                loading = state.isSaving,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    runCatching { CardioTrackingService.stop(context) }
                                        .onFailure { viewModel.showError("Couldn't stop the tracker cleanly; saving what was recorded.") }
                                    viewModel.save()
                                }
                            )
                        }
                    } else {
                        // Stopped but not saved: either a save failed or tracking
                        // ended on its own (e.g. location was switched off).
                        PrimaryButton(
                            text = "Save session",
                            onClick = viewModel::save,
                            loading = state.isSaving
                        )
                        Spacer(Modifier.height(Space.sm))
                        SecondaryButton(text = "Discard", onClick = viewModel::discard, enabled = !state.isSaving)
                    }
                }
            }
            Spacer(Modifier.height(Space.section))
        }
    }

    if (confirmLeave) {
        ConfirmDialog(
            title = "Keep recording in the background?",
            body = "Tracking continues with a notification showing distance and time. Tap it, or open Outdoor cardio, to come back and finish.",
            confirmLabel = "Keep recording",
            destructive = false,
            onConfirm = {
                confirmLeave = false
                onBack()
            },
            onDismiss = { confirmLeave = false }
        )
    }
}

@Composable
private fun SetupPanel(selected: CardioType, onSelect: (CardioType) -> Unit, onStart: () -> Unit) {
    Text("What are you doing?", style = MaterialTheme.typography.titleLarge, color = Chalk)
    Spacer(Modifier.height(Space.md))
    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        CardioType.entries.forEach { type ->
            IronChip(type.label, selected == type) { onSelect(type) }
        }
    }
    Spacer(Modifier.height(Space.xl))
    IronCard {
        Column {
            Text("How tracking works", style = MaterialTheme.typography.titleMedium, color = Chalk)
            Spacer(Modifier.height(Space.xs))
            Text(
                "GPS records only while this session runs and stops the moment you finish. " +
                    "The route is kept on this phone and is never uploaded.",
                style = MaterialTheme.typography.bodyMedium,
                color = ChalkDim
            )
        }
    }
    Spacer(Modifier.height(Space.xl))
    PrimaryButton(text = "Start ${selected.label.lowercase()}", onClick = onStart, leadingIcon = Icons.Default.PlayArrow)
}

@Composable
private fun LiveReadout(progress: CardioProgress, unit: UnitSystem) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val (dotColor, label) = when {
            !progress.isTracking -> ChalkFaint to "Stopped"
            progress.isPaused -> Plate15 to "Paused"
            progress.hasSignal -> Plate10 to "GPS locked"
            else -> ChalkFaint to "Waiting for GPS…"
        }
        Box(Modifier.size(10.dp).background(dotColor, CircleShape))
        Spacer(Modifier.width(Space.sm))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = ChalkDim,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
    Spacer(Modifier.height(Space.lg))
    Text(unit.formatDistance(progress.distanceMeters), style = NumericType.hero, color = Chalk)
    Caption("Distance")
    Spacer(Modifier.height(Space.lg))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        StatTile(formatClock(progress.elapsedSeconds), "Elapsed")
        StatTile(unit.formatPace(progress.paceSecondsPerKm), "Avg pace")
        StatTile(progress.points.size.toString(), "GPS points")
    }
}

@Composable
private fun StepsRow(steps: Int?, available: Boolean, granted: Boolean, enabled: Boolean, onEnable: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    steps != null -> "%,d".format(steps)
                    !available -> "—"
                    granted -> "0"
                    else -> "Off"
                },
                style = NumericType.stat,
                color = Chalk
            )
            Caption(if (available) "Steps this session" else "This phone has no step sensor")
        }
        if (available && !granted && enabled) {
            TextButton(onClick = onEnable) { Text("Count steps", color = Chalk) }
        }
    }
}

/** Live beats per minute from a strap, or the button that finds one. */
@Composable
private fun HeartRateRow(
    bpm: Int?,
    hrState: HeartRateMonitor.State,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    IronCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Favorite, contentDescription = null, tint = if (bpm != null) Plate25 else ChalkDim)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                when (hrState) {
                    is HeartRateMonitor.State.Connected -> {
                        Text(
                            bpm?.let { "$it bpm" } ?: "\u2014",
                            style = NumericType.stat,
                            color = Chalk,
                            modifier = Modifier.semantics { contentDescription = bpm?.let { "$it beats per minute" } ?: "No reading yet" }
                        )
                        Caption(hrState.device.name)
                    }
                    is HeartRateMonitor.State.Connecting -> {
                        Text("Connecting", style = MaterialTheme.typography.bodyLarge, color = Chalk)
                        Caption(hrState.device.name)
                    }
                    is HeartRateMonitor.State.Failed -> {
                        Text("Heart rate", style = MaterialTheme.typography.bodyLarge, color = Chalk)
                        Caption(hrState.message)
                    }
                    else -> {
                        Text("Heart rate", style = MaterialTheme.typography.bodyLarge, color = Chalk)
                        Caption("Connect a Bluetooth strap or watch that shares its heart rate")
                    }
                }
            }
            when (hrState) {
                is HeartRateMonitor.State.Connected, is HeartRateMonitor.State.Connecting ->
                    TextButton(onClick = onDisconnect) { Text("Disconnect", color = ChalkDim) }
                else -> TextButton(onClick = onConnect) { Text("Connect", color = Chalk) }
            }
        }
    }
}

/** The straps found so far; the list fills as the scan runs. */
@Composable
private fun StrapPicker(
    devices: List<HeartRateMonitor.Device>,
    scanning: Boolean,
    onPick: (HeartRateMonitor.Device) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Slate800,
        title = { Text("Heart-rate sensors", color = Chalk) },
        text = {
            Column {
                if (devices.isEmpty()) {
                    Caption(if (scanning) "Looking for straps nearby\u2026 make sure it is on and worn." else "Nothing found yet.")
                }
                devices.forEach { device ->
                    Text(
                        device.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Chalk,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClickLabel = "Connect to ${device.name}") { onPick(device) }
                            .padding(vertical = Space.sm)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = ChalkDim) } }
    )
}
