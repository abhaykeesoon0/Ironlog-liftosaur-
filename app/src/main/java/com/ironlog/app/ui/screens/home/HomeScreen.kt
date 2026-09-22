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
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.platform.LocalContext
import com.ironlog.app.core.sensors.HealthConnectSteps
import com.ironlog.app.data.entity.StepSource
import com.ironlog.app.core.permissions.AppPermission
import com.ironlog.app.core.permissions.rememberPermissionState
import com.ironlog.app.data.entity.PlateSet
import com.ironlog.app.data.entity.UnitSystem
import com.ironlog.app.data.repository.EquipmentSettings
import com.ironlog.app.domain.usecase.Recovery
import com.ironlog.app.domain.usecase.TrainingMath
import com.ironlog.app.ui.components.BodyMap
import com.ironlog.app.ui.components.Caption
import com.ironlog.app.ui.components.ChartPoint
import com.ironlog.app.ui.components.ErrorBanner
import com.ironlog.app.ui.components.IronCard
import com.ironlog.app.ui.components.IronTextField
import com.ironlog.app.ui.components.LineChart
import com.ironlog.app.ui.components.PrBadge
import com.ironlog.app.ui.components.PrimaryButton
import com.ironlog.app.ui.components.SecondaryButton
import com.ironlog.app.ui.components.SectionHeader
import com.ironlog.app.ui.components.StatTile
import com.ironlog.app.ui.format.formatClock
import com.ironlog.app.ui.format.formatDuration
import com.ironlog.app.ui.format.formatRelativeDay
import com.ironlog.app.ui.format.formatShortDate
import com.ironlog.app.ui.format.formatWeight
import com.ironlog.app.ui.format.formatVolume
import com.ironlog.app.ui.format.trimmed
import com.ironlog.app.ui.format.weightValue
import com.ironlog.app.ui.screens.goals.GoalCard
import com.ironlog.app.ui.screens.goals.HomeGoalsViewModel
import com.ironlog.app.ui.screens.rewards.CoinPill
import com.ironlog.app.ui.screens.rewards.EquippedViewModel
import com.ironlog.app.ui.screens.rewards.QuestsViewModel
import com.ironlog.app.ui.screens.rewards.badgeIcon
import com.ironlog.app.ui.screens.exercises.formatRecordContext
import com.ironlog.app.ui.screens.exercises.formatRecordValue
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.ChalkFaint
import com.ironlog.app.ui.theme.NumericType
import com.ironlog.app.ui.theme.Plate10
import com.ironlog.app.ui.theme.Plate20
import com.ironlog.app.ui.theme.PrGold
import com.ironlog.app.ui.theme.Slate700
import com.ironlog.app.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow

@Composable
fun HomeScreen(
    onOpenActiveWorkout: () -> Unit,
    onOpenRoutines: () -> Unit,
    onOpenCardio: () -> Unit,
    onOpenWorkout: (String) -> Unit,
    onOpenBody: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenQuests: () -> Unit,
    onOpenCoach: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    goalsViewModel: HomeGoalsViewModel = hiltViewModel(),
    questsViewModel: QuestsViewModel = hiltViewModel(),
    equippedViewModel: EquippedViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val goals by goalsViewModel.goals.collectAsStateWithLifecycle()
    val quests by questsViewModel.state.collectAsStateWithLifecycle()
    val equipped by equippedViewModel.equipped.collectAsStateWithLifecycle()
    val stepsPermission = rememberPermissionState(AppPermission.ACTIVITY_RECOGNITION)

    // Health Connect runs its own permission screen rather than the system
    // dialog, so it is launched through its contract instead of the app's
    // permission helper.
    val healthConnect = remember(context) { HealthConnectSteps(context) }
    val healthConnectRequest = rememberLauncherForActivityResult(
        contract = healthConnect.permissionContract()
    ) { _: Set<String> -> viewModel.refreshHealthConnectSteps() }

    // A watch syncs in batches, so the figure is refreshed whenever Home
    // returns to the foreground rather than being subscribed to.
    LifecycleStartEffect(state.stepSource) {
        if (state.stepSource == StepSource.HEALTH_CONNECT) viewModel.refreshHealthConnectSteps()
        onStopOrDispose { }
    }
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.displayName.ifBlank { "Lifter" },
                style = MaterialTheme.typography.displayMedium,
                color = Chalk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            badgeIcon(equipped.badgeId)?.let { icon ->
                Spacer(Modifier.width(Space.sm))
                Icon(icon, contentDescription = "Your badge", tint = Chalk, modifier = Modifier.size(28.dp))
            }
        }
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
                val fromWatch = state.stepSource == StepSource.HEALTH_CONNECT
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null, tint = ChalkDim, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(Space.sm))
                    when {
                        fromWatch && state.healthConnectReady -> Text(
                            "%,d steps".format(state.todaySteps),
                            style = NumericType.stat,
                            color = Chalk
                        )
                        fromWatch -> Text(
                            "Not connected yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = ChalkDim
                        )
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
                Caption(
                    if (fromWatch) "From Health Connect, including your watch"
                    else "From your phone's built-in step counter"
                )

                if (fromWatch && !state.healthConnectReady) {
                    Spacer(Modifier.height(Space.md))
                    SecondaryButton(
                        text = "Connect Health Connect",
                        onClick = { healthConnectRequest.launch(viewModel.healthConnectPermissions()) }
                    )
                } else if (!fromWatch && state.stepSensorAvailable && !stepsPermission.isGranted) {
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

        Spacer(Modifier.height(Space.xl))
        SectionHeader("Coach")
        IronCard(onClick = onOpenCoach) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.QuestionAnswer,
                    contentDescription = null,
                    tint = ChalkDim,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Ask about training",
                        style = MaterialTheme.typography.titleMedium,
                        color = Chalk
                    )
                    Caption("What to train today, how to progress, how you are doing.")
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = ChalkFaint)
            }
        }

        Spacer(Modifier.height(Space.xl))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader("Quests", Modifier.weight(1f))
            CoinPill(quests.balance)
        }
        IronCard(onClick = onOpenQuests) {
            Column {
                val claimable = quests.quests.count { it.isClaimable }
                val inProgress = quests.quests.count { !it.isClaimed && !it.isComplete }
                Text(
                    when {
                        claimable == 1 -> "1 reward waiting"
                        claimable > 1 -> "$claimable rewards waiting"
                        else -> "No rewards waiting"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (claimable > 0) PrGold else Chalk
                )
                Caption(
                    if (claimable > 0) "Claim them, then spend the coins in the store."
                    else "$inProgress quest(s) still in progress. Coins come from training, not from tapping."
                )
            }
        }

        if (state.recovery.isNotEmpty()) {
            Spacer(Modifier.height(Space.xl))
            SectionHeader("Recovery")
            IronCard {
                Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                    // Anything not in the list has not been trained recently
                    // enough to still be recovering, so it is ready.
                    BodyMap(readiness = state.recovery.associate { it.muscle to it.fraction })
                    state.recovery.take(RECOVERY_ROWS).forEach { muscle ->
                        RecoveryRow(muscle)
                    }
                    val hidden = state.recovery.size - RECOVERY_ROWS
                    if (hidden > 0) Caption("$hidden more fully recovered")
                }
            }
        }

        if (goals.isNotEmpty()) {
            Spacer(Modifier.height(Space.xl))
            SectionHeader("Strength goals")
            IronCard(onClick = onOpenGoals) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                    goals.take(HOME_GOALS).forEach { goal ->
                        GoalSummaryRow(goal, state.unit)
                    }
                    val hidden = goals.size - HOME_GOALS
                    if (hidden > 0) Caption("$hidden more")
                }
            }
        } else {
            Spacer(Modifier.height(Space.xl))
            SectionHeader("Strength goals")
            IronCard(onClick = onOpenGoals) {
                Column {
                    Text(
                        "Set a target on a lift",
                        style = MaterialTheme.typography.titleMedium,
                        color = Chalk
                    )
                    Caption("Track the share of it you have reached, read straight from your logged sets.")
                }
            }
        }

        if (equipped.widgetIds.contains("widget_plates") || equipped.widgetIds.contains("widget_1rm")) {
            Spacer(Modifier.height(Space.xl))
            SectionHeader("Your widgets")
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                if (equipped.widgetIds.contains("widget_plates")) {
                    PlateCalculatorWidget(state.unit, state.equipment)
                }
                if (equipped.widgetIds.contains("widget_1rm")) OneRepMaxWidget(state.unit)
            }
        }

        val recent = state.recentBlock
        if (recent.workouts > 0) {
            Spacer(Modifier.height(Space.xl))
            SectionHeader("Last ${recent.workouts} workouts")
            IronCard {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatTile(state.unit.formatVolume(recent.totalVolumeKg), "Total volume")
                        StatTile(formatDuration(recent.totalDurationSeconds), "Total time")
                    }
                    Spacer(Modifier.height(Space.lg))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatTile(formatDuration(recent.averageDurationSeconds), "Average session")
                        StatTile(
                            recent.recordsBroken.toString(),
                            "Records broken",
                            accent = if (recent.recordsBroken > 0) PrGold else Chalk
                        )
                    }
                    if (recent.workouts < HomeViewModel.RECENT_WINDOW) {
                        Spacer(Modifier.height(Space.md))
                        Caption("Everything you have logged so far, up to your last ${HomeViewModel.RECENT_WINDOW} sessions.")
                    }
                }
            }
        }

        val bodyweight = state.bodyweight
        if (bodyweight.size >= 2) {
            Spacer(Modifier.height(Space.xl))
            SectionHeader("Bodyweight")
            IronCard(onClick = onOpenBody) {
                LineChart(
                    points = bodyweight.map { point ->
                        ChartPoint(
                            formatShortDate(point.takenAt),
                            state.unit.weightValue(point.weightKg).toFloatOrNull() ?: 0f
                        )
                    },
                    color = Plate20,
                    valueFormatter = { "${it.toDouble().trimmed(1)} ${state.unit.weightSuffix}" },
                    description = "Bodyweight over time"
                )
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
    // A ticking flow rather than produceState: collectAsStateWithLifecycle stops
    // the timer when the screen is not started, so a session left open in the
    // background is not waking the app once a second to recompute a number
    // nobody is looking at.
    val elapsed by remember(active.startedAt) {
        flow {
            while (true) {
                emit(((System.currentTimeMillis() - active.startedAt) / 1000).toInt())
                delay(1000)
            }
        }
    }.collectAsStateWithLifecycle(initialValue = 0)
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

/** How many muscles the Home card lists before it stops. */
private const val RECOVERY_ROWS = 5

/** How many goals Home shows before sending the user to the full screen. */
private const val HOME_GOALS = 3

/**
 * One muscle's recovery.
 *
 * The bar carries the reading twice over: it fills as the muscle recovers, and
 * its colour comes back with it, faint just after training and solid once the
 * muscle is ready. Green is a data colour here, not decoration.
 */
@Composable
private fun RecoveryRow(muscle: Recovery.MuscleState) {
    val percent = (muscle.fraction * 100).toInt()
    val status = if (muscle.isFresh) "Fresh" else "$percent% · ${muscle.hoursRemaining}h left"

    Column(
        Modifier.semantics(mergeDescendants = true) {
            contentDescription = "${muscle.muscle.label}: " +
                if (muscle.isFresh) "recovered" else "$percent percent recovered, ${muscle.hoursRemaining} hours left"
        }
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(muscle.muscle.label, style = MaterialTheme.typography.bodyMedium, color = Chalk)
            Text(
                status,
                style = NumericType.small,
                color = if (muscle.isFresh) ChalkDim else ChalkFaint
            )
        }
        Spacer(Modifier.height(Space.xs))
        LinearProgressIndicator(
            progress = { muscle.fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            // Alpha rises with recovery: worked muscles read as drained, and the
            // colour returns as they come back.
            color = Plate10.copy(alpha = RECOVERY_MIN_ALPHA + (1f - RECOVERY_MIN_ALPHA) * muscle.fraction),
            trackColor = Slate700,
            drawStopIndicator = {}
        )
    }
}

/** Faintest a recovery bar goes, so a just-trained muscle is still visible. */
private const val RECOVERY_MIN_ALPHA = 0.3f

/** A goal on Home: the numbers and the bar, without the chart. */
@Composable
private fun GoalSummaryRow(goal: GoalCard, unit: UnitSystem) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                goal.exerciseName,
                style = MaterialTheme.typography.bodyLarge,
                color = Chalk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(Space.sm))
            Text(
                "${(goal.progress * 100).toInt()}%",
                style = NumericType.small,
                color = if (goal.isAchieved) PrGold else ChalkDim
            )
        }
        Spacer(Modifier.height(Space.xs))
        LinearProgressIndicator(
            progress = { goal.progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .semantics {
                    contentDescription = "${goal.exerciseName}, ${(goal.progress * 100).toInt()} percent of " +
                        unit.formatWeight(goal.targetKg)
                },
            color = if (goal.isAchieved) PrGold else Plate10,
            trackColor = Slate700,
            drawStopIndicator = {}
        )
        Spacer(Modifier.height(Space.xs))
        Caption("${unit.formatWeight(goal.currentKg)} of ${unit.formatWeight(goal.targetKg)} · ${goal.metric.shortLabel}")
    }
}

/**
 * Bought widget: what goes on each side of the bar.
 *
 * Convenience only. The same maths is already reachable from inside a workout;
 * this saves opening one. Nothing needed to log a set is behind the price.
 */
@Composable
private fun PlateCalculatorWidget(unit: UnitSystem, equipment: EquipmentSettings) {
    var target by rememberSaveable { mutableStateOf("") }
    // The bar follows Settings unless the user types over it, and is written in
    // the plate set's own unit because that is the number stamped on the bar.
    // Holding an override rather than seeding a field means a bar changed in
    // Settings reaches a widget that is already on screen.
    var barOverride by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(equipment) { barOverride = null }
    val bar = barOverride
        ?: TrainingMath.plateLabel(equipment.plateSet, equipment.barWeightKg).substringBefore(' ')
    val plateUnitSuffix = if (equipment.plateSet == PlateSet.KILOGRAMS) "kg" else "lb"

    IronCard {
        Column {
            Text("Plate calculator", style = MaterialTheme.typography.titleMedium, color = Chalk)
            Spacer(Modifier.height(Space.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                IronTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = "Target (${unit.weightSuffix})",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f)
                )
                IronTextField(
                    value = bar,
                    onValueChange = { barOverride = it },
                    label = "Bar ($plateUnitSuffix)",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f)
                )
            }

            val targetKg = target.toDisplayKg(unit)
            val barKg = bar.toPlateKg(equipment.plateSet)
            if (targetKg != null && barKg != null) {
                val loading = TrainingMath.platesPerSide(
                    targetKg,
                    barKg,
                    TrainingMath.platesFor(equipment.plateSet)
                )
                Spacer(Modifier.height(Space.md))
                if (loading.plates.isEmpty()) {
                    Caption("That is the bar on its own.")
                } else {
                    Text(
                        loading.plates.joinToString(" · ") {
                            TrainingMath.plateLabel(equipment.plateSet, it)
                        },
                        style = NumericType.setCell,
                        color = Chalk
                    )
                    Caption(
                        if (loading.isExact) "Each side"
                        else "Each side · closest is ${unit.formatWeight(loading.achievableTotalKg)}"
                    )
                }
            }
        }
    }
}

/**
 * Bought widget: a one-rep max estimate from a weight and a rep count.
 *
 * Uses the same Epley formula and the same reliability cut-off as the rest of
 * the app, so the number here cannot disagree with the one on an exercise.
 */
@Composable
private fun OneRepMaxWidget(unit: UnitSystem) {
    var weight by rememberSaveable { mutableStateOf("") }
    var reps by rememberSaveable { mutableStateOf("") }

    IronCard {
        Column {
            Text("1RM estimator", style = MaterialTheme.typography.titleMedium, color = Chalk)
            Spacer(Modifier.height(Space.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                IronTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = "Weight (${unit.weightSuffix})",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f)
                )
                IronTextField(
                    value = reps,
                    onValueChange = { reps = it },
                    label = "Reps",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
            }

            val weightKg = weight.toDisplayKg(unit)
            val repCount = reps.trim().toIntOrNull()
            if (weightKg != null && repCount != null && repCount > 0) {
                val estimate = TrainingMath.estimatedOneRepMax(weightKg, repCount)
                Spacer(Modifier.height(Space.md))
                Text(unit.formatWeight(estimate), style = NumericType.stat, color = Chalk)
                Caption(
                    if (TrainingMath.isReliableEstimate(repCount)) "Estimated one-rep max"
                    else "Rough only — the formula loses accuracy past ${TrainingMath.MAX_RELIABLE_REPS} reps"
                )
            }
        }
    }
}

/** Reads a bar weight typed in the plate set's own unit and returns kilograms. */
private fun String.toPlateKg(plateSet: PlateSet): Double? {
    val entered = trim().replace(',', '.').toDoubleOrNull() ?: return null
    if (entered <= 0) return null
    return if (plateSet == PlateSet.KILOGRAMS) entered else TrainingMath.lbToKg(entered)
}

/** Reads a typed figure in the display unit and returns kilograms. */
private fun String.toDisplayKg(unit: UnitSystem): Double? {
    val entered = trim().replace(',', '.').toDoubleOrNull() ?: return null
    if (entered <= 0) return null
    return if (unit == UnitSystem.METRIC) entered else TrainingMath.lbToKg(entered)
}
