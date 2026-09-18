package com.ironlog.app.ui.screens.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.data.dao.RoutineWithExercises
import com.ironlog.app.ui.components.Caption
import com.ironlog.app.ui.components.ConfirmDialog
import com.ironlog.app.ui.components.EmptyState
import com.ironlog.app.ui.components.ErrorBanner
import com.ironlog.app.ui.components.IronCard
import com.ironlog.app.ui.components.LoadingBlock
import com.ironlog.app.ui.components.PrimaryButton
import com.ironlog.app.ui.components.ScreenTitle
import com.ironlog.app.ui.components.SecondaryButton
import com.ironlog.app.ui.components.SectionHeader
import com.ironlog.app.ui.format.formatRelativeDay
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.Space

@Composable
fun RoutinesScreen(
    onEditRoutine: (String?) -> Unit,
    onWorkoutStarted: () -> Unit,
    onOpenLibrary: () -> Unit,
    viewModel: RoutinesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val started by viewModel.started.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<RoutineWithExercises?>(null) }

    LaunchedEffect(started) {
        if (started) {
            viewModel.consumeStarted()
            onWorkoutStarted()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.md)
    ) {
        item(key = "title") { ScreenTitle("Routines", subtitle = "Plan it once, start it in one tap.") }

        item(key = "error") { ErrorBanner(error) }

        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                val active = state.activeWorkoutName
                if (active != null) {
                    // One session at a time: the primary action resumes rather than
                    // silently starting a second workout.
                    PrimaryButton(
                        text = "Resume \"$active\"",
                        onClick = onWorkoutStarted,
                        leadingIcon = Icons.Default.PlayArrow
                    )
                } else {
                    PrimaryButton(
                        text = "Start empty workout",
                        onClick = { viewModel.start(null) },
                        loading = busy,
                        leadingIcon = Icons.Default.PlayArrow
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    SecondaryButton(
                        text = "New routine",
                        onClick = { onEditRoutine(null) },
                        leadingIcon = Icons.Default.Add,
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryButton(
                        text = "Exercises",
                        onClick = onOpenLibrary,
                        leadingIcon = Icons.Default.Search,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        when {
            state.isLoading -> item(key = "loading") { LoadingBlock() }
            state.folders.isEmpty() -> item(key = "empty") {
                EmptyState(
                    icon = Icons.Default.FitnessCenter,
                    title = "No routines yet",
                    body = "A routine saves the exercises, sets and rest times you repeat, so a session starts ready to log.",
                    actionLabel = "Create a routine",
                    onAction = { onEditRoutine(null) }
                )
            }
            else -> state.folders.forEach { (folder, routines) ->
                item(key = "folder-$folder") { SectionHeader(folder) }
                items(routines, key = { it.routine.id }) { routine ->
                    RoutineCard(
                        routine = routine,
                        canStart = state.activeWorkoutName == null && !busy,
                        onStart = { viewModel.start(routine.routine.id) },
                        onEdit = { onEditRoutine(routine.routine.id) },
                        onDelete = { pendingDelete = routine }
                    )
                }
            }
        }

        item(key = "bottom") { Spacer(Modifier.height(Space.xl)) }
    }

    pendingDelete?.let { routine ->
        ConfirmDialog(
            title = "Delete routine?",
            body = "\"${routine.routine.name}\" will be removed. Workouts you already logged from it are kept.",
            confirmLabel = "Delete routine",
            onConfirm = {
                viewModel.delete(routine.routine.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun RoutineCard(
    routine: RoutineWithExercises,
    canStart: Boolean,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    IronCard(onClick = onEdit) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        routine.routine.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Chalk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Caption(
                        routine.routine.lastPerformedAt?.let { "Last done ${formatRelativeDay(it).lowercase()}" }
                            ?: "Not done yet"
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit ${routine.routine.name}", tint = ChalkDim)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete ${routine.routine.name}", tint = ChalkDim)
                }
            }
            Spacer(Modifier.height(Space.sm))
            Text(
                text = routine.exercises
                    .sortedBy { it.routineExercise.orderIndex }
                    .joinToString(" · ") { "${it.routineExercise.targetSets}× ${it.exercise.name}" }
                    .ifBlank { "No exercises" },
                style = MaterialTheme.typography.bodySmall,
                color = ChalkDim,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(Space.md))
            Row(Modifier.fillMaxWidth()) {
                PrimaryButton(
                    text = "Start",
                    onClick = onStart,
                    enabled = canStart,
                    leadingIcon = Icons.Default.PlayArrow
                )
            }
        }
    }
}
