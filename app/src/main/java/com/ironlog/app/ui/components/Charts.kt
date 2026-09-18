package com.ironlog.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.ChalkFaint
import com.ironlog.app.ui.theme.NumericType
import com.ironlog.app.ui.theme.Plate10
import com.ironlog.app.ui.theme.Plate20
import com.ironlog.app.ui.theme.Plate25
import com.ironlog.app.ui.theme.Slate600
import com.ironlog.app.ui.theme.Slate700
import com.ironlog.app.ui.theme.Space

/**
 * Charts are drawn directly on a Canvas rather than pulled from a library: the
 * app needs three simple chart types, and a charting dependency would be the
 * largest thing in the APK. Each chart carries a content description summarising
 * the data, because a Canvas is otherwise invisible to TalkBack.
 */

data class ChartPoint(val label: String, val value: Float)

@Composable
fun BarChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    color: Color = Plate20,
    height: Dp = 160.dp,
    valueFormatter: (Float) -> String = { it.toInt().toString() },
    description: String = "Bar chart"
) {
    if (points.isEmpty()) return
    val max = points.maxOf { it.value }.takeIf { it > 0f } ?: 1f
    val summary = "$description. " + points.joinToString { "${it.label}: ${valueFormatter(it.value)}" }

    Column(modifier.semantics { contentDescription = summary }) {
        Text(
            text = "Peak ${valueFormatter(max)}",
            style = NumericType.small,
            color = ChalkFaint
        )
        Spacer(Modifier.height(Space.sm))
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val gap = 6.dp.toPx()
            val barWidth = ((size.width - gap * (points.size - 1)) / points.size).coerceAtLeast(2f)
            // Baseline and a faint mid gridline give the bars a reference.
            drawLine(Slate600, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            drawLine(
                Slate700, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx()
            )
            points.forEachIndexed { index, point ->
                val barHeight = (point.value / max) * size.height
                if (barHeight <= 0f) return@forEachIndexed
                drawRoundRect(
                    color = color,
                    topLeft = Offset(index * (barWidth + gap), size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )
            }
        }
        Spacer(Modifier.height(Space.xs))
        AxisLabels(points.map { it.label })
    }
}

@Composable
fun LineChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    color: Color = Plate20,
    height: Dp = 180.dp,
    valueFormatter: (Float) -> String = { it.toInt().toString() },
    description: String = "Line chart"
) {
    if (points.isEmpty()) return
    val min = points.minOf { it.value }
    val max = points.maxOf { it.value }
    // Pad the range so a flat series does not sit on the top edge, and so the
    // line is never drawn clipped against the canvas bounds.
    val span = (max - min).takeIf { it > 0f } ?: (max.takeIf { it > 0f } ?: 1f)
    val lower = min - span * 0.1f
    val upper = max + span * 0.1f
    val summary = "$description. From ${valueFormatter(points.first().value)} to " +
        "${valueFormatter(points.last().value)}, best ${valueFormatter(max)}"

    Column(modifier.semantics { contentDescription = summary }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("High ${valueFormatter(max)}", style = NumericType.small, color = ChalkFaint)
            Text("Low ${valueFormatter(min)}", style = NumericType.small, color = ChalkFaint)
        }
        Spacer(Modifier.height(Space.sm))
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val stepX = if (points.size > 1) size.width / (points.size - 1) else 0f
            fun y(value: Float) = size.height - ((value - lower) / (upper - lower)) * size.height

            listOf(0f, 0.5f, 1f).forEach { fraction ->
                drawLine(
                    Slate700,
                    Offset(0f, size.height * fraction),
                    Offset(size.width, size.height * fraction),
                    1.dp.toPx()
                )
            }

            val path = Path()
            points.forEachIndexed { index, point ->
                val x = if (points.size == 1) size.width / 2 else index * stepX
                if (index == 0) path.moveTo(x, y(point.value)) else path.lineTo(x, y(point.value))
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            points.forEachIndexed { index, point ->
                val x = if (points.size == 1) size.width / 2 else index * stepX
                drawCircle(color, radius = 3.5.dp.toPx(), center = Offset(x, y(point.value)))
            }
        }
        Spacer(Modifier.height(Space.xs))
        AxisLabels(listOfNotNull(points.first().label, points.last().label.takeIf { points.size > 1 }))
    }
}

/**
 * Horizontal proportion bars, one per category -- used for the muscle-group
 * split, where a pie chart would make close values impossible to compare.
 */
@Composable
fun ProportionBars(
    items: List<ChartPoint>,
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(Plate20),
    valueFormatter: (Float) -> String = { it.toInt().toString() }
) {
    val max = items.maxOfOrNull { it.value }?.takeIf { it > 0f } ?: 1f
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.md)) {
        items.forEachIndexed { index, item ->
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(item.label, style = MaterialTheme.typography.bodyMedium, color = ChalkDim)
                    Text(valueFormatter(item.value), style = NumericType.small, color = ChalkDim)
                }
                Spacer(Modifier.height(Space.xs))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(Slate700, RoundedCornerShape(4.dp))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(item.value / max)
                            .height(8.dp)
                            .background(colors[index % colors.size], RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}

/**
 * Draws a recorded route with no map tiles -- the app has no network access by
 * design, so the route is shown as a shape with start and finish markers.
 */
@Composable
fun RouteShape(
    normalisedPoints: List<Pair<Float, Float>>,
    modifier: Modifier = Modifier,
    color: Color = Plate20
) {
    Canvas(
        modifier.semantics {
            contentDescription = if (normalisedPoints.size < 2) "No route recorded yet" else "Route map"
        }
    ) {
        if (normalisedPoints.size < 2) return@Canvas
        val pad = 16.dp.toPx()
        val side = minOf(size.width, size.height) - pad * 2
        val offsetX = (size.width - side) / 2
        val offsetY = (size.height - side) / 2
        fun map(p: Pair<Float, Float>) = Offset(offsetX + p.first * side, offsetY + p.second * side)

        val path = Path()
        normalisedPoints.forEachIndexed { i, p ->
            val o = map(p)
            if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
        }
        drawPath(path, color, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(Plate10, 7.dp.toPx(), map(normalisedPoints.first()))
        drawCircle(Plate25, 7.dp.toPx(), map(normalisedPoints.last()))
    }
}

@Composable
private fun AxisLabels(labels: List<String>) {
    if (labels.isEmpty()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        // Only the first, middle and last labels: a label under every bar would
        // collide on a phone-width chart.
        val shown = when {
            labels.size <= 3 -> labels
            else -> listOf(labels.first(), labels[labels.size / 2], labels.last())
        }
        shown.forEach {
            Text(
                it,
                style = NumericType.small,
                color = ChalkFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Small coloured square used in chart legends. */
@Composable
fun LegendSwatch(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(Space.xs))
        Text(label, style = MaterialTheme.typography.bodySmall, color = ChalkDim, modifier = Modifier.padding(end = Space.md))
    }
}
