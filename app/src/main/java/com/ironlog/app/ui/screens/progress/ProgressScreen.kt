package com.ironlog.app.ui.screens.progress

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.ui.components.BarChart
import com.ironlog.app.ui.components.Caption
import com.ironlog.app.ui.components.ChartPoint
import com.ironlog.app.ui.components.EmptyState
import com.ironlog.app.ui.components.ErrorBanner
import com.ironlog.app.ui.components.IronCard
import com.ironlog.app.ui.components.IronChip
import com.ironlog.app.ui.components.LoadingBlock
import com.ironlog.app.ui.components.PrBadge
import com.ironlog.app.ui.components.ProportionBars
import com.ironlog.app.ui.components.ScreenTitle
import com.ironlog.app.ui.components.SectionHeader
import com.ironlog.app.ui.components.StatTile
import com.ironlog.app.ui.format.formatRelativeDay
import com.ironlog.app.ui.format.formatVolume
import com.ironlog.app.ui.screens.exercises.formatRecordContext
import com.ironlog.app.ui.screens.exercises.formatRecordValue
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.ChalkFaint
import com.ironlog.app.ui.theme.ChartSeries
import com.ironlog.app.ui.theme.NumericType
import com.ironlog.app.ui.theme.Plate10
import com.ironlog.app.ui.theme.Plate15
import com.ironlog.app.ui.theme.Plate20
import com.ironlog.app.ui.theme.Space
import com.ironlog.app.ui.theme.Touch

@Composable
fun ProgressScreen(
    onOpenExercise: (String) -> Unit,
    onOpenBody: () -> Unit,
    onOpenCardio: () -> Unit,
    viewModel: ProgressViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.md)
    ) {
        item { ScreenTitle("Progress") }
        item { ErrorBanner(state.error) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                items(ProgressRange.entries) { range ->
                    IronChip(range.label, state.range == range) { viewModel.selectRange(range) }
                }
            }
        }

        if (state.isLoading) {
            item { LoadingBlock() }
        } else {
            item {
                IronCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatTile(state.totalWorkouts.toString(), "Workouts")
                        StatTile(state.unit.formatVolume(state.totalVolumeKg), "Volume")
                        StatTile(
                            "%.1f".format(state.totalWorkouts.toFloat() / state.range.weeks),
                            "Per week"
                        )
                    }
                }
            }

            if (!state.hasTraining) {
                item {
                    EmptyState(
                        icon = Icons.Default.BarChart,
                        title = "No training in this range",
                        body = "Finish a workout and your weekly volume, muscle split and records show up here."
                    )
                }
            } else {
                item { SectionHeader("Weekly volume") }
                item {
                    IronCard {
                        BarChart(
                            points = state.weeks.map { ChartPoint(it.label, it.volumeKg.toFloat()) },
                            color = Plate20,
                            valueFormatter = { state.unit.formatVolume(it.toDouble()) },
                            description = "Training volume per week"
                        )
                    }
                }

                item { SectionHeader("Workouts per week") }
                item {
                    IronCard {
                        BarChart(
                            points = state.weeks.map { ChartPoint(it.label, it.workouts.toFloat()) },
                            color = Plate15,
                            height = 120.dp,
                            description = "Workouts per week"
                        )
                    }
                }

                if (state.muscles.isNotEmpty()) {
                    item { SectionHeader("Muscle split") }
                    item {
                        IronCard {
                            Column {
                                Caption("Working volume by primary muscle. Warm-ups are excluded.")
                                Spacer(Modifier.height(Space.md))
                                ProportionBars(
                                    items = state.muscles.map { ChartPoint(it.label, it.volumeKg.toFloat()) },
                                    colors = ChartSeries,
                                    valueFormatter = { state.unit.formatVolume(it.toDouble()) }
                                )
                            }
                        }
                    }
                }
            }

            item { SectionHeader("Steps, last 7 days") }
            item {
                IronCard {
                    if (state.steps.all { it.steps == 0 }) {
                        Caption("No steps recorded yet. Turn on step counting from the Home screen.")
                    } else {
                        BarChart(
                            points = state.steps.map { ChartPoint(it.label, it.steps.toFloat()) },
                            color = Plate10,
                            height = 120.dp,
                            valueFormatter = { "%,d".format(it.toInt()) },
                            description = "Daily steps"
                        )
                    }
                }
            }

            if (state.records.isNotEmpty()) {
                item { SectionHeader("Recent records") }
                items(state.records, key = { it.record.id }) { item ->
                    val r = item.record
                    IronCard(onClick = { onOpenExercise(r.exerciseId) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.exerciseName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Chalk,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    PrBadge(r.recordType)
                                    Spacer(Modifier.width(Space.sm))
                                    Caption(formatRelativeDay(r.achievedAt))
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    formatRecordValue(r.recordType, r.value, state.unit),
                                    style = NumericType.small.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize),
                                    color = Chalk
                                )
                                formatRecordContext(r.recordType, r.weightKg, r.reps, state.unit)?.let { Caption(it) }
                            }
                        }
                    }
                }
            }

            if (state.mostUsed.isNotEmpty()) {
                item { SectionHeader("Most trained") }
                item {
                    IronCard {
                        Column {
                            state.mostUsed.forEach { ex ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = Touch.min)
                                        .clickable(onClickLabel = "Open ${ex.name}") { onOpenExercise(ex.exerciseId) },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        ex.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Chalk,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        if (ex.sessions == 1) "1 session" else "${ex.sessions} sessions",
                                        style = NumericType.small,
                                        color = ChalkDim
                                    )
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = ChalkFaint
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { SectionHeader("More") }
            item {
                EntryCard(
                    icon = Icons.Default.MonitorWeight,
                    title = "Body measurements",
                    subtitle = "Bodyweight, body fat and tape measurements",
                    onClick = onOpenBody
                )
            }
            item {
                EntryCard(
                    icon = Icons.AutoMirrored.Filled.DirectionsRun,
                    title = "Cardio",
                    subtitle = "GPS runs, walks and rides",
                    onClick = onOpenCardio
                )
            }
        }
        item { Spacer(Modifier.height(Space.xl)) }
    }
}

@Composable
private fun EntryCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    IronCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Chalk)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f).padding(end = Space.sm)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Chalk)
                Caption(subtitle)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = ChalkFaint)
        }
    }
}
