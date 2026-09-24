package com.ironlog.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.Slate800
import com.ironlog.app.ui.theme.Space
import java.io.File

/**
 * Two progress photos over each other with a divider to drag: the earlier
 * one on the left of the line, the later on the right. Same pose, same
 * frame, and the change shows where the mirror hides it.
 *
 * Both images stay inside the app's private storage; this only draws them.
 */
@Composable
fun PhotoCompareDialog(
    beforePath: String,
    beforeLabel: String,
    afterPath: String,
    afterLabel: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0E1216))
                .padding(Space.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Compare, contentDescription = null, tint = ChalkDim)
                Spacer(Modifier.width(Space.sm))
                Text("Before and after", style = MaterialTheme.typography.titleMedium, color = Chalk, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close comparison", tint = Chalk) }
            }
            Spacer(Modifier.height(Space.sm))
            CompareSlider(beforePath, afterPath, Modifier.weight(1f).fillMaxWidth())
            Spacer(Modifier.height(Space.sm))
            Row(Modifier.fillMaxWidth()) {
                Text(beforeLabel, style = MaterialTheme.typography.labelLarge, color = ChalkDim, modifier = Modifier.weight(1f))
                Text(afterLabel, style = MaterialTheme.typography.labelLarge, color = Chalk)
            }
            Caption("Drag the line to compare.")
        }
    }
}

@Composable
private fun CompareSlider(beforePath: String, afterPath: String, modifier: Modifier = Modifier) {
    var fraction by remember { mutableFloatStateOf(0.5f) }
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Slate800)
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    fraction = (change.position.x / size.width).coerceIn(0.02f, 0.98f)
                }
            }
            .semantics { contentDescription = "Comparison slider at ${(fraction * 100).toInt()} percent" }
    ) {
        AsyncImage(
            model = File(afterPath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // The earlier photo, clipped to the left of the line.
        AsyncImage(
            model = File(beforePath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    clipRect(right = size.width * fraction) { this@drawWithContent.drawContent() }
                }
        )
        Box(
            Modifier
                .fillMaxHeight()
                .width(2.dp)
                .align(Alignment.CenterStart)
                .offsetFraction(fraction)
                .background(Chalk)
        )
        Box(
            Modifier
                .size(36.dp)
                .align(Alignment.CenterStart)
                .offsetFraction(fraction, centre = true)
                .background(Chalk, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Compare, contentDescription = null, tint = Color(0xFF0E1216), modifier = Modifier.size(20.dp))
        }
    }
}

/** Places a box at a horizontal fraction of its parent's width. */
private fun Modifier.offsetFraction(fraction: Float, centre: Boolean = false): Modifier =
    this.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            val x = (constraints.maxWidth * fraction).toInt() - placeable.width / 2
            placeable.placeRelative(x, 0)
        }
    }

