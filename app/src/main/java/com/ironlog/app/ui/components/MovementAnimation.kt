package com.ironlog.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ironlog.app.domain.usecase.MovementPattern
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.Plate20
import com.ironlog.app.ui.theme.Slate600
import com.ironlog.app.ui.theme.Space

/**
 * A figure performing the movement, looping between its start and end position.
 *
 * Drawn rather than filmed. Video or photography for two hundred exercises
 * would be tens of megabytes in an app that is otherwise a few, and would need
 * either a network call or a licence -- and this app is offline by design. A
 * figure also shows the pattern rather than a particular person's technique,
 * which is what a lifter needs to recognise the lift.
 *
 * Each pattern is two poses. The animation eases between them and back, which
 * is what a repetition is.
 */
@Composable
fun MovementAnimation(
    pattern: MovementPattern,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp
) {
    val transition = rememberInfiniteTransition(label = pattern.name)
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            // A repetition takes about as long as a real one, with a pause at
            // each end so the two positions can actually be read.
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "phase"
    )

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .semantics { contentDescription = "${pattern.label}. ${pattern.detail}" }
        ) {
            val scale = minOf(size.width / FIGURE_ASPECT, size.height)
            val figure = Size(scale * FIGURE_ASPECT, scale)
            val origin = Offset((size.width - figure.width) / 2f, (size.height - figure.height) / 2f)
            fun at(p: Offset) = Offset(origin.x + p.x * figure.width, origin.y + p.y * figure.height)

            val pose = posesFor(pattern).let { (start, end) -> start.blend(end, ease(phase)) }
            drawFigure(pose, figure.width, ::at)
        }
        Spacer(Modifier.height(Space.sm))
        Text(pattern.label, style = MaterialTheme.typography.titleMedium, color = Chalk)
        Text(pattern.detail, style = MaterialTheme.typography.bodySmall, color = ChalkDim)
    }
}

/** Smoothstep, so the figure accelerates out of each position rather than jerking. */
private fun ease(t: Float): Float = t * t * (3f - 2f * t)

private const val FIGURE_ASPECT = 0.62f

/**
 * A side-on figure: the joints that move, plus where the hands are holding the
 * weight. Coordinates run 0..1 across the drawing, y downwards.
 */
private data class Pose(
    val head: Offset,
    val shoulder: Offset,
    val hip: Offset,
    val knee: Offset,
    val ankle: Offset,
    val elbow: Offset,
    val hand: Offset,
    /** Draws a bar across the hands. Dumbbell and bodyweight work leaves it off. */
    val hasBar: Boolean = true
) {
    fun blend(other: Pose, t: Float) = Pose(
        head = lerp(head, other.head, t),
        shoulder = lerp(shoulder, other.shoulder, t),
        hip = lerp(hip, other.hip, t),
        knee = lerp(knee, other.knee, t),
        ankle = lerp(ankle, other.ankle, t),
        elbow = lerp(elbow, other.elbow, t),
        hand = lerp(hand, other.hand, t),
        hasBar = hasBar
    )
}

private fun lerp(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

private fun o(x: Float, y: Float) = Offset(x, y)

/**
 * Standing upright, seen from the side and facing right.
 *
 * The joints are deliberately not stacked on one vertical line. Anatomically
 * they nearly are, but drawn that way the figure collapses into a single
 * stroke with the arm hidden behind the torso, and nothing can be read. A
 * slight stagger -- shoulders behind the hips, arm in front of the body --
 * separates the limbs without misrepresenting the position.
 */
private val STANDING = Pose(
    head = o(0.47f, 0.09f),
    shoulder = o(0.46f, 0.23f),
    hip = o(0.50f, 0.50f),
    knee = o(0.48f, 0.70f),
    ankle = o(0.45f, 0.88f),
    elbow = o(0.53f, 0.37f),
    hand = o(0.55f, 0.50f),
    hasBar = false
)

/**
 * The two ends of one repetition for each pattern.
 *
 * The figure faces right, so forward is +x: in a squat the knees travel
 * forward and the hips back, and in a hinge the shoulders travel forward while
 * the hips go back. Ranges are drawn slightly larger than life, because a
 * movement shown at true scale in a 200dp box reads as a twitch.
 */
private fun posesFor(pattern: MovementPattern): Pair<Pose, Pose> = when (pattern) {
    MovementPattern.SQUAT -> STANDING.copy(
        // Bar racked across the shoulders.
        elbow = o(0.38f, 0.30f), hand = o(0.43f, 0.24f), hasBar = true
    ) to STANDING.copy(
        head = o(0.52f, 0.34f), shoulder = o(0.47f, 0.46f), hip = o(0.36f, 0.66f),
        knee = o(0.58f, 0.72f), ankle = o(0.45f, 0.88f),
        elbow = o(0.38f, 0.52f), hand = o(0.43f, 0.46f), hasBar = true
    )

    MovementPattern.HINGE -> STANDING.copy(
        elbow = o(0.53f, 0.37f), hand = o(0.55f, 0.52f), hasBar = true
    ) to STANDING.copy(
        // Shoulders forward and down, hips back, shins near vertical.
        head = o(0.70f, 0.36f), shoulder = o(0.62f, 0.42f), hip = o(0.36f, 0.52f),
        knee = o(0.44f, 0.71f), ankle = o(0.45f, 0.88f),
        elbow = o(0.62f, 0.58f), hand = o(0.62f, 0.74f), hasBar = true
    )

    MovementPattern.LUNGE -> STANDING.copy(
        elbow = o(0.40f, 0.32f), hand = o(0.44f, 0.26f), hasBar = true
    ) to STANDING.copy(
        // Front leg forward and bent, torso upright, back knee towards the floor.
        head = o(0.46f, 0.14f), shoulder = o(0.45f, 0.28f), hip = o(0.44f, 0.56f),
        knee = o(0.66f, 0.66f), ankle = o(0.66f, 0.88f),
        elbow = o(0.38f, 0.36f), hand = o(0.42f, 0.30f), hasBar = true
    )

    MovementPattern.HORIZONTAL_PRESS -> LYING.copy(
        // Bar down at the chest.
        elbow = o(0.28f, 0.52f), hand = o(0.36f, 0.50f)
    ) to LYING.copy(
        // Pressed to lockout, arms vertical.
        elbow = o(0.34f, 0.42f), hand = o(0.36f, 0.28f)
    )

    MovementPattern.VERTICAL_PRESS -> STANDING.copy(
        elbow = o(0.40f, 0.32f), hand = o(0.47f, 0.25f), hasBar = true
    ) to STANDING.copy(
        elbow = o(0.45f, 0.14f), hand = o(0.48f, 0.02f), hasBar = true
    )

    MovementPattern.HORIZONTAL_PULL -> BENT_OVER.copy(
        // Bar hanging at arm's length.
        elbow = o(0.62f, 0.58f), hand = o(0.62f, 0.74f)
    ) to BENT_OVER.copy(
        // Pulled to the ribs, elbow past the torso.
        elbow = o(0.50f, 0.56f), hand = o(0.58f, 0.52f)
    )

    MovementPattern.VERTICAL_PULL -> STANDING.copy(
        elbow = o(0.44f, 0.14f), hand = o(0.50f, 0.02f), hasBar = true
    ) to STANDING.copy(
        elbow = o(0.38f, 0.32f), hand = o(0.50f, 0.26f), hasBar = true
    )

    MovementPattern.CURL -> STANDING.copy(
        // Elbows stay put; only the forearm moves.
        elbow = o(0.53f, 0.37f), hand = o(0.55f, 0.52f), hasBar = true
    ) to STANDING.copy(
        elbow = o(0.53f, 0.37f), hand = o(0.49f, 0.27f), hasBar = true
    )

    MovementPattern.EXTENSION -> STANDING.copy(
        // Overhead, elbow fixed, forearm folded behind the head.
        elbow = o(0.45f, 0.14f), hand = o(0.36f, 0.24f), hasBar = true
    ) to STANDING.copy(
        elbow = o(0.45f, 0.14f), hand = o(0.47f, 0.02f), hasBar = true
    )

    MovementPattern.LATERAL_RAISE -> STANDING.copy(
        elbow = o(0.53f, 0.37f), hand = o(0.55f, 0.52f), hasBar = false
    ) to STANDING.copy(
        elbow = o(0.62f, 0.30f), hand = o(0.72f, 0.24f), hasBar = false
    )

    MovementPattern.SHRUG -> STANDING.copy(
        shoulder = o(0.46f, 0.25f), elbow = o(0.53f, 0.39f), hand = o(0.55f, 0.54f), hasBar = true
    ) to STANDING.copy(
        // Only the shoulders move; the arms stay long, which is the whole point.
        shoulder = o(0.46f, 0.17f), elbow = o(0.53f, 0.31f), hand = o(0.55f, 0.46f), hasBar = true
    )

    MovementPattern.CALF_RAISE -> STANDING.copy(
        hasBar = false
    ) to STANDING.copy(
        // The whole body rises as the heels come up.
        head = o(0.47f, 0.02f), shoulder = o(0.46f, 0.16f), hip = o(0.50f, 0.43f),
        knee = o(0.48f, 0.63f), ankle = o(0.45f, 0.81f),
        elbow = o(0.53f, 0.30f), hand = o(0.55f, 0.43f), hasBar = false
    )

    MovementPattern.CORE -> LYING.copy(
        head = o(0.20f, 0.56f), shoulder = o(0.34f, 0.58f),
        elbow = o(0.26f, 0.50f), hand = o(0.22f, 0.52f)
    ) to LYING.copy(
        // Ribs towards the hips: the shoulders curl up off the floor.
        head = o(0.30f, 0.42f), shoulder = o(0.40f, 0.50f),
        elbow = o(0.32f, 0.42f), hand = o(0.30f, 0.46f)
    )

    MovementPattern.CARRY -> STANDING.copy(
        elbow = o(0.53f, 0.37f), hand = o(0.55f, 0.52f), hasBar = true
    ) to STANDING.copy(
        // Barely moves: holding position under load is the exercise.
        head = o(0.47f, 0.10f), shoulder = o(0.46f, 0.24f),
        elbow = o(0.53f, 0.38f), hand = o(0.55f, 0.53f), hasBar = true
    )

    MovementPattern.CARDIO -> STANDING.copy(
        knee = o(0.58f, 0.66f), ankle = o(0.64f, 0.84f),
        elbow = o(0.40f, 0.34f), hand = o(0.36f, 0.26f), hasBar = false
    ) to STANDING.copy(
        knee = o(0.40f, 0.70f), ankle = o(0.34f, 0.86f),
        elbow = o(0.62f, 0.34f), hand = o(0.68f, 0.26f), hasBar = false
    )
}

/** On a bench: hips and shoulders level, knees bent, feet down. */
private val LYING = Pose(
    head = o(0.20f, 0.56f),
    shoulder = o(0.34f, 0.58f),
    hip = o(0.56f, 0.60f),
    knee = o(0.70f, 0.70f),
    ankle = o(0.72f, 0.87f),
    elbow = o(0.30f, 0.50f),
    hand = o(0.36f, 0.50f),
    hasBar = true
)

/** Hinged forward over a bar, the position every row starts from. */
private val BENT_OVER = Pose(
    head = o(0.70f, 0.36f),
    shoulder = o(0.62f, 0.42f),
    hip = o(0.36f, 0.52f),
    knee = o(0.44f, 0.71f),
    ankle = o(0.45f, 0.88f),
    elbow = o(0.62f, 0.58f),
    hand = o(0.62f, 0.74f),
    hasBar = true
)

// ------------------------------------------------------------------ rendering

/**
 * Draws the figure: limbs as strokes, joints left implicit, and the weight as a
 * bar across the hands where the movement holds one.
 */
private fun DrawScope.drawFigure(pose: Pose, figureWidth: Float, at: (Offset) -> Offset) {
    val limb = figureWidth * 0.075f
    val stroke = Stroke(width = limb, cap = StrokeCap.Round)

    fun bone(a: Offset, b: Offset, colour: Color = Chalk) {
        drawLine(colour, at(a), at(b), strokeWidth = stroke.width, cap = StrokeCap.Round)
    }

    // The floor, so the figure is standing rather than floating. Drawn from the
    // lowest point any pose reaches, not from the current one, or it would rise
    // and fall with the repetition.
    val floorY = at(o(0f, 0.93f)).y
    drawLine(Slate600, Offset(0f, floorY), Offset(size.width, floorY), strokeWidth = limb * 0.3f)

    // Torso and legs, then the arm on top, since the arm crosses the body.
    bone(pose.shoulder, pose.hip)
    bone(pose.hip, pose.knee)
    bone(pose.knee, pose.ankle)
    // Foot, pointing the way the figure faces.
    bone(pose.ankle, Offset(pose.ankle.x + 0.07f, 0.92f))
    bone(pose.shoulder, pose.elbow)
    bone(pose.elbow, pose.hand)

    drawCircle(Chalk, radius = figureWidth * 0.075f, center = at(pose.head))
    // Neck, so the head is attached rather than hovering.
    bone(pose.head, pose.shoulder)

    if (pose.hasBar) {
        val centre = at(pose.hand)
        val half = figureWidth * 0.22f
        drawLine(
            Plate20,
            Offset(centre.x - half, centre.y),
            Offset(centre.x + half, centre.y),
            strokeWidth = limb * 0.55f,
            cap = StrokeCap.Round
        )
        // Plates, so it reads as a loaded bar rather than a broom handle.
        listOf(-1f, 1f).forEach { side ->
            drawCircle(
                Plate20,
                radius = limb * 0.62f,
                center = Offset(centre.x + side * half, centre.y)
            )
        }
    } else {
        drawCircle(Slate600, radius = limb * 0.42f, center = at(pose.hand))
    }
}
