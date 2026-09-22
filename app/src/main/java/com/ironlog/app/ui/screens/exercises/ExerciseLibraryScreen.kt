package com.ironlog.app.ui.screens.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.data.entity.ExerciseCategory
import com.ironlog.app.data.entity.ExerciseEntity
import com.ironlog.app.data.entity.MuscleGroup
import com.ironlog.app.ui.components.Caption
import com.ironlog.app.ui.components.EmptyState
import com.ironlog.app.ui.components.FilterChipRow
import com.ironlog.app.ui.components.Hairline
import com.ironlog.app.ui.components.IronChip
import com.ironlog.app.ui.components.IronTextField
import com.ironlog.app.ui.components.IronTopBar
import com.ironlog.app.ui.components.LoadingBlock
import com.ironlog.app.ui.components.PrimaryButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.height
import com.ironlog.app.domain.usecase.MovementPattern
import com.ironlog.app.ui.components.MovementAnimation
import com.ironlog.app.ui.components.MuscleHighlight
import com.ironlog.app.ui.theme.Slate700
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.ChalkFaint
import com.ironlog.app.ui.theme.Slate800
import com.ironlog.app.ui.theme.Space
import com.ironlog.app.ui.theme.Touch

@Composable
fun ExerciseLibraryScreen(
    pickMode: Boolean,
    onBack: () -> Unit,
    onOpenOnline: () -> Unit,
    onPicked: (List<String>) -> Unit,
    onOpenExercise: (String) -> Unit,
    viewModel: ExerciseLibraryViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val muscle by viewModel.muscle.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val create by viewModel.create.collectAsStateWithLifecycle()

    /** The exercise whose movement is being shown, if any. */
    var demo by remember { mutableStateOf<ExerciseEntity?>(null) }

    Column(Modifier.fillMaxSize().imePadding()) {
        IronTopBar(
            title = if (pickMode) "Add exercises" else "Exercise library",
            onBack = onBack,
            actions = {
                IconButton(onClick = onOpenOnline) {
                    Icon(
                        Icons.Default.CloudDownload,
                        contentDescription = "Browse the online library",
                        tint = Chalk
                    )
                }
                IconButton(onClick = viewModel::openCreate) {
                    Icon(Icons.Default.Add, contentDescription = "Create exercise", tint = Chalk)
                }
            }
        )

        Column(Modifier.padding(horizontal = Space.lg)) {
            IronTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                label = "Search",
                placeholder = "Bench, squat, row…",
                trailingContent = { Icon(Icons.Default.Search, contentDescription = null, tint = ChalkFaint) }
            )
            Spacer(Modifier.size(Space.sm))
            FilterChipRow(
                options = MuscleGroup.entries,
                selected = muscle,
                label = { it.label },
                onSelect = viewModel::onMuscleSelect
            )
        }

        val list = exercises
        Column(Modifier.weight(1f)) {
            when {
                list == null -> LoadingBlock(Modifier.fillMaxSize())
                list.isEmpty() -> EmptyState(
                    icon = Icons.Default.Search,
                    title = "No matches",
                    body = "Nothing in the library matches that search. You can add it as your own exercise.",
                    actionLabel = "Create exercise",
                    onAction = viewModel::openCreate
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm)
                ) {
                    items(list, key = { it.id }) { exercise ->
                        ExerciseRow(
                            exercise = exercise,
                            pickMode = pickMode,
                            isSelected = exercise.id in selected,
                            onPlay = { demo = exercise },
                            onClick = {
                                if (pickMode) viewModel.toggle(exercise.id) else onOpenExercise(exercise.id)
                            }
                        )
                        Hairline()
                    }
                }
            }
        }

        if (pickMode) {
            Column(Modifier.fillMaxWidth().background(Slate800).padding(Space.lg)) {
                PrimaryButton(
                    text = when (selected.size) {
                        0 -> "Select exercises"
                        1 -> "Add 1 exercise"
                        else -> "Add ${selected.size} exercises"
                    },
                    onClick = { onPicked(selected) },
                    enabled = selected.isNotEmpty()
                )
            }
        }
    }

    create?.let { form ->
        CreateExerciseDialog(
            form = form,
            onNameChange = { name -> viewModel.updateCreate { it.copy(name = name) } },
            onMuscleChange = { m -> viewModel.updateCreate { it.copy(muscle = m) } },
            onCategoryChange = { c -> viewModel.updateCreate { it.copy(category = c) } },
            onConfirm = { viewModel.saveCreate(pickMode) },
            onDismiss = viewModel::dismissCreate
        )
    }

    demo?.let { exercise ->
        MovementDemoDialog(exercise = exercise, onDismiss = { demo = null })
    }
}

/**
 * The movement behind an exercise, as an animated figure.
 *
 * Shown from the library so a lifter can tell what "Jefferson Squat" is before
 * committing it to a workout, rather than after.
 */
@Composable
private fun MovementDemoDialog(exercise: ExerciseEntity, onDismiss: () -> Unit) {
    val pattern = remember(exercise.id) {
        MovementPattern.of(exercise.name, exercise.primaryMuscle, exercise.category)
    }
    val secondary = remember(exercise.id) {
        exercise.secondaryMuscles
            .split(",")
            .mapNotNull { name -> runCatching { MuscleGroup.valueOf(name.trim()) }.getOrNull() }
            .toSet()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(exercise.name, color = Chalk) },
        text = {
            // Scrolls, because the movement, the anatomy and the instructions
            // together are taller than a dialog on a small phone.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                MovementAnimation(
                    pattern = pattern,
                    primaryMuscle = exercise.primaryMuscle,
                    secondaryMuscles = secondary,
                    // Named on the anatomy chart below instead, so the two
                    // panels do not print the same list twice.
                    showKey = false
                )
                Spacer(Modifier.height(Space.lg))
                Hairline()
                Spacer(Modifier.height(Space.md))
                // What the lift is for, on a body rather than in a sentence.
                MuscleHighlight(primary = exercise.primaryMuscle, secondary = secondary)
                if (exercise.instructions.isNotBlank()) {
                    Spacer(Modifier.height(Space.md))
                    Text(
                        exercise.instructions,
                        style = MaterialTheme.typography.bodySmall,
                        color = ChalkDim
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = Chalk) }
        },
        containerColor = Slate700
    )
}

@Composable
private fun ExerciseRow(
    exercise: ExerciseEntity,
    pickMode: Boolean,
    isSelected: Boolean,
    onPlay: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Touch.setRow)
            .clickable(role = if (pickMode) Role.Checkbox else Role.Button, onClick = onClick)
            .padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                exercise.name,
                style = MaterialTheme.typography.titleMedium,
                color = Chalk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Caption(
                buildString {
                    append(exercise.primaryMuscle.label)
                    append(" · ")
                    append(exercise.category.label)
                    if (exercise.isCustom) append(" · Custom")
                }
            )
        }
        // Its own button rather than part of the row: in pick mode the row
        // selects the exercise, and watching the movement must not also add it
        // to the workout.
        IconButton(onClick = onPlay) {
            Icon(
                Icons.Default.PlayCircleOutline,
                contentDescription = "Show the ${exercise.name} movement",
                tint = ChalkDim
            )
        }
        if (pickMode) {
            Spacer(Modifier.width(Space.md))
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (isSelected) "Selected" else "Not selected",
                tint = if (isSelected) Chalk else ChalkFaint,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun CreateExerciseDialog(
    form: CreateExerciseState,
    onNameChange: (String) -> Unit,
    onMuscleChange: (MuscleGroup) -> Unit,
    onCategoryChange: (ExerciseCategory) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Slate800,
        title = { Text("Create exercise", color = Chalk) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                IronTextField(
                    value = form.name,
                    onValueChange = onNameChange,
                    label = "Name",
                    error = form.nameError,
                    enabled = !form.isSaving
                )
                Text("Main muscle", style = MaterialTheme.typography.labelMedium, color = ChalkDim)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    items(MuscleGroup.entries) { m ->
                        IronChip(m.label, form.muscle == m) { onMuscleChange(m) }
                    }
                }
                Text("Equipment", style = MaterialTheme.typography.labelMedium, color = ChalkDim)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    items(ExerciseCategory.entries) { c ->
                        IronChip(c.label, form.category == c) { onCategoryChange(c) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !form.isSaving && form.name.isNotBlank()) {
                Text("Create", color = Chalk)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = ChalkDim) }
        }
    )
}
