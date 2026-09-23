package com.ironlog.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.ChalkFaint
import com.ironlog.app.ui.theme.ChartSeries
import com.ironlog.app.ui.theme.NumericType
import com.ironlog.app.ui.theme.Plate10
import com.ironlog.app.ui.theme.Plate15
import com.ironlog.app.ui.theme.Plate20
import com.ironlog.app.ui.theme.Slate500
import com.ironlog.app.ui.theme.Slate600
import com.ironlog.app.ui.theme.Slate700
import com.ironlog.app.ui.theme.Space

/**
 * The charts the progress screen needs beyond a bar and a line: a share, a
 * distribution, a comparison and a progression.
 *
 * Drawn on a Canvas like the others, for the same reason -- a charting library
 * would be the largest thing in the APK -- and each one carries a spoken
 * summary, because a Canvas is a blank rectangle to a screen reader.
 */

/**
 * A ring showing how a total divides between categories.
 *
 * The hole in the middle is not decoration: it carries the total, which is the
 * number every share is a share of. Percentages are printed in the legend
 * rather than on the ring, because two slices a few degrees apart cannot be
 * told apart by eye. That is the honest weakness of a pie, and it is why the
 * proportion bars remain underneath it rather than being replaced by it.
 */
@Composable
fun DonutChart(
    items: List<ChartPoint>,
    modifier: Modifier = Modifier,
    colors: List<Color> = ChartSeries,
    diameter: Dp = 164.dp,
    centreLabel: String = "",
    centreValue: String = "",
    description: String = "Share"
) {
    val total = items.fold(0f) { sum, item -> sum + item.value }
    if (items.isEmpty() || total <= 0f) return

    val summary = "$description. " + items.joinToString {
        "${it.label} ${percent(it.value, total)} percent"
    }

    Row(
        modifier.semantics { contentDescription = summary },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(diameter)) {
                val stroke = size.minDimension * 0.18f
                val inset = stroke / 2f
                val box = size.minDimension - stroke
                var start = -90f
                items.forEachIndexed { index, item ->
                    val sweep = (item.value / total) * 360f
                    drawArc(
                        color = colors[index % colors.size],
                        startAngle = start,
                        // A hairline gap, so two neighbouring slices of similar
                        // size stay two slices rather than one long one.
                        sweepAngle = (sweep - 1.5f).coerceAtLeast(0.4f),
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(box, box),
                        style = Stroke(width = stroke, cap = StrokeCap.Butt)
                    )
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(centreValue, style = NumericType.stat, color = Chalk)
                Text(centreLabel, style = MaterialTheme.typography.labelSmall, color = ChalkFaint)
            }
        }
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            items.forEachIndexed { index, item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(colors[index % colors.size], RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.width(Space.sm))
                    Text(
                        item.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = ChalkDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text("${percent(item.value, total)}%", style = NumericType.small, color = Chalk)
                }
            }
        }
    }
}

private fun percent(value: Float, total: Float): Int = ((value / total) * 100f).toInt()

/**
 * A histogram: bars that touch, because the axis under them is continuous.
 *
 * The tallest bin is drawn in chalk, since on a distribution the question is
 * nearly always where the weight of the data sits rather than how tall any one
 * bar is.
 */
@Composable
fun Histogram(
    bins: List<ChartPoint>,
    modifier: Modifier = Modifier,
    color: Color = Plate20,
    peakColor: Color = Chalk,
    height: Dp = 150.dp,
    valueFormatter: (Float) -> String = { it.toInt().toString() },
    description: String = "Distribution"
) {
    if (bins.isEmpty()) return
    val max = bins.maxOf { it.value }
    if (max <= 0f) return
    val peak = bins.indexOfFirst { it.value == max }
    val summary = "$description. " + bins.joinToString { "${it.label}: ${valueFormatter(it.value)}" }

    Column(modifier.semantics { contentDescription = summary }) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            val gap = 2.dp.toPx()
            val barWidth = ((size.width - gap * (bins.size - 1)) / bins.size).coerceAtLeast(2f)
            drawLine(Slate600, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            bins.forEachIndexed { index, bin ->
                val barHeight = (bin.value / max) * (size.height - 4.dp.toPx())
                if (barHeight <= 0f) return@forEachIndexed
                drawRoundRect(
                    color = if (index == peak) peakColor else color,
                    topLeft = Offset(index * (barWidth + gap), size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }
        }
        Spacer(Modifier.height(Space.xs))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            bins.forEach { bin ->
                Text(
                    bin.label,
                    style = NumericType.small,
                    color = ChalkFaint,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

/** One measure set against what it was over the previous period of equal length. */
data class Comparison(
    val label: String,
    val now: Float,
    val before: Float,
    val formattedNow: String,
    val formattedBefore: String
)

/**
 * Now against before, a pair of bars per measure.
 *
 * Both bars in a pair are scaled against the larger of the two, so the reading
 * is the difference in length rather than a number the reader has to hold in
 * their head. The change is still printed, because one bar being slightly
 * longer than another does not say "up four percent".
 */
@Composable
fun ComparisonBars(
    rows: List<Comparison>,
    modifier: Modifier = Modifier,
    nowColor: Color = Chalk,
    beforeColor: Color = Slate500,
    description: String = "This period against the one before it"
) {
    if (rows.isEmpty()) return
    val summary = "$description. " + rows.joinToString {
        "${it.label}: ${it.formattedNow}, previously ${it.formattedBefore}"
    }

    Column(
        modifier.semantics { contentDescription = summary },
        verticalArrangement = Arrangement.spacedBy(Space.md)
    ) {
        rows.forEach { row ->
            val scale = maxOf(row.now, row.before).takeIf { it > 0f } ?: 1f
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(row.label, style = MaterialTheme.typography.bodySmall, color = ChalkDim)
                    Text(
                        changeLabel(row.now, row.before),
                        style = NumericType.small,
                        color = changeColour(row.now, row.before)
                    )
                }
                Spacer(Modifier.height(Space.xs))
                ComparisonBar(row.now / scale, nowColor, row.formattedNow)
                Spacer(Modifier.height(3.dp))
                ComparisonBar(row.before / scale, beforeColor, row.formattedBefore)
            }
        }
        Row {
            LegendSwatch(nowColor, "Now")
            LegendSwatch(beforeColor, "Before")
        }
    }
}

@Composable
private fun ComparisonBar(fraction: Float, color: Color, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .height(14.dp)
                .background(Slate700, RoundedCornerShape(3.dp))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(14.dp)
                    .background(color, RoundedCornerShape(3.dp))
            )
        }
        Spacer(Modifier.width(Space.sm))
        Text(value, style = NumericType.small, color = ChalkDim)
    }
}

private fun changeLabel(now: Float, before: Float): String = when {
    before <= 0f && now <= 0f -> "no data"
    before <= 0f -> "new"
    else -> {
        val change = ((now - before) / before) * 100f
        when {
            change >= 0.5f -> "+${change.toInt()}%"
            change <= -0.5f -> "${change.toInt()}%"
            else -> "level"
        }
    }
}

/**
 * Green for more, amber for less. Not red: a lighter period is a training
 * decision as often as it is a failure, and the app does not know which.
 */
private fun changeColour(now: Float, before: Float): Color = when {
    before <= 0f -> ChalkDim
    now > before * 1.005f -> Plate10
    now < before * 0.995f -> Plate15
    else -> ChalkDim
}

/** One rung of a lift's progression: what was done, and when. */
data class FlowStep(val load: String, val detail: String, val date: String, val isBest: Boolean)

/**
 * How one lift moved, session by session, as a chain of steps.
 *
 * A line chart answers "is it going up". This answers "what did I actually
 * do" -- the weight and the repetitions at each rung, in order, with the best
 * session marked. It is the form a lifter's own notebook takes, and it reads
 * at a glance in a way that a point on an axis does not.
 */
@Composable
fun ProgressionFlow(
    steps: List<FlowStep>,
    modifier: Modifier = Modifier,
    description: String = "Progression"
) {
    if (steps.isEmpty()) return
    val summary = "$description. " + steps.joinToString {
        "${it.date}: ${it.load} for ${it.detail}"
    }

    Column(
        modifier.semantics { contentDescription = summary },
        verticalArrangement = Arrangement.spacedBy(Space.xs)
    ) {
        steps.forEachIndexed { index, step ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The rail: a dot per session, joined by the line between them,
                // so the sessions read as one run rather than as separate rows.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(if (step.isBest) 12.dp else 8.dp)
                            .background(
                                if (step.isBest) Chalk else Slate500,
                                RoundedCornerShape(50)
                            )
                    )
                    if (index < steps.lastIndex) {
                        Box(
                            Modifier
                                .width(2.dp)
                                .height(22.dp)
                                .background(Slate600)
                        )
                    }
                }
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(step.load, style = NumericType.setCell, color = Chalk)
                        Spacer(Modifier.width(Space.sm))
                        Text(step.detail, style = MaterialTheme.typography.bodySmall, color = ChalkDim)
                        if (step.isBest) {
                            Spacer(Modifier.width(Space.sm))
                            Text("best", style = MaterialTheme.typography.labelSmall, color = Plate15)
                        }
                    }
                    Text(step.date, style = MaterialTheme.typography.labelSmall, color = ChalkFaint)
                }
            }
            if (index < steps.lastIndex) Spacer(Modifier.height(Space.xs))
        }
    }
}
