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
import com.ironlog.app.ui.components.Comparison
import com.ironlog.app.ui.components.ComparisonBars
import com.ironlog.app.ui.components.DonutChart
import com.ironlog.app.ui.components.FlowStep
import com.ironlog.app.ui.components.Histogram
import com.ironlog.app.ui.components.LineChart
import com.ironlog.app.ui.components.ProgressionFlow
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
import com.ironlog.app.data.entity.UnitSystem
import com.ironlog.app.ui.format.formatRelativeDay
import com.ironlog.app.ui.format.formatShortDate
import com.ironlog.app.ui.format.formatWeight
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
import com.ironlog.app.ui.theme.Plate25
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

                state.comparison?.let { comparison ->
                    item { SectionHeader("Against the ${state.range.label} before") }
                    item {
                        IronCard {
                            Column {
                                if (comparison.hasBefore) {
                                    Caption(
                                        "The same measures over the previous ${state.range.label}, so a " +
                                            "heavier block shows as a longer bar rather than a bigger number."
                                    )
                                    Spacer(Modifier.height(Space.md))
                                    ComparisonBars(rows = comparisonRows(comparison, state.unit))
                                } else {
                                    Caption(
                                        "Nothing logged before this period yet, so there is nothing to " +
                                            "compare against. Keep training and this fills in."
                                    )
                                }
                            }
                        }
                    }
                }

                if (state.muscles.isNotEmpty()) {
                    item { SectionHeader("Muscle split") }
                    item {
                        IronCard {
                            Column {
                                Caption("Working volume by primary muscle. Warm-ups are excluded.")
                                Spacer(Modifier.height(Space.md))
                                // The ring answers "what is this training mostly
                                // for"; the bars underneath answer "by how much",
                                // which slices a few degrees apart cannot.
                                DonutChart(
                                    items = state.muscles.take(6)
                                        .map { ChartPoint(it.label, it.volumeKg.toFloat()) },
                                    centreValue = state.unit.formatVolume(state.totalVolumeKg),
                                    centreLabel = "total",
                                    description = "Share of volume by muscle"
                                )
                                Spacer(Modifier.height(Space.lg))
                                ProportionBars(
                                    items = state.muscles.map { ChartPoint(it.label, it.volumeKg.toFloat()) },
                                    colors = ChartSeries,
                                    valueFormatter = { state.unit.formatVolume(it.toDouble()) }
                                )
                            }
                        }
                    }
                }

                if (state.repBands.isNotEmpty()) {
                    item { SectionHeader("Rep ranges") }
                    item {
                        IronCard {
                            Column {
                                Caption("Every working set, counted by how many reps it was taken for.")
                                Spacer(Modifier.height(Space.md))
                                Histogram(
                                    bins = state.repBands.map { ChartPoint(it.label, it.sets.toFloat()) },
                                    valueFormatter = { "${it.toInt()} sets" },
                                    description = "Sets by rep range"
                                )
                                state.repBands.maxByOrNull { it.sets }?.let { band ->
                                    Spacer(Modifier.height(Space.sm))
                                    Caption(
                                        "Most of your work sits at ${band.label} reps, which is " +
                                            "${band.note.lowercase()} territory."
                                    )
                                }
                            }
                        }
                    }
                }

                state.topLift?.takeIf { it.sessions.size > 1 }?.let { lift ->
                    item { SectionHeader("How ${lift.name} has moved") }
                    item {
                        IronCard(onClick = { onOpenExercise(lift.exerciseId) }) {
                            Column {
                                Caption("Heaviest set in each session, oldest first.")
                                Spacer(Modifier.height(Space.md))
                                LineChart(
                                    points = lift.sessions.takeLast(12).map { session ->
                                        ChartPoint(
                                            formatShortDate(session.startedAt),
                                            session.bestWeight.toFloat()
                                        )
                                    },
                                    color = Plate25,
                                    valueFormatter = { state.unit.formatWeight(it.toDouble()) },
                                    description = "Heaviest set per session for ${lift.name}"
                                )
                                Spacer(Modifier.height(Space.lg))
                                Text(
                                    "Session by session",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ChalkDim
                                )
                                Spacer(Modifier.height(Space.sm))
                                ProgressionFlow(
                                    steps = flowSteps(lift, state.unit),
                                    description = "${lift.name} session by session"
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

/**
 * The measures worth setting against an earlier block of training.
 *
 * Six of them rather than one: volume alone can rise because the sessions got
 * longer, and load alone can rise because the sets got shorter. Together they
 * say which it was.
 */
private fun comparisonRows(comparison: PeriodComparison, unit: UnitSystem): List<Comparison> {
    val now = comparison.now
    val before = comparison.before
    return listOf(
        Comparison(
            "Volume", now.volumeKg.toFloat(), before.volumeKg.toFloat(),
            unit.formatVolume(now.volumeKg), unit.formatVolume(before.volumeKg)
        ),
        Comparison(
            "Working sets", now.sets.toFloat(), before.sets.toFloat(),
            "%,d".format(now.sets), "%,d".format(before.sets)
        ),
        Comparison(
            "Reps", now.reps.toFloat(), before.reps.toFloat(),
            "%,d".format(now.reps), "%,d".format(before.reps)
        ),
        Comparison(
            "Average load", now.averageWeightKg.toFloat(), before.averageWeightKg.toFloat(),
            unit.formatWeight(now.averageWeightKg), unit.formatWeight(before.averageWeightKg)
        ),
        Comparison(
            "Heaviest set", now.bestWeightKg.toFloat(), before.bestWeightKg.toFloat(),
            unit.formatWeight(now.bestWeightKg), unit.formatWeight(before.bestWeightKg)
        ),
        Comparison(
            "Workouts", now.workouts.toFloat(), before.workouts.toFloat(),
            "%,d".format(now.workouts), "%,d".format(before.workouts)
        )
    )
}

/**
 * The last few sessions of one lift as a chain, newest last.
 *
 * Six rungs: enough to see a direction, few enough that the card does not turn
 * into the workout history it links to.
 */
private fun flowSteps(lift: TopLift, unit: UnitSystem): List<FlowStep> {
    val best = lift.sessions.maxOfOrNull { it.bestWeight } ?: 0.0
    return lift.sessions.takeLast(6).map { session ->
        FlowStep(
            load = unit.formatWeight(session.bestWeight),
            detail = "for ${session.bestReps} reps",
            date = formatShortDate(session.startedAt),
            isBest = best > 0.0 && session.bestWeight >= best
        )
    }
}
