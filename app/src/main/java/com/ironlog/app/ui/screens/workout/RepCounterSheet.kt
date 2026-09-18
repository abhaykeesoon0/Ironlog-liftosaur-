package com.ironlog.app.ui.screens.workout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ironlog.app.core.sensors.RepSignal
import com.ironlog.app.ui.components.Caption
import com.ironlog.app.ui.components.PrimaryButton
import com.ironlog.app.ui.components.SecondaryButton
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.NumericType
import com.ironlog.app.ui.theme.Plate20
import com.ironlog.app.ui.theme.Slate700
import com.ironlog.app.ui.theme.Slate800
import com.ironlog.app.ui.theme.Space
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/**
 * Live accelerometer rep count for one set. The sensor is only registered while
 * this sheet is on screen: the flow is collected in a LaunchedEffect, so leaving
 * composition cancels it and unregisters the listener.
 *
 * The count is a suggestion. Nothing is written until the user taps "Use".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RepCounterSheet(
    exerciseName: String,
    setNumber: Int,
    available: Boolean,
    signals: () -> Flow<RepSignal>,
    onUse: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var count by remember { mutableIntStateOf(0) }
    var offset by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val trace = remember { mutableStateListOf<Float>() }

    if (available) {
        LaunchedEffect(Unit) {
            signals()
                .catch { error = "The motion sensor stopped responding." }
                .collect { signal ->
                    count = signal.repCount
                    trace.add(signal.magnitude)
                    if (trace.size > TRACE_POINTS) trace.removeAt(0)
                }
        }
    }
    val shown = (count - offset).coerceAtLeast(0)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Slate800) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Space.lg).padding(bottom = Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Rep counter", style = MaterialTheme.typography.titleLarge, color = Chalk)
            Caption("$exerciseName · set $setNumber")
            Spacer(Modifier.height(Space.lg))

            if (!available) {
                Text(
                    "This phone has no accelerometer, so reps can't be counted automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ChalkDim
                )
                Spacer(Modifier.height(Space.lg))
                SecondaryButton(text = "Close", onClick = onDismiss)
                return@Column
            }

            Text(
                shown.toString(),
                style = NumericType.hero.copy(fontSize = NumericType.hero.fontSize * 2),
                color = Chalk,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            Text("reps detected", style = MaterialTheme.typography.bodySmall, color = ChalkDim)
            Spacer(Modifier.height(Space.md))

            // Live trace of the filtered signal, so the user can see it responding.
            Canvas(Modifier.fillMaxWidth().height(56.dp)) {
                drawLine(Slate700, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                if (trace.size < 2) return@Canvas
                val max = (trace.maxOrNull() ?: 1f).coerceAtLeast(3f)
                val step = size.width / (TRACE_POINTS - 1)
                for (i in 1 until trace.size) {
                    drawLine(
                        Plate20,
                        Offset((i - 1) * step, size.height - trace[i - 1] / max * size.height),
                        Offset(i * step, size.height - trace[i] / max * size.height),
                        2.dp.toPx()
                    )
                }
            }
            Spacer(Modifier.height(Space.md))
            Caption(
                "Hold or strap the phone to the moving limb, or keep it in a tight pocket. " +
                    "The count is an estimate from the motion sensor — check it before you use it."
            )
            error?.let {
                Spacer(Modifier.height(Space.sm))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(Space.lg))
            PrimaryButton(
                text = if (shown == 1) "Use 1 rep" else "Use $shown reps",
                onClick = { onUse(shown) },
                enabled = shown > 0
            )
            Spacer(Modifier.height(Space.sm))
            SecondaryButton(text = "Reset count", onClick = { offset = count })
        }
    }
}

private const val TRACE_POINTS = 90
