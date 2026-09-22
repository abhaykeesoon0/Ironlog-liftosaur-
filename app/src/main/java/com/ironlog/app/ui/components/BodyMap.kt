package com.ironlog.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ironlog.app.data.entity.MuscleGroup
import com.ironlog.app.ui.theme.ChalkFaint
import com.ironlog.app.ui.theme.Plate10
import com.ironlog.app.ui.theme.Slate700
import com.ironlog.app.ui.theme.Slate800
import com.ironlog.app.ui.theme.Space

/**
 * Front and back body maps, shaded by how recovered each muscle is.
 *
 * A muscle ready to train again is drawn in deep green and recedes into the
 * figure; one still recovering is pale, so what needs leaving alone is what
 * catches the eye. Colour here is data -- the shade *is* the reading -- which
 * is the use the plate palette is reserved for.
 *
 * The figures are drawn as smooth paths on a Canvas rather than shipped as
 * artwork: they scale to any width without a second asset, cost nothing in the
 * APK, and can be recoloured per muscle, which an image cannot. Muscle shapes
 * are built from a handful of points run through a Catmull-Rom spline, so the
 * outlines curve like a body instead of stacking rectangles.
 */

/** A muscle's readiness, where 1 is fully recovered and 0 is just trained. */
typealias Readiness = Map<MuscleGroup, Float>

@Composable
fun BodyMap(
    readiness: Readiness,
    modifier: Modifier = Modifier,
    height: Dp = 300.dp
) {
    val recovering = readiness.filterValues { it < 1f }
    val description = if (recovering.isEmpty()) {
        "Body map. Every muscle is recovered and ready to train."
    } else {
        "Body map. Still recovering: " + recovering.entries
            .sortedBy { it.value }
            .joinToString { "${it.key.label} at ${(it.value * 100).toInt()} percent" }
    }

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(height)
                .semantics { contentDescription = description },
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Figure(readiness, FRONT, "Front", Modifier.weight(1f))
            Figure(readiness, BACK, "Back", Modifier.weight(1f))
        }
        Spacer(Modifier.height(Space.sm))
        Legend()
    }
}

@Composable
private fun Figure(
    readiness: Readiness,
    regions: List<Region>,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            // Keep the figure's proportions whatever width the card gives us,
            // and centre it in whatever space is left over.
            val scale = minOf(size.width / FIGURE_ASPECT, size.height)
            val figure = Size(scale * FIGURE_ASPECT, scale)
            val origin = Offset((size.width - figure.width) / 2f, (size.height - figure.height) / 2f)

            fun at(p: Offset) = Offset(origin.x + p.x * figure.width, origin.y + p.y * figure.height)

            // The body first, so the parts we do not track -- head, hands, feet
            // -- still read as a person rather than as floating muscle.
            SILHOUETTE.forEach { shape -> drawPath(shape.toPath(::at, figure), Slate700) }

            regions.forEach { region ->
                val ready = readiness[region.muscle] ?: 1f
                // Pale when just trained, deep green once ready.
                val colour = lerp(RECOVERING, READY, ready.coerceIn(0f, 1f))
                region.shapes.forEach { shape ->
                    val path = shape.toPath(::at, figure)
                    drawPath(path, colour)
                    // A hairline in the card's own colour, so two muscles in the
                    // same state stay two muscles instead of merging into one
                    // green mass -- which is most of the figure on a rest day.
                    drawPath(
                        path = path,
                        color = Slate800,
                        style = Stroke(width = figure.width * OUTLINE_WIDTH)
                    )
                }
            }
        }
        Spacer(Modifier.height(Space.xs))
        Text(label, style = MaterialTheme.typography.labelSmall, color = ChalkFaint)
    }
}

@Composable
private fun Legend() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Swatch(RECOVERING)
        Spacer(Modifier.width(Space.xs))
        Text("Recovering", style = MaterialTheme.typography.labelSmall, color = ChalkFaint)
        Spacer(Modifier.width(Space.md))
        Swatch(READY)
        Spacer(Modifier.width(Space.xs))
        Text("Ready", style = MaterialTheme.typography.labelSmall, color = ChalkFaint)
    }
}

@Composable
private fun Swatch(colour: Color) {
    Spacer(Modifier.size(10.dp).background(colour, RoundedCornerShape(2.dp)))
}

/** Ready to train: deep green, sitting back into the figure. */
private val READY = lerp(Plate10, Color.Black, 0.30f)

/** Just trained: the same hue washed pale, so a sore muscle is the thing you see. */
private val RECOVERING = lerp(Plate10, Color.White, 0.62f)

/** Separating line between muscles, as a fraction of the figure's width. */
private const val OUTLINE_WIDTH = 0.012f

/** Width ÷ height of one figure. */
private const val FIGURE_ASPECT = 0.46f

// --------------------------------------------------------------------- shapes

/**
 * A muscle or body part. Either a closed outline smoothed into curves, or a
 * capsule along a limb, which is what an arm or a shin actually looks like.
 */
private sealed interface Shape {
    fun toPath(at: (Offset) -> Offset, figure: Size): Path
}

/** A closed shape through [points], smoothed so the outline curves. */
private class Blob(val points: List<Offset>) : Shape {
    override fun toPath(at: (Offset) -> Offset, figure: Size): Path = smoothClosedPath(points.map(at))
}

/** A rounded bar from [from] to [to], [halfWidth] wide in figure-x units. */
private class Limb(val from: Offset, val to: Offset, val halfWidth: Float) : Shape {
    override fun toPath(at: (Offset) -> Offset, figure: Size): Path =
        capsulePath(at(from), at(to), halfWidth * figure.width)
}

/** An oval centred on [centre], radii in figure units. */
private class Oval(val centre: Offset, val rx: Float, val ry: Float) : Shape {
    override fun toPath(at: (Offset) -> Offset, figure: Size): Path {
        val c = at(centre)
        return Path().apply {
            addOval(
                androidx.compose.ui.geometry.Rect(
                    center = c,
                    radius = 1f
                ).let {
                    androidx.compose.ui.geometry.Rect(
                        left = c.x - rx * figure.width,
                        top = c.y - ry * figure.height,
                        right = c.x + rx * figure.width,
                        bottom = c.y + ry * figure.height
                    )
                }
            )
        }
    }
}

private data class Region(val muscle: MuscleGroup, val shapes: List<Shape>)

private fun region(muscle: MuscleGroup, vararg shapes: Shape) = Region(muscle, shapes.toList())

private fun p(x: Float, y: Float) = Offset(x, y)

// ------------------------------------------------------------------ the body

/**
 * Head, neck, torso, limbs and feet. Drawn in slate underneath the muscles so
 * the coloured parts sit on a body rather than floating.
 */
private val SILHOUETTE = listOf(
    Oval(p(0.50f, 0.050f), 0.070f, 0.042f),                    // head
    // Short, thick neck running into the traps, as a trained figure has.
    Limb(p(0.50f, 0.078f), p(0.50f, 0.112f), 0.052f),
    // Torso: a V. Wide across the deltoids, drawn in hard at the waist, out
    // again across the hips. The taper is what makes a figure read as trained
    // rather than as a gingerbread man.
    Blob(
        listOf(
            p(0.22f, 0.175f), p(0.32f, 0.140f), p(0.50f, 0.128f), p(0.68f, 0.140f),
            p(0.78f, 0.175f), p(0.74f, 0.255f), p(0.655f, 0.355f), p(0.675f, 0.435f),
            p(0.60f, 0.475f), p(0.40f, 0.475f), p(0.325f, 0.435f), p(0.345f, 0.355f),
            p(0.26f, 0.255f)
        )
    ),
    // Arms hang slightly away from the body, as they do over a built back.
    Limb(p(0.265f, 0.185f), p(0.185f, 0.345f), 0.066f),        // left upper arm
    Limb(p(0.735f, 0.185f), p(0.815f, 0.345f), 0.066f),        // right upper arm
    Limb(p(0.185f, 0.345f), p(0.135f, 0.50f), 0.052f),         // left forearm
    Limb(p(0.815f, 0.345f), p(0.865f, 0.50f), 0.052f),         // right forearm
    Oval(p(0.125f, 0.535f), 0.042f, 0.024f),                   // left hand
    Oval(p(0.875f, 0.535f), 0.042f, 0.024f),                   // right hand
    // Thighs thick at the hip, tapering into the knee.
    Limb(p(0.415f, 0.465f), p(0.395f, 0.665f), 0.098f),        // left thigh
    Limb(p(0.585f, 0.465f), p(0.605f, 0.665f), 0.098f),        // right thigh
    Limb(p(0.395f, 0.665f), p(0.385f, 0.885f), 0.062f),        // left shin
    Limb(p(0.605f, 0.665f), p(0.615f, 0.885f), 0.062f),        // right shin
    Oval(p(0.375f, 0.920f), 0.052f, 0.022f),                   // left foot
    Oval(p(0.625f, 0.920f), 0.052f, 0.022f)                    // right foot
)

private val FRONT = listOf(
    region(
        MuscleGroup.TRAPS,
        // Sloping from the neck onto each shoulder.
        Blob(listOf(p(0.50f, 0.108f), p(0.615f, 0.152f), p(0.66f, 0.178f), p(0.50f, 0.168f))),
        Blob(listOf(p(0.50f, 0.108f), p(0.385f, 0.152f), p(0.34f, 0.178f), p(0.50f, 0.168f)))
    ),
    region(
        MuscleGroup.SHOULDERS,
        // Deltoid caps, rounded over the top of the arm.
        Blob(listOf(p(0.32f, 0.142f), p(0.245f, 0.165f), p(0.225f, 0.235f), p(0.30f, 0.245f), p(0.345f, 0.192f))),
        Blob(listOf(p(0.68f, 0.142f), p(0.755f, 0.165f), p(0.775f, 0.235f), p(0.70f, 0.245f), p(0.655f, 0.192f)))
    ),
    region(
        MuscleGroup.CHEST,
        // Pecs: square at the sternum, full and rounded underneath, with a
        // clear split down the middle.
        Blob(listOf(p(0.488f, 0.182f), p(0.488f, 0.272f), p(0.405f, 0.288f), p(0.335f, 0.245f), p(0.352f, 0.186f))),
        Blob(listOf(p(0.512f, 0.182f), p(0.512f, 0.272f), p(0.595f, 0.288f), p(0.665f, 0.245f), p(0.648f, 0.186f)))
    ),
    region(
        MuscleGroup.BICEPS,
        // A peak rather than a bar: thick at the middle, tapering at both ends.
        Blob(listOf(p(0.295f, 0.205f), p(0.245f, 0.245f), p(0.215f, 0.315f), p(0.255f, 0.335f), p(0.30f, 0.265f))),
        Blob(listOf(p(0.705f, 0.205f), p(0.755f, 0.245f), p(0.785f, 0.315f), p(0.745f, 0.335f), p(0.70f, 0.265f)))
    ),
    region(
        MuscleGroup.FOREARMS,
        Blob(listOf(p(0.215f, 0.352f), p(0.165f, 0.40f), p(0.148f, 0.478f), p(0.185f, 0.485f), p(0.225f, 0.40f))),
        Blob(listOf(p(0.785f, 0.352f), p(0.835f, 0.40f), p(0.852f, 0.478f), p(0.815f, 0.485f), p(0.775f, 0.40f)))
    ),
    region(
        MuscleGroup.ABS,
        // A narrow column between the obliques, tapering towards the navel.
        Blob(listOf(p(0.445f, 0.288f), p(0.555f, 0.288f), p(0.565f, 0.36f), p(0.535f, 0.44f), p(0.465f, 0.44f), p(0.435f, 0.36f)))
    ),
    region(
        MuscleGroup.QUADS,
        // The sweep: widest on the outside of the thigh, narrowing to the knee.
        Blob(listOf(p(0.345f, 0.485f), p(0.455f, 0.49f), p(0.44f, 0.60f), p(0.405f, 0.655f), p(0.355f, 0.60f), p(0.325f, 0.53f))),
        Blob(listOf(p(0.655f, 0.485f), p(0.545f, 0.49f), p(0.56f, 0.60f), p(0.595f, 0.655f), p(0.645f, 0.60f), p(0.675f, 0.53f)))
    ),
    region(
        MuscleGroup.CALVES,
        // Full at the top, tapering into the ankle.
        Blob(listOf(p(0.352f, 0.685f), p(0.435f, 0.69f), p(0.425f, 0.78f), p(0.393f, 0.855f), p(0.362f, 0.78f))),
        Blob(listOf(p(0.648f, 0.685f), p(0.565f, 0.69f), p(0.575f, 0.78f), p(0.607f, 0.855f), p(0.638f, 0.78f)))
    )
)

private val BACK = listOf(
    region(
        MuscleGroup.TRAPS,
        // The diamond between the neck, the shoulders and the mid-back.
        Blob(listOf(p(0.50f, 0.112f), p(0.645f, 0.165f), p(0.565f, 0.245f), p(0.50f, 0.27f), p(0.435f, 0.245f), p(0.355f, 0.165f)))
    ),
    region(
        MuscleGroup.SHOULDERS,
        Blob(listOf(p(0.32f, 0.142f), p(0.245f, 0.165f), p(0.225f, 0.235f), p(0.30f, 0.245f), p(0.345f, 0.192f))),
        Blob(listOf(p(0.68f, 0.142f), p(0.755f, 0.165f), p(0.775f, 0.235f), p(0.70f, 0.245f), p(0.655f, 0.192f)))
    ),
    region(
        MuscleGroup.LATS,
        // Wings: wide under the armpit, sweeping down into the waist. These are
        // what give the back its V, so they are drawn generously.
        Blob(listOf(p(0.315f, 0.195f), p(0.455f, 0.29f), p(0.44f, 0.40f), p(0.365f, 0.375f), p(0.29f, 0.29f))),
        Blob(listOf(p(0.685f, 0.195f), p(0.545f, 0.29f), p(0.56f, 0.40f), p(0.635f, 0.375f), p(0.71f, 0.29f)))
    ),
    region(
        MuscleGroup.BACK,
        // The spinal column between the lats, down to the waist.
        Blob(listOf(p(0.468f, 0.285f), p(0.532f, 0.285f), p(0.548f, 0.39f), p(0.50f, 0.425f), p(0.452f, 0.39f)))
    ),
    region(
        MuscleGroup.TRICEPS,
        // The horseshoe: thick at the shoulder, tapering to the elbow.
        Blob(listOf(p(0.30f, 0.20f), p(0.245f, 0.245f), p(0.222f, 0.325f), p(0.262f, 0.34f), p(0.305f, 0.26f))),
        Blob(listOf(p(0.70f, 0.20f), p(0.755f, 0.245f), p(0.778f, 0.325f), p(0.738f, 0.34f), p(0.695f, 0.26f)))
    ),
    region(
        MuscleGroup.FOREARMS,
        Blob(listOf(p(0.215f, 0.352f), p(0.165f, 0.40f), p(0.148f, 0.478f), p(0.185f, 0.485f), p(0.225f, 0.40f))),
        Blob(listOf(p(0.785f, 0.352f), p(0.835f, 0.40f), p(0.852f, 0.478f), p(0.815f, 0.485f), p(0.775f, 0.40f)))
    ),
    region(
        MuscleGroup.GLUTES,
        Blob(listOf(p(0.355f, 0.43f), p(0.49f, 0.435f), p(0.49f, 0.53f), p(0.375f, 0.535f), p(0.325f, 0.485f))),
        Blob(listOf(p(0.645f, 0.43f), p(0.51f, 0.435f), p(0.51f, 0.53f), p(0.625f, 0.535f), p(0.675f, 0.485f)))
    ),
    region(
        MuscleGroup.HAMSTRINGS,
        Blob(listOf(p(0.355f, 0.545f), p(0.445f, 0.55f), p(0.435f, 0.63f), p(0.40f, 0.66f), p(0.355f, 0.62f))),
        Blob(listOf(p(0.645f, 0.545f), p(0.555f, 0.55f), p(0.565f, 0.63f), p(0.60f, 0.66f), p(0.645f, 0.62f)))
    ),
    region(
        MuscleGroup.CALVES,
        Blob(listOf(p(0.352f, 0.685f), p(0.435f, 0.69f), p(0.425f, 0.78f), p(0.393f, 0.855f), p(0.362f, 0.78f))),
        Blob(listOf(p(0.648f, 0.685f), p(0.565f, 0.69f), p(0.575f, 0.78f), p(0.607f, 0.855f), p(0.638f, 0.78f)))
    )
)

// ---------------------------------------------------------------- path making

/**
 * A closed curve through every point, using Catmull-Rom converted to cubic
 * Béziers. Going through the points rather than near them means the shapes can
 * be described by where the muscle's edges actually are.
 */
private fun smoothClosedPath(points: List<Offset>): Path {
    val path = Path()
    if (points.size < 3) return path
    val n = points.size
    path.moveTo(points[0].x, points[0].y)
    for (i in 0 until n) {
        val p0 = points[(i - 1 + n) % n]
        val p1 = points[i]
        val p2 = points[(i + 1) % n]
        val p3 = points[(i + 2) % n]
        // The sixth is the standard Catmull-Rom to Bézier tangent scale.
        path.cubicTo(
            p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
            p2.x, p2.y
        )
    }
    path.close()
    return path
}

/** A bar with round ends, which is what a limb segment looks like. */
private fun capsulePath(from: Offset, to: Offset, halfWidth: Float): Path {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val length = kotlin.math.hypot(dx, dy).takeIf { it > 0f } ?: return Path()
    // Unit vector perpendicular to the limb, to offset each side by the width.
    val nx = -dy / length * halfWidth
    val ny = dx / length * halfWidth
    // Cap control points run along the limb's own direction.
    val ex = dx / length * halfWidth
    val ey = dy / length * halfWidth

    return Path().apply {
        moveTo(from.x + nx, from.y + ny)
        lineTo(to.x + nx, to.y + ny)
        cubicTo(
            to.x + nx + ex * 1.33f, to.y + ny + ey * 1.33f,
            to.x - nx + ex * 1.33f, to.y - ny + ey * 1.33f,
            to.x - nx, to.y - ny
        )
        lineTo(from.x - nx, from.y - ny)
        cubicTo(
            from.x - nx - ex * 1.33f, from.y - ny - ey * 1.33f,
            from.x + nx - ex * 1.33f, from.y + ny - ey * 1.33f,
            from.x + nx, from.y + ny
        )
        close()
    }
}

/** Kept explicit so the drawing code above reads as drawing, not plumbing. */
private fun DrawScope.drawPath(path: Path, colour: Color) = drawPath(path = path, color = colour)
