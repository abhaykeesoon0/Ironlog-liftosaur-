package com.ironlog.app.ui.screens.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.data.entity.GoalMetric
import com.ironlog.app.data.entity.UnitSystem
import com.ironlog.app.ui.components.Caption
import com.ironlog.app.ui.components.ChartPoint
import com.ironlog.app.ui.components.EmptyState
import com.ironlog.app.ui.components.ErrorBanner
import com.ironlog.app.ui.components.IronCard
import com.ironlog.app.ui.components.IronChip
import com.ironlog.app.ui.components.IronTextField
import com.ironlog.app.ui.components.IronTopBar
import com.ironlog.app.ui.components.LineChart
import com.ironlog.app.ui.components.LoadingBlock
import com.ironlog.app.ui.format.formatShortDate
import com.ironlog.app.ui.format.formatWeight
import com.ironlog.app.ui.format.trimmed
import com.ironlog.app.ui.format.weightValue
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.NumericType
import com.ironlog.app.ui.theme.Plate10
import com.ironlog.app.ui.theme.Plate20
import com.ironlog.app.ui.theme.PrGold
import com.ironlog.app.ui.theme.Slate700
import com.ironlog.app.ui.theme.Space

/**
 * Strength goals: a target on one lift, the share of it reached so far, and the
 * chart of how that lift has moved.
 *
 * The target is the only thing stored. Everything shown is derived from the
 * training log, so a goal cannot disagree with the sets underneath it.
 */
@Composable
fun StrengthGoalsScreen(
    onBack: () -> Unit,
    onPickExercise: () -> Unit,
    pickedExerciseIds: List<String>?,
    onPickedConsumed: () -> Unit,
    viewModel: StrengthGoalsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pendingExerciseId by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<GoalCard?>(null) }

    // The picker hands its choice back through the saved-state handle; taking it
    // here opens the target dialog for that exercise.
    LaunchedEffect(pickedExerciseIds) {
        pickedExerciseIds?.firstOrNull()?.let { id ->
            pendingExerciseId = id
            onPickedConsumed()
        }
    }

    Column(Modifier.fillMaxSize()) {
        IronTopBar(
            title = "Strength goals",
            onBack = onBack,
            actions = {
                IconButton(onClick = onPickExercise) {
                    Icon(Icons.Default.Add, contentDescription = "Add goal", tint = Chalk)
                }
            }
        )

        when {
            state.isLoading -> LoadingBlock(Modifier.fillMaxSize())
            state.goals.isEmpty() -> Column {
                ErrorBanner(state.error, Modifier.padding(horizontal = Space.lg))
                EmptyState(
                    icon = Icons.Default.Flag,
                    title = "No goals yet",
                    body = "Pick a lift and a number to chase. Progress is read from your logged sets, so it keeps itself up to date.",
                    actionLabel = "Add a goal",
                    onAction = onPickExercise
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.md)
            ) {
                item { ErrorBanner(state.error) }
                items(state.goals, key = { it.id }) { goal ->
                    GoalCardView(goal, state.unit) { confirmDelete = goal }
                }
                item { Spacer(Modifier.height(Space.xl)) }
            }
        }
    }

    pendingExerciseId?.let { exerciseId ->
        AddGoalDialog(
            unit = state.unit,
            isSaving = state.isSaving,
            onDismiss = { pendingExerciseId = null },
            onConfirm = { metric, target ->
                viewModel.addGoal(exerciseId, metric, target)
                pendingExerciseId = null
            }
        )
    }

    confirmDelete?.let { goal ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this goal?", color = Chalk) },
            text = {
                Text(
                    "The target goes; your logged sets stay exactly as they are.",
                    color = ChalkDim
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteGoal(goal.id)
                    confirmDelete = null
                }) { Text("Delete", color = Chalk) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel", color = ChalkDim) }
            },
            containerColor = Slate700
        )
    }
}

@Composable
private fun GoalCardView(goal: GoalCard, unit: UnitSystem, onDelete: () -> Unit) {
    IronCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        goal.exerciseName,
                        style = MaterialTheme.typography.titleMedium,
                        color = Chalk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Caption(goal.metric.label)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete goal", tint = ChalkDim)
                }
            }

            Spacer(Modifier.height(Space.md))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    "${unit.formatWeight(goal.currentKg)} / ${unit.formatWeight(goal.targetKg)}",
                    style = NumericType.stat,
                    color = if (goal.isAchieved) PrGold else Chalk
                )
                Text(
                    "${(goal.progress * 100).toInt()}%",
                    style = NumericType.stat,
                    color = if (goal.isAchieved) PrGold else ChalkDim
                )
            }

            Spacer(Modifier.height(Space.sm))
            val percent = (goal.progress * 100).toInt()
            LinearProgressIndicator(
                progress = { goal.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .semantics {
                        contentDescription = "$percent percent of the way to ${unit.formatWeight(goal.targetKg)}"
                    },
                color = if (goal.isAchieved) PrGold else Plate10,
                trackColor = Slate700,
                drawStopIndicator = {}
            )
            Spacer(Modifier.height(Space.xs))
            Caption(
                if (goal.isAchieved) "Reached. Raise the target when you are ready."
                else "${unit.weightValue(goal.remainingKg).let { it }} ${unit.weightSuffix} to go"
            )

            // Two points are the minimum that can show a direction; one session
            // is a dot, not a trend.
            if (goal.series.size >= 2) {
                Spacer(Modifier.height(Space.lg))
                LineChart(
                    points = goal.series.map { point ->
                        ChartPoint(
                            formatShortDate(point.startedAt),
                            unit.weightValue(point.valueKg).toFloatOrNull() ?: 0f
                        )
                    },
                    color = Plate20,
                    valueFormatter = { "${it.toDouble().trimmed(1)} ${unit.weightSuffix}" },
                    description = "${goal.exerciseName}, ${goal.metric.label.lowercase()} over time"
                )
            } else {
                Spacer(Modifier.height(Space.sm))
                Caption("Log this lift twice to see the chart.")
            }
        }
    }
}

@Composable
private fun AddGoalDialog(
    unit: UnitSystem,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (GoalMetric, String) -> Unit
) {
    var metric by remember { mutableStateOf(GoalMetric.ESTIMATED_1RM) }
    var target by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set the target", color = Chalk) },
        text = {
            Column {
                Text(
                    "Measure it on an estimate from any rep range, or on the heaviest weight you actually lift.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ChalkDim
                )
                Spacer(Modifier.height(Space.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    GoalMetric.entries.forEach { option ->
                        IronChip(
                            text = option.label,
                            selected = metric == option,
                            onClick = { metric = option }
                        )
                    }
                }
                Spacer(Modifier.height(Space.md))
                IronTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = "Target (${unit.weightSuffix})",
                    keyboardType = KeyboardType.Decimal
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(metric, target) },
                enabled = !isSaving && target.isNotBlank()
            ) { Text("Save", color = Chalk) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = ChalkDim) }
        },
        containerColor = Slate700
    )
}
