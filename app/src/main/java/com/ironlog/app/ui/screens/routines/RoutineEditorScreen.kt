package com.ironlog.app.ui.screens.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.data.repository.RoutineExerciseDraft
import com.ironlog.app.ui.components.Caption
import com.ironlog.app.ui.components.ErrorBanner
import com.ironlog.app.ui.components.Hairline
import com.ironlog.app.ui.components.IronCard
import com.ironlog.app.ui.components.IronTextField
import com.ironlog.app.ui.components.IronTopBar
import com.ironlog.app.ui.components.LoadingBlock
import com.ironlog.app.ui.components.PrimaryButton
import com.ironlog.app.ui.components.SecondaryButton
import com.ironlog.app.ui.components.SectionHeader
import com.ironlog.app.ui.format.formatClock
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.NumericType
import com.ironlog.app.ui.theme.Space

@Composable
fun RoutineEditorScreen(
    onBack: () -> Unit,
    onAddExercises: () -> Unit,
    pickedExerciseIds: List<String>?,
    onPickedConsumed: () -> Unit,
    viewModel: RoutineEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(pickedExerciseIds) {
        val ids = pickedExerciseIds ?: return@LaunchedEffect
        if (ids.isNotEmpty()) viewModel.addExercises(ids)
        onPickedConsumed()
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onBack()
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        IronTopBar(
            title = if (state.isNew) "New routine" else "Edit routine",
            onBack = onBack,
            actions = {
                TextButton(onClick = viewModel::save, enabled = !state.isSaving && !state.isLoading) {
                    Text("Save", color = Chalk)
                }
            }
        )

        if (state.isLoading) {
            LoadingBlock(Modifier.fillMaxSize())
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            item(key = "error") { ErrorBanner(state.error) }
            item(key = "name") {
                IronTextField(
                    value = state.draft.name,
                    onValueChange = viewModel::onNameChange,
                    label = "Routine name",
                    placeholder = "e.g. Push day",
                    error = state.nameError
                )
            }
            item(key = "folder") {
                IronTextField(
                    value = state.draft.folder,
                    onValueChange = viewModel::onFolderChange,
                    label = "Folder",
                    placeholder = "My routines"
                )
            }
            item(key = "notes") {
                IronTextField(
                    value = state.draft.notes,
                    onValueChange = viewModel::onNotesChange,
                    label = "Notes (optional)",
                    singleLine = false
                )
            }
            item(key = "header") {
                SectionHeader("Exercises")
                if (state.draft.exercises.isEmpty()) {
                    Caption("Add the movements this routine is built from.")
                }
            }
            itemsIndexed(
                state.draft.exercises,
                key = { index, item -> "${item.exerciseId}-$index" }
            ) { index, item ->
                ExerciseDraftCard(
                    item = item,
                    isFirst = index == 0,
                    isLast = index == state.draft.exercises.lastIndex,
                    onChange = { transform -> viewModel.updateExercise(index, transform) },
                    onMoveUp = { viewModel.move(index, -1) },
                    onMoveDown = { viewModel.move(index, 1) },
                    onRemove = { viewModel.remove(index) }
                )
            }
            item(key = "add") {
                SecondaryButton(text = "Add exercises", onClick = onAddExercises, leadingIcon = Icons.Default.Add)
            }
            item(key = "save") {
                Spacer(Modifier.height(Space.sm))
                PrimaryButton(text = "Save routine", onClick = viewModel::save, loading = state.isSaving)
                Spacer(Modifier.height(Space.xl))
            }
        }
    }
}

@Composable
private fun ExerciseDraftCard(
    item: RoutineExerciseDraft,
    isFirst: Boolean,
    isLast: Boolean,
    onChange: ((RoutineExerciseDraft) -> RoutineExerciseDraft) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    IronCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.exerciseName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Chalk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onMoveUp, enabled = !isFirst) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move ${item.exerciseName} up", tint = ChalkDim)
                }
                IconButton(onClick = onMoveDown, enabled = !isLast) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move ${item.exerciseName} down", tint = ChalkDim)
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Close, contentDescription = "Remove ${item.exerciseName}", tint = ChalkDim)
                }
            }
            Hairline(Modifier.padding(vertical = Space.sm))
            Stepper(
                label = "Sets",
                value = item.targetSets.toString(),
                onDecrement = { onChange { it.copy(targetSets = (it.targetSets - 1).coerceAtLeast(1)) } },
                onIncrement = { onChange { it.copy(targetSets = (it.targetSets + 1).coerceAtMost(20)) } }
            )
            Stepper(
                label = "Reps from",
                value = item.targetRepsLow.toString(),
                onDecrement = { onChange { it.copy(targetRepsLow = (it.targetRepsLow - 1).coerceAtLeast(1)) } },
                onIncrement = {
                    onChange {
                        val low = (it.targetRepsLow + 1).coerceAtMost(100)
                        it.copy(targetRepsLow = low, targetRepsHigh = maxOf(it.targetRepsHigh, low))
                    }
                }
            )
            Stepper(
                label = "Reps to",
                value = item.targetRepsHigh.toString(),
                onDecrement = {
                    onChange {
                        val high = (it.targetRepsHigh - 1).coerceAtLeast(1)
                        it.copy(targetRepsHigh = high, targetRepsLow = minOf(it.targetRepsLow, high))
                    }
                },
                onIncrement = { onChange { it.copy(targetRepsHigh = (it.targetRepsHigh + 1).coerceAtMost(100)) } }
            )
            Stepper(
                label = "Rest",
                value = formatClock(item.restSeconds),
                onDecrement = { onChange { it.copy(restSeconds = (it.restSeconds - 15).coerceAtLeast(0)) } },
                onIncrement = { onChange { it.copy(restSeconds = (it.restSeconds + 15).coerceAtMost(900)) } }
            )
        }
    }
}

@Composable
internal fun Stepper(
    label: String,
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = ChalkDim, modifier = Modifier.weight(1f))
        IconButton(onClick = onDecrement) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease $label", tint = Chalk)
        }
        Text(
            value,
            style = NumericType.setCell,
            color = Chalk,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 56.dp).semantics { contentDescription = "$label $value" }
        )
        IconButton(onClick = onIncrement) {
            Icon(Icons.Default.Add, contentDescription = "Increase $label", tint = Chalk)
        }
    }
}

