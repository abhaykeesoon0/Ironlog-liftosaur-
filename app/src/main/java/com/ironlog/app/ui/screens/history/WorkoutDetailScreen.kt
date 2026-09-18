package com.ironlog.app.ui.screens.history

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.data.dao.WorkoutExerciseWithDetail
import com.ironlog.app.data.entity.SetType
import com.ironlog.app.data.entity.UnitSystem
import com.ironlog.app.data.entity.WorkoutSetEntity
import com.ironlog.app.ui.components.Caption
import com.ironlog.app.ui.components.ConfirmDialog
import com.ironlog.app.ui.components.EmptyState
import com.ironlog.app.ui.components.ErrorBanner
import com.ironlog.app.ui.components.Hairline
import com.ironlog.app.ui.components.IronCard
import com.ironlog.app.ui.components.IronTopBar
import com.ironlog.app.ui.components.LoadingBlock
import com.ironlog.app.ui.components.PrBadge
import com.ironlog.app.ui.components.StatTile
import com.ironlog.app.ui.format.formatClock
import com.ironlog.app.ui.format.formatDistance
import com.ironlog.app.ui.format.formatDuration
import com.ironlog.app.ui.format.formatFullDate
import com.ironlog.app.ui.format.formatTime
import com.ironlog.app.ui.format.formatVolume
import com.ironlog.app.ui.format.formatWeight
import com.ironlog.app.ui.format.trimmed
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.ChalkFaint
import com.ironlog.app.ui.theme.NumericType
import com.ironlog.app.ui.theme.PrGold
import com.ironlog.app.ui.theme.Space

@Composable
fun WorkoutDetailScreen(
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
    viewModel: WorkoutDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) onBack()
    }

    Column(Modifier.fillMaxSize()) {
        IronTopBar(
            title = if (state.justFinished) "Summary" else "Workout",
            onBack = onBack,
            actions = {
                if (state.detail != null) {
                    IconButton(onClick = {
                        viewModel.shareText()?.let { text ->
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(send, "Share workout"))
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share workout", tint = Chalk)
                    }
                    IconButton(onClick = { confirmDelete = true }, enabled = !state.isDeleting) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete workout", tint = Chalk)
                    }
                }
            }
        )

        val detail = state.detail
        when {
            state.isLoading -> LoadingBlock(Modifier.fillMaxSize())
            detail == null -> {
                if (!state.isDeleted) {
                    ErrorBanner(state.error, Modifier.padding(horizontal = Space.lg))
                    EmptyState(
                        icon = Icons.Default.SearchOff,
                        title = "Workout not found",
                        body = "It may have been deleted."
                    )
                }
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.md)
            ) {
                item { ErrorBanner(state.error) }
                item {
                    val w = detail.workout
                    Column {
                        if (state.justFinished) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = if (w.prCount > 0) PrGold else ChalkDim
                                )
                                Spacer(Modifier.width(Space.sm))
                                Text("Workout complete", style = MaterialTheme.typography.headlineMedium, color = Chalk)
                            }
                            if (w.prCount > 0) {
                                Spacer(Modifier.height(Space.xs))
                                Text(
                                    if (w.prCount == 1) "1 new personal record" else "${w.prCount} new personal records",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PrGold
                                )
                            }
                            Spacer(Modifier.height(Space.md))
                        }
                        Text(w.name, style = MaterialTheme.typography.headlineLarge, color = Chalk)
                        Caption("${formatFullDate(w.startedAt)} · ${formatTime(w.startedAt)}")
                    }
                }
                item {
                    val w = detail.workout
                    IronCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatTile(formatDuration(w.durationSeconds), "Duration")
                            StatTile(state.unit.formatVolume(w.totalVolumeKg), "Volume")
                            StatTile(w.totalSets.toString(), "Sets")
                            StatTile(state.totalReps.toString(), "Reps")
                        }
                    }
                }
                if (detail.workout.notes.isNotBlank()) {
                    item {
                        IronCard {
                            Column {
                                Caption("NOTES")
                                Spacer(Modifier.height(Space.xs))
                                Text(detail.workout.notes, style = MaterialTheme.typography.bodyMedium, color = ChalkDim)
                            }
                        }
                    }
                }
                items(
                    detail.exercises.sortedBy { it.workoutExercise.orderIndex },
                    key = { it.workoutExercise.id }
                ) { item ->
                    ExerciseBlock(item, state.unit, onOpenExercise)
                }
                item { Spacer(Modifier.height(Space.xl)) }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this workout?",
            body = "Its sets are removed from your history and any records it set are recalculated. This can't be undone.",
            confirmLabel = "Delete workout",
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
private fun ExerciseBlock(
    item: WorkoutExerciseWithDetail,
    unit: UnitSystem,
    onOpenExercise: (String) -> Unit
) {
    IronCard {
        Column {
            Text(
                item.exercise.name,
                style = MaterialTheme.typography.titleMedium,
                color = Chalk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 32.dp)
                    .clickable(onClickLabel = "Open exercise progress") { onOpenExercise(item.exercise.id) }
            )
            if (item.workoutExercise.notes.isNotBlank()) {
                Caption(item.workoutExercise.notes)
            }
            Spacer(Modifier.height(Space.sm))
            Hairline()
            item.sets.sortedBy { it.setNumber }.forEach { set ->
                SetLine(set, unit)
            }
        }
    }
}

@Composable
private fun SetLine(set: WorkoutSetEntity, unit: UnitSystem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (set.type == SetType.NORMAL) set.setNumber.toString() else set.type.shortLabel,
            style = NumericType.small,
            color = if (set.type == SetType.NORMAL) ChalkDim else ChalkFaint,
            modifier = Modifier.width(28.dp)
        )
        Text(
            text = describeSet(set, unit),
            style = NumericType.small.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize),
            color = Chalk,
            modifier = Modifier.weight(1f)
        )
        set.rpe?.let {
            Text("RPE ${it.trimmed(1)}", style = NumericType.small, color = ChalkFaint)
            Spacer(Modifier.width(Space.sm))
        }
        PrBadge(set.prType)
    }
}

internal fun describeSet(set: WorkoutSetEntity, unit: UnitSystem): String {
    val parts = mutableListOf<String>()
    set.weightKg?.let { if (it > 0) parts += unit.formatWeight(it) }
    set.reps?.let { parts += "$it reps" }
    set.distanceMeters?.let { parts += unit.formatDistance(it) }
    set.durationSeconds?.let { parts += formatClock(it) }
    return parts.joinToString(" × ").ifBlank { "—" }
}
