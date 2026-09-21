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
    Oval(p(0.50f, 0.055f), 0.085f, 0.048f),                    // head
    Limb(p(0.50f, 0.085f), p(0.50f, 0.115f), 0.045f),          // neck
    // Torso: wide at the shoulders, in at the waist, out again at the hips.
    Blob(
        listOf(
            p(0.28f, 0.155f), p(0.72f, 0.155f), p(0.70f, 0.26f),
            p(0.64f, 0.36f), p(0.66f, 0.44f), p(0.34f, 0.44f),
            p(0.36f, 0.36f), p(0.30f, 0.26f)
        )
    ),
    Limb(p(0.30f, 0.17f), p(0.20f, 0.34f), 0.055f),            // left upper arm
    Limb(p(0.70f, 0.17f), p(0.80f, 0.34f), 0.055f),            // right upper arm
    Limb(p(0.20f, 0.34f), p(0.15f, 0.49f), 0.045f),            // left forearm
    Limb(p(0.80f, 0.34f), p(0.85f, 0.49f), 0.045f),            // right forearm
    Oval(p(0.135f, 0.525f), 0.045f, 0.022f),                   // left hand
    Oval(p(0.865f, 0.525f), 0.045f, 0.022f),                   // right hand
    Limb(p(0.41f, 0.44f), p(0.40f, 0.68f), 0.085f),            // left thigh
    Limb(p(0.59f, 0.44f), p(0.60f, 0.68f), 0.085f),            // right thigh
    Limb(p(0.40f, 0.68f), p(0.40f, 0.90f), 0.055f),            // left shin
    Limb(p(0.60f, 0.68f), p(0.60f, 0.90f), 0.055f),            // right shin
    Oval(p(0.385f, 0.925f), 0.055f, 0.022f),                   // left foot
    Oval(p(0.615f, 0.925f), 0.055f, 0.022f)                    // right foot
)

private val FRONT = listOf(
    region(
        MuscleGroup.TRAPS,
        Blob(listOf(p(0.44f, 0.118f), p(0.56f, 0.118f), p(0.645f, 0.172f), p(0.355f, 0.172f)))
    ),
    region(
        MuscleGroup.SHOULDERS,
        Oval(p(0.305f, 0.175f), 0.075f, 0.035f),
        Oval(p(0.695f, 0.175f), 0.075f, 0.035f)
    ),
    region(
        MuscleGroup.CHEST,
        // Pecs: square at the sternum, rounded under, tapering to the armpit.
        Blob(listOf(p(0.482f, 0.192f), p(0.478f, 0.258f), p(0.385f, 0.268f), p(0.338f, 0.222f), p(0.385f, 0.192f))),
        Blob(listOf(p(0.518f, 0.192f), p(0.522f, 0.258f), p(0.615f, 0.268f), p(0.662f, 0.222f), p(0.615f, 0.192f)))
    ),
    region(
        MuscleGroup.BICEPS,
        Limb(p(0.285f, 0.205f), p(0.215f, 0.315f), 0.042f),
        Limb(p(0.715f, 0.205f), p(0.785f, 0.315f), 0.042f)
    ),
    region(
        MuscleGroup.FOREARMS,
        Limb(p(0.20f, 0.345f), p(0.155f, 0.475f), 0.035f),
        Limb(p(0.80f, 0.345f), p(0.845f, 0.475f), 0.035f)
    ),
    region(
        MuscleGroup.ABS,
        Blob(listOf(p(0.44f, 0.265f), p(0.56f, 0.265f), p(0.575f, 0.35f), p(0.55f, 0.415f), p(0.45f, 0.415f), p(0.425f, 0.35f)))
    ),
    region(
        MuscleGroup.QUADS,
        // Teardrops: full at the hip, narrowing just above the knee.
        Blob(listOf(p(0.355f, 0.455f), p(0.455f, 0.455f), p(0.445f, 0.60f), p(0.415f, 0.665f), p(0.375f, 0.62f))),
        Blob(listOf(p(0.645f, 0.455f), p(0.545f, 0.455f), p(0.555f, 0.60f), p(0.585f, 0.665f), p(0.625f, 0.62f)))
    ),
    region(
        MuscleGroup.CALVES,
        Blob(listOf(p(0.36f, 0.70f), p(0.44f, 0.70f), p(0.43f, 0.80f), p(0.40f, 0.85f), p(0.37f, 0.80f))),
        Blob(listOf(p(0.64f, 0.70f), p(0.56f, 0.70f), p(0.57f, 0.80f), p(0.60f, 0.85f), p(0.63f, 0.80f)))
    )
)

private val BACK = listOf(
    region(
        MuscleGroup.TRAPS,
        // The kite shape between the neck, the shoulders and the mid-back.
        Blob(listOf(p(0.50f, 0.118f), p(0.625f, 0.172f), p(0.545f, 0.245f), p(0.50f, 0.27f), p(0.455f, 0.245f), p(0.375f, 0.172f)))
    ),
    region(
        MuscleGroup.SHOULDERS,
        Oval(p(0.305f, 0.175f), 0.075f, 0.035f),
        Oval(p(0.695f, 0.175f), 0.075f, 0.035f)
    ),
    region(
        MuscleGroup.LATS,
        // Wings: wide under the armpit, tapering into the waist.
        Blob(listOf(p(0.335f, 0.195f), p(0.462f, 0.285f), p(0.448f, 0.385f), p(0.352f, 0.335f), p(0.312f, 0.255f))),
        Blob(listOf(p(0.665f, 0.195f), p(0.538f, 0.285f), p(0.552f, 0.385f), p(0.648f, 0.335f), p(0.688f, 0.255f)))
    ),
    region(
        MuscleGroup.BACK,
        // The spinal column between the lats, down to the waist.
        Blob(listOf(p(0.465f, 0.305f), p(0.535f, 0.305f), p(0.55f, 0.40f), p(0.50f, 0.43f), p(0.45f, 0.40f)))
    ),
    region(
        MuscleGroup.TRICEPS,
        Limb(p(0.295f, 0.20f), p(0.225f, 0.325f), 0.042f),
        Limb(p(0.705f, 0.20f), p(0.775f, 0.325f), 0.042f)
    ),
    region(
        MuscleGroup.FOREARMS,
        Limb(p(0.20f, 0.345f), p(0.155f, 0.475f), 0.035f),
        Limb(p(0.80f, 0.345f), p(0.845f, 0.475f), 0.035f)
    ),
    region(
        MuscleGroup.GLUTES,
        Blob(listOf(p(0.36f, 0.435f), p(0.49f, 0.44f), p(0.49f, 0.52f), p(0.38f, 0.525f), p(0.335f, 0.48f))),
        Blob(listOf(p(0.64f, 0.435f), p(0.51f, 0.44f), p(0.51f, 0.52f), p(0.62f, 0.525f), p(0.665f, 0.48f)))
    ),
    region(
        MuscleGroup.HAMSTRINGS,
        Blob(listOf(p(0.365f, 0.535f), p(0.445f, 0.535f), p(0.44f, 0.63f), p(0.405f, 0.665f), p(0.37f, 0.62f))),
        Blob(listOf(p(0.635f, 0.535f), p(0.555f, 0.535f), p(0.56f, 0.63f), p(0.595f, 0.665f), p(0.63f, 0.62f)))
    ),
    region(
        MuscleGroup.CALVES,
        Blob(listOf(p(0.36f, 0.70f), p(0.44f, 0.70f), p(0.43f, 0.80f), p(0.40f, 0.85f), p(0.37f, 0.80f))),
        Blob(listOf(p(0.64f, 0.70f), p(0.56f, 0.70f), p(0.57f, 0.80f), p(0.60f, 0.85f), p(0.63f, 0.80f)))
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
