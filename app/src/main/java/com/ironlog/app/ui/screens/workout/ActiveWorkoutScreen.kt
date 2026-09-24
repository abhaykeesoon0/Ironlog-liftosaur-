package com.ironlog.app.ui.screens.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.core.permissions.AppPermission
import com.ironlog.app.core.permissions.rememberPermissionState
import com.ironlog.app.data.entity.ExerciseCategory
import com.ironlog.app.data.entity.UnitSystem
import com.ironlog.app.data.entity.WorkoutSetEntity
import com.ironlog.app.ui.components.Caption
import com.ironlog.app.ui.components.ConfirmDialog
import com.ironlog.app.ui.components.EmptyState
import com.ironlog.app.ui.components.IronCard
import com.ironlog.app.ui.components.IronTextField
import com.ironlog.app.ui.components.IronTopBar
import com.ironlog.app.ui.components.LoadingBlock
import com.ironlog.app.ui.components.PrimaryButton
import com.ironlog.app.ui.components.SecondaryButton
import com.ironlog.app.ui.components.StatTile
import com.ironlog.app.ui.format.formatClock
import com.ironlog.app.ui.format.formatVolume
import com.ironlog.app.ui.screens.routines.Stepper
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.Danger
import com.ironlog.app.ui.theme.NumericType
import com.ironlog.app.ui.theme.Radius
import com.ironlog.app.ui.theme.Slate700
import com.ironlog.app.ui.theme.Slate800
import com.ironlog.app.ui.theme.Space
import kotlinx.coroutines.delay
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import com.ironlog.app.data.entity.MuscleGroup
import com.ironlog.app.data.entity.MuscleTint
import com.ironlog.app.data.entity.SetType
import com.ironlog.app.ui.components.TrainedMuscles
import com.ironlog.app.ui.theme.Slate600
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.ironlog.app.ui.components.UndoSnackbar

@Composable
fun ActiveWorkoutScreen(
    onClose: () -> Unit,
    onFinished: (String) -> Unit,
    onAddExercises: () -> Unit,
    onOpenExercise: (String) -> Unit,
    pickedExerciseIds: List<String>?,
    onPickedConsumed: () -> Unit,
    viewModel: ActiveWorkoutViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val event by viewModel.event.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val restRunning by viewModel.restRunning.collectAsStateWithLifecycle()
    val restRemaining by viewModel.restRemaining.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val undo by viewModel.undo.collectAsStateWithLifecycle()
    UndoSnackbar(snackbar, undo) { viewModel.consumeUndo() }
    val notifications = rememberPermissionState(AppPermission.NOTIFICATIONS)
    var notificationCardDismissed by rememberSaveable { mutableStateOf(false) }

    var renameOpen by remember { mutableStateOf(false) }
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<ExerciseBlock?>(null) }
    var notesTarget by remember { mutableStateOf<ExerciseBlock?>(null) }
    var restTarget by remember { mutableStateOf<ExerciseBlock?>(null) }
    var plateTarget by remember { mutableStateOf<ExerciseBlock?>(null) }
    var repTarget by remember { mutableStateOf<Pair<ExerciseBlock, WorkoutSetEntity>?>(null) }

    LaunchedEffect(pickedExerciseIds) {
        val ids = pickedExerciseIds ?: return@LaunchedEffect
        viewModel.addExercises(ids)
        onPickedConsumed()
    }

    LaunchedEffect(event) {
        when (val current = event) {
            ActiveWorkoutEvent.Closed -> {
                viewModel.consumeEvent()
                onClose()
            }
            is ActiveWorkoutEvent.Finished -> {
                viewModel.consumeEvent()
                onFinished(current.workoutId)
            }
            null -> Unit
        }
    }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        viewModel.consumeMessage()
        snackbar.showSnackbar(text)
    }

    // Ticks once a second for the elapsed clock; recomposes only this value.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().imePadding()) {
            IronTopBar(
                title = "Workout",
                onBack = onClose,
                actions = {
                    TextButton(onClick = { confirmFinish = true }, enabled = !state.isFinishing && !state.isLoading) {
                        Text("Finish", color = Chalk, style = MaterialTheme.typography.labelLarge)
                    }
                }
            )

            if (restRunning) {
                RestBar(
                    remaining = restRemaining,
                    onAdd = viewModel::addRestTime,
                    onSkip = viewModel::skipRest
                )
            }

            if (state.isLoading) {
                LoadingBlock(Modifier.fillMaxSize(), label = "Loading your workout")
                return@Column
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.md)
            ) {
                item(key = "header") {
                    WorkoutHeader(
                        name = state.name,
                        elapsedSeconds = ((now - state.startedAt) / 1000).toInt(),
                        volume = state.unit.formatVolume(state.volumeKg),
                        completedSets = state.completedSets,
                        onRename = { renameOpen = true }
                    )
                }

                item(key = "muscles") {
                    MusclesTrainedCard(
                        blocks = state.exercises,
                        tint = state.muscleTint,
                        onTint = viewModel::setMuscleTint
                    )
                }

                if (!notifications.isGranted && !notificationCardDismissed) {
                    item(key = "notifications") {
                        NotificationPrompt(
                            onEnable = { notifications.runWithPermission { } },
                            onDismiss = { notificationCardDismissed = true }
                        )
                    }
                }

                if (state.exercises.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            icon = Icons.Default.Add,
                            title = "No exercises yet",
                            body = "Add the first movement and start logging sets.",
                            actionLabel = "Add exercises",
                            onAction = onAddExercises
                        )
                    }
                }

                items(state.exercises, key = { it.id }) { block ->
                    ExerciseCard(
                        block = block,
                        unit = state.unit,
                        repCounterAvailable = viewModel.repCounterAvailable,
                        onOpenExercise = { onOpenExercise(block.item.exercise.id) },
                        onEditNotes = { notesTarget = block },
                        onEditRest = { restTarget = block },
                        onPlates = { plateTarget = block },
                        onRemove = { removeTarget = block },
                        onCountReps = { set -> repTarget = block to set },
                        onAddSet = { viewModel.addSet(block.id) },
                        viewModel = viewModel
                    )
                }

                if (state.exercises.isNotEmpty()) {
                    item(key = "footer") {
                        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                            SecondaryButton(text = "Add exercises", onClick = onAddExercises, leadingIcon = Icons.Default.Add)
                            PrimaryButton(
                                text = "Finish workout",
                                onClick = { confirmFinish = true },
                                loading = state.isFinishing
                            )
                            TextButton(onClick = { confirmDiscard = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("Discard workout", color = Danger)
                            }
                            Spacer(Modifier.height(Space.xl))
                        }
                    }
                }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(Space.lg))
    }

    // ------------------------------------------------------------------ dialogs

    if (renameOpen) {
        TextInputDialog(
            title = "Rename workout",
            label = "Name",
            initial = state.name,
            singleLine = true,
            onConfirm = {
                viewModel.rename(it)
                renameOpen = false
            },
            onDismiss = { renameOpen = false }
        )
    }

    if (confirmFinish) {
        val unfinished = state.exercises.sumOf { b -> b.sets.count { !it.isCompleted } }
        ConfirmDialog(
            title = "Finish workout?",
            body = if (unfinished > 0) {
                "$unfinished unticked set(s) will be dropped. Records are checked and saved when you finish."
            } else {
                "Your sets will be saved and checked for personal records."
            },
            confirmLabel = "Finish",
            destructive = false,
            onConfirm = {
                confirmFinish = false
                viewModel.finish()
            },
            onDismiss = { confirmFinish = false }
        )
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard workout?",
            body = "Every set logged in this session will be deleted. This can't be undone.",
            confirmLabel = "Discard workout",
            onConfirm = {
                confirmDiscard = false
                viewModel.discard()
            },
            onDismiss = { confirmDiscard = false }
        )
    }

    removeTarget?.let { block ->
        ConfirmDialog(
            title = "Remove ${block.item.exercise.name}?",
            body = "Its ${block.sets.size} set(s) in this workout will be deleted.",
            confirmLabel = "Remove exercise",
            onConfirm = {
                viewModel.removeExercise(block.id)
                removeTarget = null
            },
            onDismiss = { removeTarget = null }
        )
    }

    notesTarget?.let { block ->
        TextInputDialog(
            title = "Note for ${block.item.exercise.name}",
            label = "Note",
            initial = block.item.workoutExercise.notes,
            singleLine = false,
            onConfirm = {
                viewModel.updateNotes(block.id, it)
                notesTarget = null
            },
            onDismiss = { notesTarget = null }
        )
    }

    restTarget?.let { block ->
        RestDialog(
            exerciseName = block.item.exercise.name,
            initialSeconds = block.item.workoutExercise.restSeconds,
            onConfirm = {
                viewModel.updateRest(block.id, it)
                restTarget = null
            },
            onDismiss = { restTarget = null }
        )
    }

    plateTarget?.let { block ->
        PlateCalculatorSheet(
            exerciseName = block.item.exercise.name,
            // The exercise's own bar wins where it has one -- a trap bar is not
            // the gym's default -- and otherwise the bar from Settings is used.
            barWeightKg = block.item.exercise.defaultBarWeightKg ?: state.equipment.barWeightKg,
            initialTargetKg = block.sets.firstOrNull { !it.isCompleted }?.weightKg
                ?: block.sets.lastOrNull { it.weightKg != null }?.weightKg
                ?: block.previous.firstOrNull()?.weightKg,
            unit = state.unit,
            plateSet = state.equipment.plateSet,
            onDismiss = { plateTarget = null }
        )
    }

    repTarget?.let { (block, set) ->
        RepCounterSheet(
            exerciseName = block.item.exercise.name,
            setNumber = set.setNumber,
            available = viewModel.repCounterAvailable,
            signals = viewModel::repSignals,
            onUse = { reps ->
                viewModel.applyCountedReps(set.id, reps)
                repTarget = null
            },
            onDismiss = { repTarget = null }
        )
    }
}

@Composable
private fun WorkoutHeader(
    name: String,
    elapsedSeconds: Int,
    volume: String,
    completedSets: Int,
    onRename: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "Rename workout", onClick = onRename)
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                name,
                style = MaterialTheme.typography.headlineMedium,
                color = Chalk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.Edit, contentDescription = null, tint = ChalkDim)
        }
        Spacer(Modifier.height(Space.md))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatTile(value = formatClock(elapsedSeconds.coerceAtLeast(0)), label = "Duration")
            StatTile(value = volume, label = "Volume")
            StatTile(value = completedSets.toString(), label = "Sets done")
        }
    }
}

@Composable
private fun RestBar(remaining: Int, onAdd: () -> Unit, onSkip: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = Space.xs)
            .background(Slate700, RoundedCornerShape(Radius.card))
            .padding(horizontal = Space.lg, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Timer, contentDescription = null, tint = ChalkDim)
        Spacer(Modifier.width(Space.sm))
        Column(Modifier.weight(1f)) {
            Text("Rest", style = MaterialTheme.typography.labelMedium, color = ChalkDim)
            Text(
                formatClock(remaining),
                style = NumericType.stat,
                color = Chalk,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        TextButton(onClick = onAdd) { Text("+30s", color = Chalk) }
        TextButton(onClick = onSkip) { Text("Skip", color = Chalk) }
    }
}

@Composable
private fun NotificationPrompt(onEnable: () -> Unit, onDismiss: () -> Unit) {
    IronCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Notifications, contentDescription = null, tint = ChalkDim)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text("Get rest alerts with the screen off", style = MaterialTheme.typography.bodyMedium, color = Chalk)
                TextButton(onClick = onEnable, contentPadding = PaddingValues(0.dp)) {
                    Text("Enable notifications", color = Chalk, style = MaterialTheme.typography.labelLarge)
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = ChalkDim)
            }
        }
    }
}

@Composable
private fun ExerciseCard(
    block: ExerciseBlock,
    unit: UnitSystem,
    repCounterAvailable: Boolean,
    onOpenExercise: () -> Unit,
    onEditNotes: () -> Unit,
    onEditRest: () -> Unit,
    onPlates: () -> Unit,
    onRemove: () -> Unit,
    onCountReps: (WorkoutSetEntity) -> Unit,
    onAddSet: () -> Unit,
    viewModel: ActiveWorkoutViewModel
) {
    val exercise = block.item.exercise
    val category = exercise.category
    val haptic = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }

    IronCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(onClickLabel = "Open ${exercise.name} history", onClick = onOpenExercise)
                ) {
                    Text(
                        exercise.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Chalk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Caption("Rest ${formatClock(block.item.workoutExercise.restSeconds)}")
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "${exercise.name} options", tint = ChalkDim)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = Slate800) {
                        DropdownMenuItem(
                            text = { Text("Edit note", color = Chalk) },
                            onClick = { menuOpen = false; onEditNotes() }
                        )
                        DropdownMenuItem(
                            text = { Text("Rest time", color = Chalk) },
                            onClick = { menuOpen = false; onEditRest() }
                        )
                        if (category == ExerciseCategory.BARBELL) {
                            DropdownMenuItem(
                                text = { Text("Plate calculator", color = Chalk) },
                                onClick = { menuOpen = false; onPlates() }
                            )
                        }
                        val nextSet = block.sets.firstOrNull { !it.isCompleted } ?: block.sets.lastOrNull()
                        if (category.tracksReps && nextSet != null) {
                            DropdownMenuItem(
                                text = { Text("Count reps (set ${nextSet.setNumber})", color = Chalk) },
                                onClick = { menuOpen = false; onCountReps(nextSet) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Remove exercise", color = Danger) },
                            onClick = { menuOpen = false; onRemove() }
                        )
                    }
                }
            }
            if (block.item.workoutExercise.notes.isNotBlank()) {
                Spacer(Modifier.height(Space.xs))
                Text(block.item.workoutExercise.notes, style = MaterialTheme.typography.bodySmall, color = ChalkDim)
            }
            Spacer(Modifier.height(Space.sm))
            SetHeaderRow(category, unit)
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                block.sets.forEachIndexed { index, set ->
                    androidx.compose.runtime.key(set.id) {
                        SetRow(
                            set = set,
                            previous = block.previous.getOrNull(index),
                            category = category,
                            unit = unit,
                            onCycleType = { viewModel.cycleType(set.id) },
                            onWeight = { viewModel.setWeight(set.id, it) },
                            onReps = { viewModel.setReps(set.id, it) },
                            onDuration = { viewModel.setDuration(set.id, it) },
                            onDistance = { viewModel.setDistance(set.id, it) },
                            onToggle = { w, r ->
                                // A tick you can feel, for hands that are busy with a bar.
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.toggleComplete(set.id, w, r)
                            },
                            onDelete = { viewModel.deleteSet(set.id) },
                            onCountReps = if (repCounterAvailable) ({ onCountReps(set) }) else null
                        )
                    }
                }
            }
            Spacer(Modifier.height(Space.sm))
            TextButton(onClick = onAddSet, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Chalk)
                Spacer(Modifier.width(Space.xs))
                Text("Add set", color = Chalk)
            }
            Caption("Tap a set number to change its type · hold it for more options")
        }
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    singleLine: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Slate800,
        title = { Text(title, color = Chalk) },
        text = { IronTextField(value = text, onValueChange = { text = it }, label = label, singleLine = singleLine) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Save", color = Chalk) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = ChalkDim) } }
    )
}

@Composable
private fun RestDialog(
    exerciseName: String,
    initialSeconds: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var seconds by remember { mutableIntStateOf(initialSeconds) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Slate800,
        title = { Text("Rest for $exerciseName", color = Chalk) },
        text = {
            Column {
                Stepper(
                    label = "Rest",
                    value = if (seconds == 0) "Off" else formatClock(seconds),
                    onDecrement = { seconds = (seconds - 15).coerceAtLeast(0) },
                    onIncrement = { seconds = (seconds + 15).coerceAtMost(900) }
                )
                Caption("The timer starts automatically when you tick a set, if auto rest is on in Settings.")
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(seconds) }) { Text("Save", color = Chalk) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = ChalkDim) } }
    )
}


/**
 * The figure that fills in as the session goes.
 *
 * A muscle counts as trained once one working set of an exercise that uses it
 * is complete: a warm-up on the bench does not light the chest, and neither
 * does a set that is still waiting for its tick. The colour is the user's, and
 * the swatches under the figure change it without leaving the workout.
 */
@Composable
private fun MusclesTrainedCard(
    blocks: List<ExerciseBlock>,
    tint: MuscleTint,
    onTint: (MuscleTint) -> Unit
) {
    val worked = blocks.filter { block ->
        block.sets.any { it.isCompleted && it.type.countsTowardVolume }
    }
    val primary = worked.map { it.item.exercise.primaryMuscle }.toSet()
    val secondary = worked
        .flatMap { block ->
            block.item.exercise.secondaryMuscles.split(",")
                .mapNotNull { name -> runCatching { MuscleGroup.valueOf(name.trim()) }.getOrNull() }
        }
        .toSet() - primary

    IronCard {
        Column {
            Text("Muscles trained", style = MaterialTheme.typography.titleMedium, color = Chalk)
            Caption(
                if (worked.isEmpty()) "Fills in as you complete sets."
                else "Solid for the muscle a lift trains hardest, lighter for the ones helping."
            )
            Spacer(Modifier.height(Space.md))
            TrainedMuscles(primary = primary, secondary = secondary, tint = Color(tint.argb))
            Spacer(Modifier.height(Space.md))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Colour", style = MaterialTheme.typography.labelMedium, color = ChalkDim)
                Spacer(Modifier.width(Space.xs))
                MuscleTint.entries.forEach { option ->
                    val chosen = option == tint
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(option.argb))
                            .border(
                                width = if (chosen) 3.dp else 1.dp,
                                color = if (chosen) Chalk else Slate600,
                                shape = CircleShape
                            )
                            .clickable { onTint(option) }
                            .semantics {
                                role = Role.RadioButton
                                selected = chosen
                                contentDescription = "${option.label} for trained muscles"
                            }
                    )
                }
            }
        }
    }
}
