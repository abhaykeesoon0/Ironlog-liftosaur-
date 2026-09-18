package com.ironlog.app.ui.screens.exercises

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.data.entity.MuscleGroup
import com.ironlog.app.data.entity.UnitSystem
import com.ironlog.app.domain.usecase.TrainingMath
import com.ironlog.app.ui.components.Caption
import com.ironlog.app.ui.components.ChartPoint
import com.ironlog.app.ui.components.EmptyState
import com.ironlog.app.ui.components.ErrorBanner
import com.ironlog.app.ui.components.IronCard
import com.ironlog.app.ui.components.IronChip
import com.ironlog.app.ui.components.IronTopBar
import com.ironlog.app.ui.components.LineChart
import com.ironlog.app.ui.components.LoadingBlock
import com.ironlog.app.ui.components.PrBadge
import com.ironlog.app.ui.components.SectionHeader
import com.ironlog.app.ui.format.formatRelativeDay
import com.ironlog.app.ui.format.formatShortDate
import com.ironlog.app.ui.format.formatVolume
import com.ironlog.app.ui.format.formatWeight
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.NumericType
import com.ironlog.app.ui.theme.Plate15
import com.ironlog.app.ui.theme.Plate20
import com.ironlog.app.ui.theme.Plate10
import com.ironlog.app.ui.theme.Space

@Composable
fun ExerciseDetailScreen(
    onBack: () -> Unit,
    viewModel: ExerciseDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exercise = state.exercise

    Column(Modifier.fillMaxSize()) {
        IronTopBar(title = exercise?.name ?: "Exercise", onBack = onBack)

        when {
            state.isLoading -> LoadingBlock(Modifier.fillMaxSize())
            exercise == null -> {
                ErrorBanner(state.error, Modifier.padding(horizontal = Space.lg))
                EmptyState(
                    icon = Icons.Default.SearchOff,
                    title = "Exercise not found",
                    body = "It may have been removed from your library."
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.md)
            ) {
                item {
                    Column {
                        val secondary = exercise.secondaryMuscles.split(',')
                            .filter { it.isNotBlank() }
                            .mapNotNull { name -> MuscleGroup.entries.firstOrNull { it.name == name }?.label }
                        Text(
                            "${exercise.primaryMuscle.label} · ${exercise.category.label}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = ChalkDim
                        )
                        if (secondary.isNotEmpty()) Caption("Also works ${secondary.joinToString(", ")}")
                        if (exercise.instructions.isNotBlank()) {
                            Spacer(Modifier.height(Space.md))
                            Text(exercise.instructions, style = MaterialTheme.typography.bodyMedium, color = ChalkDim)
                        }
                    }
                }

                if (state.sessions.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Default.FitnessCenter,
                            title = "Not logged yet",
                            body = "Finish a workout with this exercise and your records and progress chart appear here."
                        )
                    }
                } else {
                    if (state.records.isNotEmpty()) {
                        item { SectionHeader("Personal records") }
                        item {
                            IronCard {
                                Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                                    state.records.forEach { r ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(r.recordType.label, style = MaterialTheme.typography.bodyLarge, color = Chalk)
                                                Caption(formatRelativeDay(r.achievedAt))
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    formatRecordValue(r.recordType, r.value, state.unit),
                                                    style = NumericType.stat,
                                                    color = Chalk
                                                )
                                                formatRecordContext(r.recordType, r.weightKg, r.reps, state.unit)
                                                    ?.let { Caption(it) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item { SectionHeader("Progress") }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            items(ExerciseMetric.entries) { metric ->
                                IronChip(metric.label, state.metric == metric) { viewModel.selectMetric(metric) }
                            }
                        }
                    }
                    item { ProgressChart(state) }

                    item { SectionHeader("Sessions") }
                    items(state.sessions.reversed(), key = { it.workoutId }) { session ->
                        IronCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(session.workoutName, style = MaterialTheme.typography.bodyLarge, color = Chalk)
                                    Caption("${formatRelativeDay(session.startedAt)} · ${session.workingSets} working sets")
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    val top = session.topSetWeightKg
                                    Text(
                                        when {
                                            top != null && session.topSetReps != null ->
                                                "${state.unit.formatWeight(top)} × ${session.topSetReps}"
                                            session.topSetReps != null -> "${session.topSetReps} reps"
                                            else -> "—"
                                        },
                                        style = NumericType.small.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize),
                                        color = Chalk
                                    )
                                    if (session.volumeKg > 0) Caption(state.unit.formatVolume(session.volumeKg))
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(Space.xl)) }
            }
        }
    }
}

@Composable
private fun ProgressChart(state: ExerciseDetailUiState) {
    val unit: UnitSystem = state.unit
    val (values, color) = when (state.metric) {
        ExerciseMetric.BEST_WEIGHT -> state.sessions.map { it.bestWeightKg } to Plate20
        ExerciseMetric.ESTIMATED_1RM -> state.sessions.map { it.estimated1RmKg } to Plate15
        ExerciseMetric.VOLUME -> state.sessions.map { it.volumeKg } to Plate10
    }
    // Sessions with no usable value (a bodyweight set has no load) are dropped
    // rather than plotted as zero, which would read as a collapse in strength.
    val points = state.sessions.zip(values)
        .filter { (_, v) -> v > 0 }
        .map { (session, v) -> ChartPoint(formatShortDate(session.startedAt), v.toFloat()) }

    IronCard {
        Column {
            if (points.isEmpty()) {
                Caption("No weighted sets to chart for this metric yet.")
            } else {
                // Points stay in kilograms; only the labels convert, and a linear
                // unit change does not alter the shape of the line.
                LineChart(
                    points = points,
                    color = color,
                    valueFormatter = {
                        if (state.metric == ExerciseMetric.VOLUME) unit.formatVolume(it.toDouble())
                        else unit.formatWeight(it.toDouble())
                    },
                    description = state.metric.label
                )
            }
            if (state.metric == ExerciseMetric.ESTIMATED_1RM) {
                Spacer(Modifier.height(Space.sm))
                Caption("Estimated from sets of ${TrainingMath.MAX_RELIABLE_REPS} reps or fewer; higher-rep sets are not reliable predictors.")
            }
        }
    }
}
