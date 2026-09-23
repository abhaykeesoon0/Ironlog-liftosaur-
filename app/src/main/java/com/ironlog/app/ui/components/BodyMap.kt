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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ironlog.app.data.entity.MuscleGroup
import com.ironlog.app.ui.theme.ChalkFaint
import com.ironlog.app.ui.theme.Plate10
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.Plate15
import com.ironlog.app.ui.theme.Plate25
import com.ironlog.app.ui.theme.Slate600
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

/**
 * The colours a figure is drawn in, apart from the muscles being read.
 *
 * The recovery chart is a dark body on a dark card, so the shaded muscles
 * are the only bright thing. The training figure is the opposite: a light
 * body, as on a printed anatomy chart, with the worked muscles filled in a
 * colour of the user's choosing. Same anatomy, different paper.
 */
data class FigurePalette(val body: Color, val idle: Color, val outline: Color) {
    companion object {
        val DARK = FigurePalette(body = BODY, idle = IDLE_MUSCLE, outline = Slate800)
        val LIGHT = FigurePalette(
            body = Color(0xFFB9C2CB),
            idle = Color(0xFFD4DAE0),
            outline = Color(0xFF9AA5B1)
        )
    }
}

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
            Figure(FRONT, "Front", Modifier.weight(1f)) { shadeFor(readiness[it] ?: 1f) }
            Figure(BACK, "Back", Modifier.weight(1f)) { shadeFor(readiness[it] ?: 1f) }
        }
        Spacer(Modifier.height(Space.sm))
        Legend()
    }
}

/**
 * The same anatomy, shaded by the part a muscle plays in one exercise rather
 * than by how recovered it is.
 *
 * This is the reading an anatomy chart in a gym gives: a body in grey with the
 * muscles the lift works picked out in colour. The moving figure beside it
 * shows what the lift *is*; this shows what it is *for*, and the two together
 * are what a lifter choosing an exercise actually wants to know.
 */
@Composable
fun MuscleHighlight(
    primary: MuscleGroup?,
    secondary: Set<MuscleGroup>,
    modifier: Modifier = Modifier,
    height: Dp = 260.dp
) {
    val description = buildString {
        append("Muscles worked. ")
        primary?.let { append("Primary: ${it.label}. ") }
        if (secondary.isNotEmpty()) append("Also working: " + secondary.joinToString { it.label })
    }

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(height)
                .semantics { contentDescription = description },
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Figure(FRONT, "Front", Modifier.weight(1f)) { roleShade(it, primary, secondary) }
            Figure(BACK, "Back", Modifier.weight(1f)) { roleShade(it, primary, secondary) }
        }
        Spacer(Modifier.height(Space.sm))
        WorkedLegend(primary, secondary)
    }
}

/**
 * Red for the muscle doing the work, amber for the ones helping, and a muscle
 * the lift does not train is left the colour of the body it sits on -- present,
 * because an anatomy chart missing half its muscles looks broken, but quiet.
 */
private fun roleShade(muscle: MuscleGroup, primary: MuscleGroup?, secondary: Set<MuscleGroup>): Color =
    when {
        muscle == primary -> WORKED
        muscle in secondary -> ASSISTING
        else -> IDLE_MUSCLE
    }

@Composable
private fun WorkedLegend(primary: MuscleGroup?, secondary: Set<MuscleGroup>) {
    Column {
        primary?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Swatch(WORKED)
                Spacer(Modifier.width(Space.sm))
                Text(
                    "Primary: ${it.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ChalkDim
                )
            }
        }
        // Only the ones the figures can actually show. Cardio and full body have
        // no shape on the chart, and naming them under a colour that appears
        // nowhere is a key to an empty legend.
        val shown = secondary.filter { muscle -> (FRONT + BACK).any { it.muscle == muscle } }
        if (shown.isNotEmpty()) {
            Spacer(Modifier.height(Space.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Swatch(ASSISTING)
                Spacer(Modifier.width(Space.sm))
                Text(
                    "Also working: " + shown.joinToString { it.label },
                    style = MaterialTheme.typography.bodySmall,
                    color = ChalkDim
                )
            }
        }
    }
}

/**
 * The muscles a session has worked so far, on a light figure.
 *
 * Fills in as sets are completed, which is the point of having it on the
 * workout screen: a lifter mid-session can see what they have covered and
 * what they have not. A muscle an exercise trains hardest is filled solid;
 * one it merely uses is filled lighter, and the difference is what stops a
 * bench press from looking like a shoulder day.
 */
@Composable
fun TrainedMuscles(
    primary: Set<MuscleGroup>,
    secondary: Set<MuscleGroup>,
    tint: Color,
    modifier: Modifier = Modifier,
    height: Dp = 240.dp
) {
    val worked = (primary + secondary).filter { muscle -> (FRONT + BACK).any { it.muscle == muscle } }
    val description = if (worked.isEmpty()) {
        "Muscles trained. Nothing yet; complete a set and it fills in."
    } else {
        "Muscles trained so far: " + worked.joinToString { it.label }
    }
    val faint = tint.copy(alpha = 0.55f)
    fun shade(muscle: MuscleGroup): Color = when (muscle) {
        in primary -> tint
        in secondary -> faint
        else -> FigurePalette.LIGHT.idle
    }

    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Figure(FRONT, "Front", Modifier.weight(1f), FigurePalette.LIGHT, ::shade)
        Figure(BACK, "Back", Modifier.weight(1f), FigurePalette.LIGHT, ::shade)
    }
}

@Composable
private fun Figure(
    regions: List<Region>,
    label: String,
    modifier: Modifier = Modifier,
    palette: FigurePalette = FigurePalette.DARK,
    colourFor: (MuscleGroup) -> Color
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
            SILHOUETTE.forEach { shape -> drawPath(shape.toPath(::at, figure), palette.body) }

            regions.forEach { region ->
                val colour = colourFor(region.muscle)
                region.shapes.forEach { shape ->
                    val path = shape.toPath(::at, figure)
                    drawPath(path, colour)
                    if (colour != palette.idle && colour != palette.body) {
                        striate(path, colour, region.fibre, figure.width)
                    }
                    // A hairline in the card's own colour, so two muscles in the
                    // same state stay two muscles instead of merging into one
                    // green mass -- which is most of the figure on a rest day.
                    drawPath(
                        path = path,
                        color = palette.outline,
                        style = Stroke(width = figure.width * OUTLINE_WIDTH)
                    )
                }
            }
        }
        Spacer(Modifier.height(Space.xs))
        Text(label, style = MaterialTheme.typography.labelSmall, color = ChalkFaint)
    }
}

/**
 * Fibre lines inside a muscle, which is most of what separates an anatomy
 * drawing from a coloured-in outline.
 *
 * They run along the muscle's longer axis, because that is the way the fibres
 * run in nearly every muscle on the chart: across the chest for a pec, down
 * the arm for a biceps. Drawn in a darker shade of the muscle's own colour and
 * clipped to its outline, so they read as texture rather than as decoration,
 * and they never appear on a muscle the exercise does not work.
 */
private fun DrawScope.striate(path: Path, colour: Color, fibre: Fibre, figureWidth: Float) {

    val bounds = path.getBounds()
    if (bounds.width <= 0f || bounds.height <= 0f) return

    val horizontal = when (fibre) {
        Fibre.ACROSS -> true
        Fibre.DOWN -> false
        Fibre.LONG_AXIS -> bounds.width >= bounds.height
    }

    val shade = lerp(colour, Color.Black, 0.30f)
    val width = figureWidth * 0.007f
    // Five gaps, four lines, whatever the muscle's size: a pec and a calf then
    // carry the same density of texture rather than the big one looking hatched.
    val step = (if (horizontal) bounds.height else bounds.width) / 5f

    clipPath(path) {
        for (i in 1..4) {
            if (horizontal) {
                val y = bounds.top + step * i
                drawLine(shade, Offset(bounds.left, y), Offset(bounds.right, y), strokeWidth = width)
            } else {
                val x = bounds.left + step * i
                drawLine(shade, Offset(x, bounds.top), Offset(x, bounds.bottom), strokeWidth = width)
            }
        }
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
        Text("Sore", style = MaterialTheme.typography.labelSmall, color = ChalkFaint)
        Spacer(Modifier.width(Space.md))
        Swatch(HALFWAY)
        Spacer(Modifier.width(Space.xs))
        Text("Halfway", style = MaterialTheme.typography.labelSmall, color = ChalkFaint)
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
private val READY = lerp(Plate10, Color.Black, 0.22f)

/** Halfway: olive, where amber turns towards green. */
private val HALFWAY = lerp(Plate15, Plate10, 0.55f)

/** Just trained: pale amber, so a sore muscle is the thing the eye lands on. */
private val RECOVERING = lerp(Plate15, Color.White, 0.42f)

/**
 * The colour for a muscle at [fraction] recovered.
 *
 * Amber to olive to green, rather than one hue fading: a traffic light is a
 * reading everyone already knows, and the middle of recovery is a real state --
 * trainable, but not fresh -- that a single fading hue cannot show. Still
 * light when sore and dark when ready, so the figure is readable even to
 * someone who cannot separate red from green.
 */
private fun shadeFor(fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return if (f < 0.5f) lerp(RECOVERING, HALFWAY, f * 2f) else lerp(HALFWAY, READY, (f - 0.5f) * 2f)
}

/** The muscle a lift trains hardest. */
private val WORKED = Plate25

/** The ones helping. Amber rather than a faded red, so the two never blur. */
private val ASSISTING = Plate15

/**
 * A muscle this lift does not train: barely lighter than the body, so the
 * anatomy is still legible without competing with the muscles that matter.
 */
private val IDLE_MUSCLE = lerp(Slate600, Color.White, 0.07f)

/** Separating line between muscles, as a fraction of the figure's width. */
private const val OUTLINE_WIDTH = 0.012f

/**
 * The untracked body: head, hands, feet and the frame under the muscles. Light
 * enough to read as a body rather than a hole in the card, dark enough that the
 * muscles stay the thing being read.
 */
private val BODY = lerp(Slate700, Slate600, 0.8f)

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

/**
 * Which way a muscle's fibres run, for the striations drawn inside it.
 *
 * Most muscles pull along their own length, so the long axis of the shape is
 * the right guess. The chest and the glutes are the exceptions that matter
 * here: both are drawn taller than they are wide, and both pull across.
 */
private enum class Fibre { LONG_AXIS, ACROSS, DOWN }

private data class Region(
    val muscle: MuscleGroup,
    val shapes: List<Shape>,
    val fibre: Fibre = Fibre.LONG_AXIS
)

private fun region(muscle: MuscleGroup, vararg shapes: Shape, fibre: Fibre = Fibre.LONG_AXIS) =
    Region(muscle, shapes.toList(), fibre)

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
        Blob(listOf(p(0.512f, 0.182f), p(0.512f, 0.272f), p(0.595f, 0.288f), p(0.665f, 0.245f), p(0.648f, 0.186f))),
        fibre = Fibre.ACROSS
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
        // The column down the middle...
        Blob(listOf(p(0.445f, 0.288f), p(0.555f, 0.288f), p(0.565f, 0.36f), p(0.535f, 0.44f), p(0.465f, 0.44f), p(0.435f, 0.36f))),
        // ...and the obliques flanking it, which is most of what a waist is.
        Blob(listOf(p(0.428f, 0.30f), p(0.40f, 0.355f), p(0.412f, 0.425f), p(0.452f, 0.40f), p(0.44f, 0.335f))),
        Blob(listOf(p(0.572f, 0.30f), p(0.60f, 0.355f), p(0.588f, 0.425f), p(0.548f, 0.40f), p(0.56f, 0.335f))),
        // The rectus abdominis is banded across, not striped down it.
        fibre = Fibre.ACROSS
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
    ),
    region(
        // The dataset files adductors under the hip musculature, so the inner
        // thigh is shaded with the glutes rather than left as bare frame.
        MuscleGroup.GLUTES,
        Blob(listOf(p(0.462f, 0.49f), p(0.475f, 0.60f), p(0.452f, 0.635f), p(0.432f, 0.56f))),
        Blob(listOf(p(0.538f, 0.49f), p(0.525f, 0.60f), p(0.548f, 0.635f), p(0.568f, 0.56f)))
    ),
    region(
        // The neck runs into the traps, and leaving it bare left a grey collar
        // between the head and the shoulders.
        MuscleGroup.TRAPS,
        Blob(listOf(p(0.455f, 0.082f), p(0.545f, 0.082f), p(0.555f, 0.125f), p(0.445f, 0.125f)))
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
        Blob(listOf(p(0.645f, 0.43f), p(0.51f, 0.435f), p(0.51f, 0.53f), p(0.625f, 0.535f), p(0.675f, 0.485f))),
        fibre = Fibre.ACROSS
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
    ),
    region(
        MuscleGroup.TRAPS,
        Blob(listOf(p(0.455f, 0.082f), p(0.545f, 0.082f), p(0.555f, 0.125f), p(0.445f, 0.125f)))
    ),
    region(
        MuscleGroup.HAMSTRINGS,
        // The inner head of the hamstring, which the outer shape left bare.
        Blob(listOf(p(0.462f, 0.545f), p(0.475f, 0.62f), p(0.452f, 0.655f), p(0.435f, 0.58f))),
        Blob(listOf(p(0.538f, 0.545f), p(0.525f, 0.62f), p(0.548f, 0.655f), p(0.565f, 0.58f)))
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
