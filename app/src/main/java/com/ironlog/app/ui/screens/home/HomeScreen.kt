package com.ironlog.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.core.permissions.AppPermission
import com.ironlog.app.core.permissions.rememberPermissionState
import com.ironlog.app.ui.components.Caption
import com.ironlog.app.ui.components.ErrorBanner
import com.ironlog.app.ui.components.IronCard
import com.ironlog.app.ui.components.PrBadge
import com.ironlog.app.ui.components.PrimaryButton
import com.ironlog.app.ui.components.SecondaryButton
import com.ironlog.app.ui.components.SectionHeader
import com.ironlog.app.ui.components.StatTile
import com.ironlog.app.ui.format.formatClock
import com.ironlog.app.ui.format.formatDuration
import com.ironlog.app.ui.format.formatRelativeDay
import com.ironlog.app.ui.format.formatVolume
import com.ironlog.app.ui.screens.exercises.formatRecordContext
import com.ironlog.app.ui.screens.exercises.formatRecordValue
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.ChalkFaint
import com.ironlog.app.ui.theme.NumericType
import com.ironlog.app.ui.theme.Plate10
import com.ironlog.app.ui.theme.PrGold
import com.ironlog.app.ui.theme.Slate700
import com.ironlog.app.ui.theme.Space
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    onOpenActiveWorkout: () -> Unit,
    onOpenRoutines: () -> Unit,
    onOpenCardio: () -> Unit,
    onOpenWorkout: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stepsPermission = rememberPermissionState(AppPermission.ACTIVITY_RECOGNITION)
    val stepsEnabled = state.stepSensorAvailable && stepsPermission.isGranted

    // The pedometer is only read while Home is on screen; the hardware keeps
    // counting regardless, so nothing is lost while the listener is off.
    LifecycleStartEffect(stepsEnabled) {
        if (stepsEnabled) viewModel.startSteps()
        onStopOrDispose { viewModel.stopSteps() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.lg)
    ) {
        Spacer(Modifier.height(Space.xl))
        Text(state.greeting, style = MaterialTheme.typography.bodyMedium, color = ChalkDim)
        Text(
            text = state.displayName.ifBlank { "Lifter" },
            style = MaterialTheme.typography.displayMedium,
            color = Chalk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(Space.lg))

        ErrorBanner(state.error)
        if (state.error != null) Spacer(Modifier.height(Space.md))

        val active = state.activeWorkout
        if (active != null) {
            ActiveWorkoutCard(active, onOpenActiveWorkout)
        } else {
            PrimaryButton(
                text = "Start training",
                onClick = onOpenRoutines,
                leadingIcon = Icons.Default.PlayArrow
            )
        }

        Spacer(Modifier.height(Space.xl))
        SectionHeader("This week")
        IronCard {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatTile(
                        value = "${state.workoutsThisWeek}/${state.weeklyGoal}",
                        label = "Workouts"
                    )
                    StatTile(value = state.unit.formatVolume(state.volumeThisWeekKg), label = "Volume")
                    StatTile(
                        value = state.streakWeeks.toString(),
                        label = "Week streak",
                        accent = if (state.streakWeeks > 0) PrGold else Chalk
                    )
                }
                Spacer(Modifier.height(Space.md))
                val progress = (state.workoutsThisWeek.toFloat() / state.weeklyGoal.coerceAtLeast(1)).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .semantics {
                            contentDescription = "${state.workoutsThisWeek} of ${state.weeklyGoal} workouts this week"
                        },
                    color = Plate10,
                    trackColor = Slate700,
                    drawStopIndicator = {}
                )
                Spacer(Modifier.height(Space.xs))
                Caption(
                    if (state.workoutsThisWeek >= state.weeklyGoal) "Weekly goal reached"
                    else "${state.weeklyGoal - state.workoutsThisWeek} to go for your weekly goal"
                )
            }
        }

        Spacer(Modifier.height(Space.xl))
        SectionHeader("Today")
        IronCard {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null, tint = ChalkDim, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(Space.sm))
                    when {
                        !state.stepSensorAvailable -> Text(
                            "This phone has no step counter",
                            style = MaterialTheme.typography.bodyLarge,
                            color = ChalkDim
                        )
                        stepsPermission.isGranted -> Text(
                            "%,d steps".format(state.todaySteps),
                            style = NumericType.stat,
                            color = Chalk
                        )
                        else -> Text("Steps are off", style = MaterialTheme.typography.bodyLarge, color = ChalkDim)
                    }
                }
                Spacer(Modifier.height(Space.xs))
                Caption("From your phone's built-in step counter")
                if (state.stepSensorAvailable && !stepsPermission.isGranted) {
                    Spacer(Modifier.height(Space.md))
                    SecondaryButton(
                        text = "Count my steps",
                        onClick = { stepsPermission.runWithPermission { viewModel.startSteps() } }
                    )
                }
            }
        }

        Spacer(Modifier.height(Space.md))
        IronCard(onClick = onOpenCardio) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = null, tint = Chalk)
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Text("Outdoor cardio", style = MaterialTheme.typography.titleMedium, color = Chalk)
                    Caption("Record a run, walk or ride with GPS")
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = ChalkFaint)
            }
        }

        state.lastWorkout?.let { last ->
            Spacer(Modifier.height(Space.xl))
            SectionHeader("Last workout")
            IronCard(onClick = { onOpenWorkout(last.id) }) {
                Column {
                    Text(last.name, style = MaterialTheme.typography.titleMedium, color = Chalk)
                    Caption(formatRelativeDay(last.startedAt))
                    Spacer(Modifier.height(Space.md))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatTile(formatDuration(last.durationSeconds), "Duration")
                        StatTile(state.unit.formatVolume(last.totalVolumeKg), "Volume")
                        StatTile(
                            last.prCount.toString(),
                            "PRs",
                            accent = if (last.prCount > 0) PrGold else Chalk
                        )
                    }
                }
            }
        }

        if (state.recentRecords.isNotEmpty()) {
            Spacer(Modifier.height(Space.xl))
            SectionHeader("Recent records")
            IronCard {
                Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                    state.recentRecords.forEach { item ->
                        val r = item.record
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
        }

        Spacer(Modifier.height(Space.section))
    }
}

@Composable
private fun ActiveWorkoutCard(active: ActiveWorkoutInfo, onResume: () -> Unit) {
    val elapsed by produceState(initialValue = 0, active.startedAt) {
        while (true) {
            value = ((System.currentTimeMillis() - active.startedAt) / 1000).toInt()
            delay(1000)
        }
    }
    IronCard(onClick = onResume) {
        Column {
            Caption("WORKOUT IN PROGRESS")
            Spacer(Modifier.height(Space.xs))
            Text(
                active.name,
                style = MaterialTheme.typography.titleLarge,
                color = Chalk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(Space.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatClock(elapsed), style = NumericType.stat, color = Chalk)
                Spacer(Modifier.width(Space.md))
                Caption("${active.completedSets} sets done")
            }
            Spacer(Modifier.height(Space.md))
            PrimaryButton(text = "Resume", onClick = onResume, leadingIcon = Icons.Default.PlayArrow)
        }
    }
}
