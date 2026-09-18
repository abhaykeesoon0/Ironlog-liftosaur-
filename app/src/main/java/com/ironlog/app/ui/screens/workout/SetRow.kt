package com.ironlog.app.ui.screens.workout

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ironlog.app.core.security.Validators
import com.ironlog.app.data.entity.ExerciseCategory
import com.ironlog.app.data.entity.SetType
import com.ironlog.app.data.entity.UnitSystem
import com.ironlog.app.data.entity.WorkoutSetEntity
import com.ironlog.app.ui.components.PrBadge
import com.ironlog.app.ui.format.parseWeightToKg
import com.ironlog.app.ui.format.trimmed
import com.ironlog.app.ui.format.weightValue
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.ChalkFaint
import com.ironlog.app.ui.theme.Danger
import com.ironlog.app.ui.theme.NumericType
import com.ironlog.app.ui.theme.Radius
import com.ironlog.app.ui.theme.Slate600
import com.ironlog.app.ui.theme.Slate700
import com.ironlog.app.ui.theme.Slate800
import com.ironlog.app.ui.theme.Slate900
import com.ironlog.app.ui.theme.Space
import com.ironlog.app.ui.theme.Touch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** Column widths shared by the header row and every set row, so they line up. */
internal object SetColumns {
    val type: Dp = 40.dp
    val input: Dp = 68.dp
    val tick: Dp = 48.dp
}

/** How long typing must pause before a value is saved. */
private const val COMMIT_DEBOUNCE_MS = 700L

/**
 * Local text for one numeric cell. The text lives here, not in the ViewModel,
 * so a database emission mid-typing cannot overwrite what the user is entering;
 * it only syncs from storage while the field is not focused.
 */
@Stable
private class FieldState(initial: String) {
    var text by mutableStateOf(initial)
    var focused by mutableStateOf(false)
    var invalid by mutableStateOf(false)
}

@Composable
private fun rememberField(setId: String, stored: String, commit: (String) -> Boolean): FieldState {
    val state = remember(setId) { FieldState(stored) }
    val currentStored by rememberUpdatedState(stored)
    val currentCommit by rememberUpdatedState(commit)

    LaunchedEffect(state, stored) {
        if (!state.focused) {
            state.text = stored
            state.invalid = false
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.text }.collectLatest { text ->
            if (text != currentStored) {
                delay(COMMIT_DEBOUNCE_MS)
                state.invalid = !currentCommit(text)
            }
        }
    }
    return state
}

private fun FieldState.flush(stored: String, commit: (String) -> Boolean) {
    if (text != stored) invalid = !commit(text)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SetRow(
    set: WorkoutSetEntity,
    previous: WorkoutSetEntity?,
    category: ExerciseCategory,
    unit: UnitSystem,
    onCycleType: () -> Unit,
    onWeight: (Double?) -> Unit,
    onReps: (Int?) -> Unit,
    onDuration: (Int?) -> Unit,
    onDistance: (Double?) -> Unit,
    onToggle: (fallbackWeightKg: Double?, fallbackReps: Int?) -> Unit,
    onDelete: () -> Unit,
    onCountReps: (() -> Unit)?
) {
    var menuOpen by remember { mutableStateOf(false) }

    // ---- field definitions per category -------------------------------------
    val distanceFactor = if (unit == UnitSystem.METRIC) 1000.0 else 1609.344

    val weightStored = unit.weightValue(set.weightKg)
    val commitWeight: (String) -> Boolean = { text ->
        if (text.isBlank()) { onWeight(null); true }
        else unit.parseWeightToKg(text)?.let { onWeight(it); true } ?: false
    }
    val repsStored = set.reps?.toString().orEmpty()
    val commitReps: (String) -> Boolean = { text ->
        if (text.isBlank()) { onReps(null); true }
        else Validators.parseReps(text)?.let { onReps(it); true } ?: false
    }
    // Duration exercises log seconds; cardio logs minutes alongside distance.
    val isCardio = category == ExerciseCategory.CARDIO
    val durationStored = set.durationSeconds?.let { if (isCardio) (it / 60.0).trimmed(1) else it.toString() }.orEmpty()
    val commitDuration: (String) -> Boolean = { text ->
        if (text.isBlank()) { onDuration(null); true }
        else {
            val value = text.trim().replace(',', '.').toDoubleOrNull()
            val seconds = value?.let { if (isCardio) (it * 60).toInt() else it.toInt() }
            if (seconds != null && seconds in 0..86_400) { onDuration(seconds); true } else false
        }
    }
    val distanceStored = set.distanceMeters?.let { (it / distanceFactor).trimmed(2) }.orEmpty()
    val commitDistance: (String) -> Boolean = { text ->
        if (text.isBlank()) { onDistance(null); true }
        else {
            val value = text.trim().replace(',', '.').toDoubleOrNull()
            if (value != null && value.isFinite() && value in 0.0..500.0) { onDistance(value * distanceFactor); true }
            else false
        }
    }

    val weight = rememberField(set.id, weightStored, commitWeight)
    val reps = rememberField(set.id + ":r", repsStored, commitReps)
    val duration = rememberField(set.id + ":d", durationStored, commitDuration)
    val distance = rememberField(set.id + ":km", distanceStored, commitDistance)

    val rowShape = RoundedCornerShape(Radius.control)
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Touch.setRow)
                .background(if (set.isCompleted) Slate700 else Slate800, rowShape)
                .padding(vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Set number / type. Tap cycles the type, long-press opens options.
            Box(
                modifier = Modifier
                    .width(SetColumns.type)
                    .height(Touch.min)
                    .combinedClickable(
                        onClickLabel = "Change set type",
                        onLongClickLabel = "Set options",
                        onClick = onCycleType,
                        onLongClick = { menuOpen = true }
                    )
                    .semantics { contentDescription = "Set ${set.setNumber}, ${set.type.label}" },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = set.type.shortLabel.ifEmpty { set.setNumber.toString() },
                    style = NumericType.setCell,
                    color = if (set.type == SetType.WARMUP) ChalkFaint else Chalk
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = Slate800) {
                    if (onCountReps != null && category.tracksReps) {
                        DropdownMenuItem(
                            text = { Text("Count reps with motion sensor", color = Chalk) },
                            onClick = { menuOpen = false; onCountReps() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Delete set", color = Danger) },
                        onClick = { menuOpen = false; onDelete() }
                    )
                }
            }

            Text(
                text = formatPrevious(previous, category, unit),
                style = NumericType.small,
                color = ChalkFaint,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics {
                    contentDescription = "Previous: ${formatPrevious(previous, category, unit)}"
                }
            )

            when {
                isCardio -> {
                    NumberCell(distance, previous?.distanceMeters?.let { (it / distanceFactor).trimmed(2) }, unit.distanceSuffix, KeyboardType.Decimal, set.isCompleted, commitDistance, distanceStored)
                    NumberCell(duration, previous?.durationSeconds?.let { (it / 60.0).trimmed(1) }, "min", KeyboardType.Decimal, set.isCompleted, commitDuration, durationStored)
                }
                category == ExerciseCategory.DURATION -> {
                    NumberCell(duration, previous?.durationSeconds?.toString(), "seconds", KeyboardType.Number, set.isCompleted, commitDuration, durationStored)
                }
                else -> {
                    if (category.tracksWeight) {
                        NumberCell(weight, previous?.weightKg?.let { unit.weightValue(it) }, unit.weightSuffix, KeyboardType.Decimal, set.isCompleted, commitWeight, weightStored)
                    }
                    NumberCell(reps, previous?.reps?.toString(), "reps", KeyboardType.Number, set.isCompleted, commitReps, repsStored)
                }
            }

            // Complete tick.
            IconButton(
                onClick = {
                    // Save anything still waiting on the debounce first; the
                    // ViewModel serialises writes so this lands before the tick.
                    weight.flush(weightStored, commitWeight)
                    reps.flush(repsStored, commitReps)
                    duration.flush(durationStored, commitDuration)
                    distance.flush(distanceStored, commitDistance)
                    onToggle(previous?.weightKg, previous?.reps)
                },
                modifier = Modifier.width(SetColumns.tick).semantics {
                    stateDescription = if (set.isCompleted) "Completed" else "Not completed"
                }
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(if (set.isCompleted) Chalk else Slate700, RoundedCornerShape(8.dp))
                        .border(1.dp, if (set.isCompleted) Chalk else Slate600, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = if (set.isCompleted) "Mark set ${set.setNumber} not done" else "Complete set ${set.setNumber}",
                        tint = if (set.isCompleted) Slate900 else ChalkFaint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        if (set.prType != null || (set.autoCountedReps != null && set.autoCountedReps == set.reps)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = SetColumns.type, top = 2.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PrBadge(set.prType)
                if (set.autoCountedReps != null && set.autoCountedReps == set.reps) {
                    Text("Reps counted by sensor", style = MaterialTheme.typography.labelSmall, color = ChalkFaint)
                }
            }
        }
    }
}

@Composable
private fun NumberCell(
    field: FieldState,
    placeholder: String?,
    label: String,
    keyboardType: KeyboardType,
    completed: Boolean,
    commit: (String) -> Boolean,
    stored: String
) {
    val shape = RoundedCornerShape(Radius.control)
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .width(SetColumns.input)
            .height(40.dp)
            .background(if (completed) Slate800 else Slate700, shape)
            .border(1.dp, if (field.invalid) Danger else if (field.focused) Chalk else Slate600, shape),
        contentAlignment = Alignment.Center
    ) {
        BasicTextField(
            value = field.text,
            onValueChange = { value ->
                field.text = value.take(7)
                field.invalid = false
            },
            singleLine = true,
            textStyle = NumericType.setCell.copy(color = Chalk),
            cursorBrush = SolidColor(Chalk),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.xs)
                .onFocusChanged {
                    if (field.focused && !it.isFocused) field.flush(stored, commit)
                    field.focused = it.isFocused
                }
                .semantics { contentDescription = label },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.Center) {
                    if (field.text.isEmpty()) {
                        Text(
                            placeholder ?: "–",
                            style = NumericType.setCell,
                            color = ChalkFaint,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    inner()
                }
            }
        )
    }
}

/** Header labels above the set rows, sharing [SetColumns] widths. */
@Composable
internal fun SetHeaderRow(category: ExerciseCategory, unit: UnitSystem) {
    Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
        HeaderText("SET", Modifier.width(SetColumns.type))
        HeaderText("PREVIOUS", Modifier.weight(1f))
        when {
            category == ExerciseCategory.CARDIO -> {
                HeaderText(unit.distanceSuffix.uppercase(), Modifier.width(SetColumns.input + 4.dp))
                HeaderText("MIN", Modifier.width(SetColumns.input + 4.dp))
            }
            category == ExerciseCategory.DURATION -> HeaderText("SEC", Modifier.width(SetColumns.input + 4.dp))
            else -> {
                if (category.tracksWeight) HeaderText(unit.weightSuffix.uppercase(), Modifier.width(SetColumns.input + 4.dp))
                HeaderText("REPS", Modifier.width(SetColumns.input + 4.dp))
            }
        }
        HeaderText("", Modifier.width(SetColumns.tick))
    }
}

@Composable
private fun HeaderText(text: String, modifier: Modifier) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = ChalkDim, textAlign = TextAlign.Center, modifier = modifier)
}

/** "60 × 8", "12 reps", "45 s", "2.5 km", or a dash when there is no history. */
internal fun formatPrevious(previous: WorkoutSetEntity?, category: ExerciseCategory, unit: UnitSystem): String {
    previous ?: return "—"
    return when {
        category == ExerciseCategory.CARDIO -> previous.distanceMeters?.let {
            val factor = if (unit == UnitSystem.METRIC) 1000.0 else 1609.344
            "${(it / factor).trimmed(2)} ${unit.distanceSuffix}"
        } ?: "—"
        category == ExerciseCategory.DURATION -> previous.durationSeconds?.let { "$it s" } ?: "—"
        category.tracksWeight && previous.weightKg != null && previous.reps != null ->
            "${unit.weightValue(previous.weightKg)} × ${previous.reps}"
        previous.reps != null -> "${previous.reps} reps"
        else -> "—"
    }
}
