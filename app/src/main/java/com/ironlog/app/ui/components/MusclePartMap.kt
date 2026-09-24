package com.ironlog.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ironlog.app.data.entity.MusclePart
import com.ironlog.app.ui.theme.ChalkFaint
import com.ironlog.app.ui.theme.Slate600
import com.ironlog.app.ui.theme.Slate700
import com.ironlog.app.ui.theme.Slate800
import kotlin.math.hypot

/**
 * The body drawn at the level a lifter talks about it -- upper and lower
 * chest, three heads of the delt, the heads of the arms, adductors and
 * abductors -- each part shaded by how much it was worked, and each one
 * tappable.
 *
 * Shading is the user's chosen tint, light for a few sets and dark for
 * many, so a week reads at a glance: the dark parts are where the work
 * went. Everything else about a part -- sets, volume, which exercises --
 * is deliberately not on the figure. It appears when a part is pressed,
 * which keeps the card the size of a figure rather than a table.
 */
@Composable
fun MusclePartMap(
    intensity: Map<MusclePart, Float>,
    tint: Color,
    selected: MusclePart?,
    onPartClick: (MusclePart) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 360.dp
) {
    val worked = intensity.filterValues { it > 0f }.keys
    val description = if (worked.isEmpty()) {
        "Muscle map. Nothing trained this week yet."
    } else {
        "Muscle map. Trained: " + worked.joinToString { it.label } + ". Press a muscle for its sets."
    }
    fun shade(part: MusclePart): Color {
        val f = intensity[part] ?: 0f
        return if (f <= 0f) IDLE else lerp(lerp(tint, Color.White, 0.45f), lerp(tint, Color.Black, 0.35f), f)
    }

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(height)
                .semantics { contentDescription = description },
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PartFigure(FRONT_PARTS, "Front", selected, ::shade, onPartClick, Modifier.weight(1f))
            PartFigure(BACK_PARTS, "Back", selected, ::shade, onPartClick, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PartFigure(
    regions: List<PartRegion>,
    label: String,
    selected: MusclePart?,
    shade: (MusclePart) -> Color,
    onPartClick: (MusclePart) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(regions) {
                    detectTapGestures { tap ->
                        val frame = frameFor(Size(size.width.toFloat(), size.height.toFloat()))
                        // Back to figure units, then the last region drawn wins,
                        // as it is the one on top.
                        val p = Offset((tap.x - frame.left) / frame.width, (tap.y - frame.top) / frame.height)
                        regions.lastOrNull { region -> region.shapes.any { it.contains(p) } }
                            ?.let { onPartClick(it.part) }
                    }
                }
        ) {
            val frame = frameFor(size)
            fun at(p: Offset) = Offset(frame.left + p.x * frame.width, frame.top + p.y * frame.height)

            SILHOUETTE.forEach { drawPath(it.toPath(::at, frame.size), BODY) }
            regions.forEach { region ->
                val colour = shade(region.part)
                region.shapes.forEach { shape ->
                    val path = shape.toPath(::at, frame.size)
                    drawPath(path, colour)
                    drawPath(path, Slate800, style = Stroke(width = frame.width * OUTLINE))
                    if (region.part == selected) {
                        drawPath(path, Color.White, style = Stroke(width = frame.width * OUTLINE * 2.2f))
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = ChalkFaint)
    }
}

/** The figure's rectangle inside the canvas: its own proportions, centred. */
private fun frameFor(size: Size): Rect {
    val scale = minOf(size.width / ASPECT, size.height)
    val figure = Size(scale * ASPECT, scale)
    val origin = Offset((size.width - figure.width) / 2f, (size.height - figure.height) / 2f)
    return Rect(origin, figure)
}

private fun DrawScope.drawPath(path: Path, colour: Color) = drawPath(path = path, color = colour)

private val IDLE = lerp(Slate600, Color.White, 0.07f)
private val BODY = lerp(Slate700, Slate600, 0.8f)
private const val OUTLINE = 0.012f
private const val ASPECT = 0.46f

// ------------------------------------------------------------------- shapes

private sealed interface PartShape {
    fun toPath(at: (Offset) -> Offset, figure: Size): Path
    /** Whether [p], in figure units, is inside the shape. */
    fun contains(p: Offset): Boolean
}

/** A closed outline through [points], drawn smoothed; hit-tested as the polygon. */
private class PartBlob(val points: List<Offset>) : PartShape {
    override fun toPath(at: (Offset) -> Offset, figure: Size): Path = smoothClosed(points.map(at))
    override fun contains(p: Offset): Boolean {
        var inside = false
        var j = points.lastIndex
        for (i in points.indices) {
            val a = points[i]
            val b = points[j]
            if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
            j = i
        }
        return inside
    }
}

private class PartLimb(val from: Pt, val to: Pt, val halfWidth: Float) : PartShape {
    override fun toPath(at: (Offset) -> Offset, figure: Size): Path = capsule(at(from.o), at(to.o), halfWidth * figure.width)
    override fun contains(p: Offset): Boolean = false
}

private class PartOval(val centre: Pt, val rx: Float, val ry: Float) : PartShape {
    override fun toPath(at: (Offset) -> Offset, figure: Size): Path {
        val c = at(centre.o)
        return Path().apply {
            addOval(Rect(c.x - rx * figure.width, c.y - ry * figure.height, c.x + rx * figure.width, c.y + ry * figure.height))
        }
    }
    override fun contains(p: Offset): Boolean = false
}

private data class PartRegion(val part: MusclePart, val shapes: List<PartShape>)

private fun partRegion(part: MusclePart, vararg shapes: PartShape) = PartRegion(part, shapes.toList())
/** A figure-space point that is not an inline class, so it can travel in a vararg. */
private class Pt(val x: Float, val y: Float) { val o: Offset get() = Offset(x, y) }
private fun p(x: Float, y: Float) = Pt(x, y)
private fun blob(vararg pts: Pt) = PartBlob(pts.map { it.o })
private fun blobOf(pts: List<Pt>) = PartBlob(pts.map { it.o })

/** The same shape on the other side of the body. */
private fun PartBlob.mirrored() = PartBlob(points.map { Offset(1f - it.x, it.y) })
private fun pair(part: MusclePart, left: PartBlob) = partRegion(part, left, left.mirrored())

// --------------------------------------------------------------------- body

private val SILHOUETTE: List<PartShape> = listOf(
    PartOval(p(0.50f, 0.050f), 0.070f, 0.042f),
    PartLimb(p(0.50f, 0.078f), p(0.50f, 0.112f), 0.052f),
    blobOf(
        listOf(
            p(0.22f, 0.175f), p(0.32f, 0.140f), p(0.50f, 0.128f), p(0.68f, 0.140f),
            p(0.78f, 0.175f), p(0.74f, 0.255f), p(0.655f, 0.355f), p(0.675f, 0.435f),
            p(0.60f, 0.475f), p(0.40f, 0.475f), p(0.325f, 0.435f), p(0.345f, 0.355f),
            p(0.26f, 0.255f)
        )
    ),
    PartLimb(p(0.265f, 0.185f), p(0.185f, 0.345f), 0.066f),
    PartLimb(p(0.735f, 0.185f), p(0.815f, 0.345f), 0.066f),
    PartLimb(p(0.185f, 0.345f), p(0.135f, 0.50f), 0.052f),
    PartLimb(p(0.815f, 0.345f), p(0.865f, 0.50f), 0.052f),
    PartOval(p(0.125f, 0.535f), 0.042f, 0.024f),
    PartOval(p(0.875f, 0.535f), 0.042f, 0.024f),
    PartLimb(p(0.415f, 0.465f), p(0.395f, 0.665f), 0.098f),
    PartLimb(p(0.585f, 0.465f), p(0.605f, 0.665f), 0.098f),
    PartLimb(p(0.395f, 0.665f), p(0.385f, 0.885f), 0.062f),
    PartLimb(p(0.605f, 0.665f), p(0.615f, 0.885f), 0.062f),
    PartOval(p(0.375f, 0.920f), 0.052f, 0.022f),
    PartOval(p(0.625f, 0.920f), 0.052f, 0.022f)
)

// Shared between the two views.
private val NECK = blob(p(0.455f, 0.082f), p(0.545f, 0.082f), p(0.555f, 0.125f), p(0.445f, 0.125f))
private val FOREARM = blob(p(0.215f, 0.352f), p(0.165f, 0.40f), p(0.148f, 0.478f), p(0.185f, 0.485f), p(0.225f, 0.40f))
private val CALF = blob(p(0.352f, 0.685f), p(0.435f, 0.69f), p(0.425f, 0.78f), p(0.393f, 0.855f), p(0.362f, 0.78f))
// The deltoid cap, split into the part nearest the chest/back and the outer cap.
private val DELT_INNER = blob(p(0.32f, 0.142f), p(0.283f, 0.153f), p(0.262f, 0.24f), p(0.30f, 0.245f), p(0.345f, 0.192f))
private val DELT_OUTER = blob(p(0.283f, 0.153f), p(0.245f, 0.165f), p(0.225f, 0.235f), p(0.262f, 0.24f))

private val FRONT_PARTS: List<PartRegion> = listOf(
    partRegion(
        MusclePart.TRAPS,
        blob(p(0.50f, 0.108f), p(0.615f, 0.152f), p(0.66f, 0.178f), p(0.50f, 0.168f)),
        blob(p(0.50f, 0.108f), p(0.385f, 0.152f), p(0.34f, 0.178f), p(0.50f, 0.168f)),
        NECK
    ),
    pair(MusclePart.FRONT_DELT, DELT_INNER),
    pair(MusclePart.MIDDLE_DELT, DELT_OUTER),
    // The pec, cut across at the line of the nipple: clavicular head above,
    // sternal head below.
    pair(MusclePart.UPPER_CHEST, blob(p(0.488f, 0.182f), p(0.488f, 0.232f), p(0.395f, 0.236f), p(0.338f, 0.222f), p(0.352f, 0.186f))),
    pair(MusclePart.LOWER_CHEST, blob(p(0.488f, 0.236f), p(0.488f, 0.272f), p(0.405f, 0.288f), p(0.338f, 0.248f), p(0.395f, 0.24f))),
    // The biceps split along its length: the long head on the outside of the
    // arm, the short head on the inside.
    pair(MusclePart.BICEPS_LONG, blob(p(0.27f, 0.225f), p(0.245f, 0.245f), p(0.215f, 0.315f), p(0.235f, 0.325f))),
    pair(MusclePart.BICEPS_SHORT, blob(p(0.295f, 0.205f), p(0.27f, 0.225f), p(0.235f, 0.325f), p(0.255f, 0.335f), p(0.30f, 0.265f))),
    pair(MusclePart.FOREARMS, FOREARM),
    partRegion(MusclePart.ABDOMINALS, blob(p(0.445f, 0.288f), p(0.555f, 0.288f), p(0.565f, 0.36f), p(0.535f, 0.44f), p(0.465f, 0.44f), p(0.435f, 0.36f))),
    pair(MusclePart.OBLIQUES, blob(p(0.428f, 0.30f), p(0.40f, 0.355f), p(0.412f, 0.425f), p(0.452f, 0.40f), p(0.44f, 0.335f))),
    // Outer hip, where the abductors sit above the sweep of the quad.
    pair(MusclePart.ABDUCTORS, blob(p(0.33f, 0.44f), p(0.365f, 0.455f), p(0.352f, 0.483f), p(0.325f, 0.522f), p(0.312f, 0.48f))),
    pair(MusclePart.QUADS, blob(p(0.345f, 0.485f), p(0.455f, 0.49f), p(0.44f, 0.60f), p(0.405f, 0.655f), p(0.355f, 0.60f), p(0.325f, 0.53f))),
    pair(MusclePart.ADDUCTORS, blob(p(0.462f, 0.49f), p(0.475f, 0.60f), p(0.452f, 0.635f), p(0.432f, 0.56f))),
    pair(MusclePart.CALVES, CALF)
)

private val BACK_PARTS: List<PartRegion> = listOf(
    partRegion(
        MusclePart.TRAPS,
        blob(p(0.50f, 0.112f), p(0.645f, 0.165f), p(0.565f, 0.245f), p(0.50f, 0.27f), p(0.435f, 0.245f), p(0.355f, 0.165f)),
        NECK
    ),
    pair(MusclePart.REAR_DELT, DELT_INNER),
    pair(MusclePart.MIDDLE_DELT, DELT_OUTER),
    pair(MusclePart.LATS, blob(p(0.315f, 0.195f), p(0.455f, 0.29f), p(0.44f, 0.40f), p(0.365f, 0.375f), p(0.29f, 0.29f))),
    // The column between the lats: rhomboids and mid-back above, erectors below.
    partRegion(MusclePart.UPPER_BACK, blob(p(0.468f, 0.285f), p(0.532f, 0.285f), p(0.54f, 0.35f), p(0.46f, 0.35f))),
    partRegion(MusclePart.LOWER_BACK, blob(p(0.46f, 0.352f), p(0.54f, 0.352f), p(0.548f, 0.39f), p(0.50f, 0.425f), p(0.452f, 0.39f))),
    // The horseshoe in three: the lateral head down the outside, the long head
    // on the inside by the body, the medial head low by the elbow.
    pair(MusclePart.TRICEPS_LATERAL, blob(p(0.272f, 0.222f), p(0.245f, 0.245f), p(0.222f, 0.325f), p(0.242f, 0.332f))),
    pair(MusclePart.TRICEPS_LONG, blob(p(0.30f, 0.20f), p(0.272f, 0.222f), p(0.252f, 0.292f), p(0.284f, 0.30f), p(0.305f, 0.26f))),
    pair(MusclePart.TRICEPS_MEDIAL, blob(p(0.252f, 0.292f), p(0.242f, 0.332f), p(0.262f, 0.34f), p(0.284f, 0.30f))),
    pair(MusclePart.FOREARMS, FOREARM),
    pair(MusclePart.GLUTES, blob(p(0.355f, 0.43f), p(0.49f, 0.435f), p(0.49f, 0.53f), p(0.375f, 0.535f), p(0.325f, 0.485f))),
    pair(MusclePart.HAMSTRINGS, blob(p(0.355f, 0.545f), p(0.445f, 0.55f), p(0.435f, 0.63f), p(0.40f, 0.66f), p(0.355f, 0.62f))),
    pair(MusclePart.ADDUCTORS, blob(p(0.462f, 0.545f), p(0.475f, 0.62f), p(0.452f, 0.655f), p(0.435f, 0.58f))),
    pair(MusclePart.CALVES, CALF)
)

// ---------------------------------------------------------------- path making

private fun smoothClosed(points: List<Offset>): Path {
    val path = Path()
    if (points.size < 3) return path
    val n = points.size
    path.moveTo(points[0].x, points[0].y)
    for (i in 0 until n) {
        val p0 = points[(i - 1 + n) % n]
        val p1 = points[i]
        val p2 = points[(i + 1) % n]
        val p3 = points[(i + 2) % n]
        path.cubicTo(
            p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
            p2.x, p2.y
        )
    }
    path.close()
    return path
}

private fun capsule(from: Offset, to: Offset, halfWidth: Float): Path {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val length = hypot(dx, dy).takeIf { it > 0f } ?: return Path()
    val nx = -dy / length * halfWidth
    val ny = dx / length * halfWidth
    val ex = dx / length * halfWidth
    val ey = dy / length * halfWidth
    return Path().apply {
        moveTo(from.x + nx, from.y + ny)
        lineTo(to.x + nx, to.y + ny)
        cubicTo(to.x + nx + ex * 1.33f, to.y + ny + ey * 1.33f, to.x - nx + ex * 1.33f, to.y - ny + ey * 1.33f, to.x - nx, to.y - ny)
        lineTo(from.x - nx, from.y - ny)
        cubicTo(from.x - nx - ex * 1.33f, from.y - ny - ey * 1.33f, from.x + nx - ex * 1.33f, from.y + ny - ey * 1.33f, from.x + nx, from.y + ny)
        close()
    }
}
