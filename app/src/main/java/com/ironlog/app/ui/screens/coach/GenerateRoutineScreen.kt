package com.ironlog.app.ui.screens.coach

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.domain.usecase.RoutineGenerator
import com.ironlog.app.ui.components.Caption
import com.ironlog.app.ui.components.ErrorBanner
import com.ironlog.app.ui.components.IronCard
import com.ironlog.app.ui.components.IronChip
import com.ironlog.app.ui.components.IronTopBar
import com.ironlog.app.ui.components.PrimaryButton
import com.ironlog.app.ui.components.SectionHeader
import com.ironlog.app.ui.screens.profile.Stepper
import com.ironlog.app.ui.format.formatClock
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.Space

/**
 * Builds a week of training from a few answers, previews it, and saves it as
 * routines the user can start straight away.
 *
 * The plan is shown before it is saved. A generated programme is something
 * somebody will act on under a loaded bar, so it should be inspected first, and
 * the reasoning behind each choice should be visible: the day names say what
 * they train, and the sets and reps follow from the goal.
 */
@Composable
fun GenerateRoutineScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: GenerateRoutineViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        IronTopBar(title = "Generate a routine", onBack = onBack)

        LazyColumn(
            contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            item { ErrorBanner(state.error) }

            item {
                IronCard {
                    Column {
                        Text("Your goal", style = MaterialTheme.typography.bodyLarge, color = Chalk)
                        Spacer(Modifier.height(Space.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            RoutineGenerator.Goal.entries.forEach { goal ->
                                IronChip(
                                    text = goal.label,
                                    selected = state.request.goal == goal
                                ) { viewModel.setGoal(goal) }
                            }
                        }
                        Spacer(Modifier.height(Space.sm))
                        Caption(state.request.goal.detail)
                    }
                }
            }

            item {
                IronCard {
                    Column {
                        Stepper(
                            label = "Days a week",
                            value = "${state.request.daysPerWeek}",
                            onDecrement = { viewModel.changeDays(-1) },
                            onIncrement = { viewModel.changeDays(1) },
                            caption = "Two to six. The split follows from this.",
                            canDecrement = state.request.daysPerWeek > 2,
                            canIncrement = state.request.daysPerWeek < 6
                        )
                        Spacer(Modifier.height(Space.sm))
                        Stepper(
                            label = "Session length",
                            value = formatClock(state.request.minutesPerSession * 60),
                            onDecrement = { viewModel.changeMinutes(-15) },
                            onIncrement = { viewModel.changeMinutes(15) },
                            caption = "How long you have, warm-up included",
                            canDecrement = state.request.minutesPerSession > 30,
                            canIncrement = state.request.minutesPerSession < 120
                        )
                    }
                }
            }

            item {
                SectionHeader("Your week")
                Caption("${state.plan.days.size} sessions · ${state.plan.totalExercises} exercises")
            }

            items(state.plan.days, key = { it.name }) { day ->
                IronCard {
                    Column {
                        Text(day.name, style = MaterialTheme.typography.titleMedium, color = Chalk)
                        Caption(day.focus)
                        Spacer(Modifier.height(Space.md))
                        day.exercises.forEach { slot ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    slot.exercise.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Chalk,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${slot.sets} × ${slot.repsLow}-${slot.repsHigh}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ChalkDim
                                )
                            }
                            Spacer(Modifier.height(Space.xs))
                        }
                    }
                }
            }

            item {
                PrimaryButton(
                    text = if (state.isSaving) "Saving" else "Save these routines",
                    onClick = { viewModel.save(onSaved) },
                    enabled = !state.isSaving,
                    loading = state.isSaving
                )
            }
            item {
                Caption(
                    "Saved as one routine per session. Everything stays editable afterwards — this is " +
                        "a starting point, not a prescription."
                )
            }
            item { Spacer(Modifier.height(Space.xl)) }
        }
    }
}
