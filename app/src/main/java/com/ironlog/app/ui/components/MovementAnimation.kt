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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ironlog.app.domain.usecase.MovementPattern
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import com.ironlog.app.data.entity.MuscleGroup
import com.ironlog.app.ui.theme.Plate15
import com.ironlog.app.ui.theme.Plate25
import androidx.compose.ui.graphics.Path
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
    height: Dp = 220.dp,
    /** The muscle the lift trains hardest, marked in red on the figure. */
    primaryMuscle: MuscleGroup? = null,
    /** The ones helping, marked in amber. */
    secondaryMuscles: Set<MuscleGroup> = emptySet(),
    /** Names the marked muscles underneath. Off where something else names them. */
    showKey: Boolean = true
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
            drawFigure(pose, figure.width, primaryMuscle, secondaryMuscles, ::at)
        }
        Spacer(Modifier.height(Space.sm))
        Text(pattern.label, style = MaterialTheme.typography.titleMedium, color = Chalk)
        Text(pattern.detail, style = MaterialTheme.typography.bodySmall, color = ChalkDim)

        if (showKey && (primaryMuscle != null || secondaryMuscles.isNotEmpty())) {
            Spacer(Modifier.height(Space.sm))
            MuscleKey(primaryMuscle, secondaryMuscles)
        }
    }
}

/**
 * Names the marked muscles.
 *
 * The figure alone cannot say which patch is which -- a dot on the torso could
 * be a pec or a lat -- so the colours are spelled out underneath rather than
 * left to be guessed.
 */
@Composable
private fun MuscleKey(primary: MuscleGroup?, secondary: Set<MuscleGroup>) {
    Column {
        primary?.let {
            MuscleLine(PRIMARY_COLOUR, "Primary", it.label)
        }
        // Only the ones the figure can actually show: cardio and full body have
        // no place to mark, and listing them under a colour nothing carries
        // would be a key to an empty legend.
        val shown = secondary.filter { it in MARKERS.keys }
        if (shown.isNotEmpty()) {
            Spacer(Modifier.height(Space.xs))
            MuscleLine(SECONDARY_COLOUR, "Also working", shown.joinToString(", ") { it.label })
        }
    }
}

@Composable
private fun MuscleLine(colour: Color, role: String, names: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            Modifier
                .size(10.dp)
                .background(colour, androidx.compose.foundation.shape.CircleShape)
        )
        Spacer(Modifier.width(Space.sm))
        Text(
            "$role: $names",
            style = MaterialTheme.typography.bodySmall,
            color = ChalkDim
        )
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
    val hasBar: Boolean = true,
    /**
     * Which way the chest points, so a muscle knows which side of a bone it is
     * on. Perpendicular to the torso is not enough: lying on a bench, the
     * perpendicular runs into the bench, and the pecs would be drawn underneath
     * the lifter.
     */
    val chestFacing: Offset = Offset(1f, 0f),
    /** Draws a bench under the figure, for the lifts performed on one. */
    val onBench: Boolean = false
) {
    fun blend(other: Pose, t: Float) = Pose(
        head = lerp(head, other.head, t),
        shoulder = lerp(shoulder, other.shoulder, t),
        hip = lerp(hip, other.hip, t),
        knee = lerp(knee, other.knee, t),
        ankle = lerp(ankle, other.ankle, t),
        elbow = lerp(elbow, other.elbow, t),
        hand = lerp(hand, other.hand, t),
        hasBar = hasBar,
        chestFacing = lerp(chestFacing, other.chestFacing, t),
        onBench = onBench
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
        elbow = o(0.24f, 0.50f), hand = o(0.34f, 0.46f)
    ) to LYING.copy(
        // Pressed to lockout, arms vertical.
        elbow = o(0.32f, 0.38f), hand = o(0.34f, 0.22f)
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
        onBench = false,
        head = o(0.16f, 0.60f), shoulder = o(0.32f, 0.62f),
        hip = o(0.60f, 0.64f), knee = o(0.72f, 0.72f), ankle = o(0.74f, 0.88f),
        elbow = o(0.22f, 0.54f), hand = o(0.18f, 0.56f)
    ) to LYING.copy(
        onBench = false,
        // Ribs towards the hips: the shoulders curl up off the floor.
        head = o(0.30f, 0.44f), shoulder = o(0.40f, 0.54f),
        hip = o(0.60f, 0.64f), knee = o(0.72f, 0.72f), ankle = o(0.74f, 0.88f),
        elbow = o(0.30f, 0.44f), hand = o(0.28f, 0.48f)
    )

    MovementPattern.UPRIGHT_ROW -> STANDING.copy(
        // Bar hanging at the thighs.
        elbow = o(0.53f, 0.37f), hand = o(0.53f, 0.52f), hasBar = true
    ) to STANDING.copy(
        // Pulled to the collarbone with the elbow above the hand, which is the
        // whole distinction between this and a curl.
        elbow = o(0.62f, 0.22f), hand = o(0.52f, 0.28f), hasBar = true
    )

    MovementPattern.PULLOVER -> LYING.copy(
        // Arms back behind the head, which is where the stretch is.
        elbow = o(0.18f, 0.34f), hand = o(0.06f, 0.30f)
    ) to LYING.copy(
        // Swept over the chest, elbows barely moving.
        elbow = o(0.28f, 0.34f), hand = o(0.34f, 0.24f)
    )

    MovementPattern.LEG_CURL -> PRONE to PRONE.copy(
        // Heels to the backside; everything above the knee stays put.
        knee = o(0.72f, 0.66f), ankle = o(0.60f, 0.48f)
    )

    MovementPattern.LEG_EXTENSION -> SEATED to SEATED.copy(
        // Knee straightens, shin swings forward and up.
        ankle = o(0.86f, 0.62f)
    )

    MovementPattern.HIP_ABDUCTION -> SEATED.copy(
        knee = o(0.62f, 0.66f), ankle = o(0.60f, 0.86f), hasBar = false
    ) to SEATED.copy(
        // Side-on, the thigh swinging out reads as the pads opening. It is the
        // honest limit of a figure drawn from one side.
        knee = o(0.70f, 0.60f), ankle = o(0.74f, 0.82f), hasBar = false
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
    head = o(0.14f, 0.52f),
    shoulder = o(0.32f, 0.55f),
    hip = o(0.60f, 0.58f),
    knee = o(0.76f, 0.68f),
    ankle = o(0.80f, 0.88f),
    elbow = o(0.26f, 0.48f),
    hand = o(0.34f, 0.46f),
    hasBar = true,
    chestFacing = Offset(0f, -1f),
    onBench = true
)

/** Face down on a bench, which is where a leg curl is performed. */
private val PRONE = Pose(
    head = o(0.14f, 0.56f),
    shoulder = o(0.32f, 0.58f),
    hip = o(0.60f, 0.60f),
    knee = o(0.76f, 0.62f),
    ankle = o(0.90f, 0.64f),
    elbow = o(0.24f, 0.62f),
    hand = o(0.16f, 0.64f),
    hasBar = false,
    // Chest towards the bench, so the hamstrings are marked on the upper side
    // of the thigh rather than underneath it.
    chestFacing = Offset(0f, 1f),
    onBench = true
)

/** Sitting on a machine, thighs level, shins hanging: leg extensions and the
 *  hip machines. */
private val SEATED = Pose(
    head = o(0.30f, 0.20f),
    shoulder = o(0.32f, 0.32f),
    hip = o(0.36f, 0.58f),
    knee = o(0.64f, 0.60f),
    ankle = o(0.62f, 0.86f),
    elbow = o(0.30f, 0.45f),
    hand = o(0.30f, 0.57f),
    hasBar = false,
    onBench = true
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
    hasBar = true,
    chestFacing = Offset(0.35f, 0.94f)
)

// ------------------------------------------------------------------ rendering

/**
 * Where a muscle sits on the body, expressed against the bone it moves with.
 *
 * Anchoring to a bone rather than to a fixed point is what lets the markers
 * follow the movement: the quadriceps stay on the thigh as the knee bends,
 * because the thigh is what they are attached to on a real person too.
 */
private data class Marker(
    val bone: Bone,
    /** How far along the bone, 0 at its start and 1 at its end. */
    val along: Float,
    /** Sideways from the bone. Positive is the front of the body, which faces right. */
    val across: Float,
    /** Half the width of the belly, across the bone. */
    val radius: Float,
    /** How long the belly is along the bone, as a multiple of its width. */
    val stretch: Float = 2.4f
)

private enum class Bone { TORSO, UPPER_ARM, FOREARM, THIGH, SHIN }

/**
 * Each muscle group placed on the figure. Side-on, so front and back matter:
 * the biceps sit on the front of the upper arm and the triceps behind it, and
 * the same bone carries both.
 *
 * The shapes are proportioned roughly as the muscles are. A long flat sheet
 * down the back is a lat; a short round cap on the shoulder is a deltoid. A row
 * of identical dots would say only "something here", which is the thing the
 * figure already said.
 */
private val MARKERS: Map<MuscleGroup, Marker> = mapOf(
    MuscleGroup.CHEST to Marker(Bone.TORSO, 0.24f, 0.46f, 0.046f, stretch = 2.2f),
    MuscleGroup.ABS to Marker(Bone.TORSO, 0.70f, 0.34f, 0.034f, stretch = 2.6f),
    MuscleGroup.LATS to Marker(Bone.TORSO, 0.42f, -0.46f, 0.042f, stretch = 3.0f),
    MuscleGroup.BACK to Marker(Bone.TORSO, 0.66f, -0.38f, 0.036f, stretch = 2.2f),
    MuscleGroup.TRAPS to Marker(Bone.TORSO, 0.06f, -0.34f, 0.034f, stretch = 2.0f),
    MuscleGroup.SHOULDERS to Marker(Bone.UPPER_ARM, 0.08f, 0.0f, 0.044f, stretch = 1.5f),
    MuscleGroup.BICEPS to Marker(Bone.UPPER_ARM, 0.52f, 0.28f, 0.030f, stretch = 2.2f),
    MuscleGroup.TRICEPS to Marker(Bone.UPPER_ARM, 0.52f, -0.28f, 0.030f, stretch = 2.2f),
    MuscleGroup.FOREARMS to Marker(Bone.FOREARM, 0.45f, 0.0f, 0.026f, stretch = 2.4f),
    MuscleGroup.GLUTES to Marker(Bone.THIGH, 0.10f, -0.44f, 0.044f, stretch = 1.7f),
    MuscleGroup.QUADS to Marker(Bone.THIGH, 0.48f, 0.34f, 0.040f, stretch = 2.8f),
    MuscleGroup.HAMSTRINGS to Marker(Bone.THIGH, 0.52f, -0.34f, 0.038f, stretch = 2.8f),
    MuscleGroup.CALVES to Marker(Bone.SHIN, 0.38f, -0.34f, 0.032f, stretch = 2.2f)
)

/** The muscle a movement trains hardest. */
private val PRIMARY_COLOUR = Plate25

/** The ones helping. Amber rather than a faded red, so the two never blur. */
private val SECONDARY_COLOUR = Plate15

/**
 * How wide a body part is at one point along its length.
 *
 * Front and back are given separately because a person is not symmetrical from
 * the side: the chest swells forward and the glutes back, and a calf is almost
 * entirely behind the bone it hangs off.
 */
private data class Station(
    /** How far along the part, 0 at its start and 1 at its end. */
    val along: Float,
    /** Half-width towards the front of the body, as a fraction of the figure. */
    val front: Float,
    /** Half-width towards the back. */
    val back: Float
)

// The proportions the figure is built from. Roughly those of an adult seen from
// the side, flattered a little: a drawing at true human proportions reads as
// slightly stumpy at this size.
private val TORSO_SHAPE = listOf(
    Station(0.00f, 0.076f, 0.082f),  // shoulder girdle
    Station(0.22f, 0.090f, 0.070f),  // chest
    Station(0.55f, 0.060f, 0.057f),  // waist
    Station(0.85f, 0.068f, 0.078f),  // pelvis, and the glutes behind it
    Station(1.00f, 0.056f, 0.060f)
)
private val THIGH_SHAPE = listOf(
    Station(0.00f, 0.060f, 0.070f),
    Station(0.40f, 0.056f, 0.058f),
    Station(1.00f, 0.038f, 0.040f)
)
private val SHIN_SHAPE = listOf(
    Station(0.00f, 0.040f, 0.042f),
    Station(0.30f, 0.036f, 0.054f),  // the calf
    Station(1.00f, 0.022f, 0.024f)
)
private val UPPER_ARM_SHAPE = listOf(
    Station(0.00f, 0.048f, 0.050f),
    Station(0.40f, 0.044f, 0.046f),
    Station(1.00f, 0.030f, 0.031f)
)
private val FOREARM_SHAPE = listOf(
    Station(0.00f, 0.032f, 0.033f),
    Station(0.30f, 0.033f, 0.035f),
    Station(1.00f, 0.019f, 0.020f)
)
private val NECK_SHAPE = listOf(
    Station(0.00f, 0.028f, 0.030f),
    Station(1.00f, 0.044f, 0.046f)
)
private val FOOT_SHAPE = listOf(
    Station(0.00f, 0.030f, 0.034f),
    Station(1.00f, 0.018f, 0.020f)
)

/** The far arm and leg, which are behind the body and in its shadow. */
private val FAR_SIDE = Chalk.copy(alpha = 0.32f)

/**
 * Draws a body, then the worked muscles over the top.
 *
 * Built from outlines rather than tubes. A person seen from the side has a
 * chest that swells and a waist that does not, a calf that hangs off the back
 * of the shin, and a head that is taller than it is wide -- and a figure drawn
 * without any of that reads as a mannequin. The far arm and leg are drawn
 * behind in a dimmer shade, which is the other half of it: a body has depth,
 * and a single flat silhouette never looks like one.
 */
private fun DrawScope.drawFigure(
    pose: Pose,
    figureWidth: Float,
    primary: MuscleGroup?,
    secondary: Set<MuscleGroup>,
    at: (Offset) -> Offset
) {
    val unit = figureWidth

    // The floor, so the figure is standing rather than floating. Drawn from the
    // lowest point any pose reaches, not from the current one, or it would rise
    // and fall with the repetition.
    val floorY = at(o(0f, 0.93f)).y
    drawLine(Slate600, Offset(0f, floorY), Offset(size.width, floorY), strokeWidth = unit * 0.02f)

    if (pose.onBench) {
        // Drawn from fixed coordinates rather than from the pose: a bench does
        // not move when the lifter does.
        // Runs from behind the head to past the hips: on a real bench the head
        // is supported and only the legs hang off the end.
        val left = at(o(0.06f, 0.62f))
        val right = at(o(0.68f, 0.66f))
        drawRect(
            color = Slate600,
            topLeft = Offset(left.x, left.y),
            size = androidx.compose.ui.geometry.Size(right.x - left.x, unit * 0.06f)
        )
        listOf(0.16f, 0.60f).forEach { x ->
            val leg = at(o(x, 0.66f))
            drawRect(
                color = Slate600,
                topLeft = Offset(leg.x, leg.y),
                size = androidx.compose.ui.geometry.Size(unit * 0.035f, at(o(0f, 0.93f)).y - leg.y)
            )
        }
    }

    val facing = normalise(pose.chestFacing)
    // The far limbs sit a little way behind the near ones. Enough to be seen
    // past the body, not so much that the figure looks like two people.
    val behind = Offset(-facing.x * 0.022f, -facing.y * 0.022f)

    fun part(a: Offset, b: Offset, shape: List<Station>, colour: Color, shift: Offset = Offset.Zero) {
        val from = at(Offset(a.x + shift.x, a.y + shift.y))
        val to = at(Offset(b.x + shift.x, b.y + shift.y))
        drawPath(fleshPath(from, to, frontNormal(from, to, facing), shape, unit), colour)
    }

    fun joint(p: Offset, radius: Float, colour: Color, shift: Offset = Offset.Zero) {
        drawCircle(colour, radius = radius * unit, center = at(Offset(p.x + shift.x, p.y + shift.y)))
    }

    // Relative to the ankle, not to the floor. Pinned to the floor it stretched
    // into a flipper the moment a foot left the ground -- on a leg curl, where
    // the heel finishes by the backside, most of the leg was foot.
    val toe = Offset(pose.ankle.x + 0.080f, pose.ankle.y + 0.035f)

    // The far side first, then the near side over it, which is the order they
    // overlap in from where the viewer is standing.
    part(pose.hip, pose.knee, THIGH_SHAPE, FAR_SIDE, behind)
    part(pose.knee, pose.ankle, SHIN_SHAPE, FAR_SIDE, behind)
    part(pose.ankle, toe, FOOT_SHAPE, FAR_SIDE, behind)
    joint(pose.knee, 0.040f, FAR_SIDE, behind)
    part(pose.shoulder, pose.elbow, UPPER_ARM_SHAPE, FAR_SIDE, behind)
    part(pose.elbow, pose.hand, FOREARM_SHAPE, FAR_SIDE, behind)
    joint(pose.elbow, 0.031f, FAR_SIDE, behind)

    // Near leg, with the joints rounded off so the parts read as one limb
    // rather than a stack of separate pieces.
    part(pose.hip, pose.knee, THIGH_SHAPE, Chalk)
    joint(pose.knee, 0.040f, Chalk)
    part(pose.knee, pose.ankle, SHIN_SHAPE, Chalk)
    joint(pose.ankle, 0.026f, Chalk)
    part(pose.ankle, toe, FOOT_SHAPE, Chalk)

    joint(pose.hip, 0.058f, Chalk)
    part(pose.shoulder, pose.hip, TORSO_SHAPE, Chalk)
    part(pose.head, pose.shoulder, NECK_SHAPE, Chalk)
    drawHead(pose, unit, at)

    // Near arm last: from the side it crosses everything else. Drawn as one
    // joined shape with a contour around it, because an arm the same colour as
    // the chest it is lying across simply vanishes into it.
    val upper = at(pose.shoulder)
    val elbowAt = at(pose.elbow)
    val handAt = at(pose.hand)
    val arm = union(
        circlePath(upper, unit * 0.058f),   // the deltoid capping the joint
        fleshPath(upper, elbowAt, frontNormal(upper, elbowAt, facing), UPPER_ARM_SHAPE, unit),
        circlePath(elbowAt, unit * 0.031f),
        fleshPath(elbowAt, handAt, frontNormal(elbowAt, handAt, facing), FOREARM_SHAPE, unit),
        circlePath(handAt, unit * 0.026f)
    )
    drawPath(arm, Chalk)
    drawPath(arm, Slate600, style = Stroke(width = unit * 0.010f))

    // Muscles over the body, secondary underneath so a primary marker sharing
    // an area is the one that reads.
    secondary.forEach { muscle -> drawMarker(pose, muscle, SECONDARY_COLOUR, unit, at) }
    primary?.let { muscle -> drawMarker(pose, muscle, PRIMARY_COLOUR, unit, at) }

    if (pose.hasBar) {
        val centre = at(pose.hand)
        val half = unit * 0.22f
        drawLine(
            Plate20,
            Offset(centre.x - half, centre.y),
            Offset(centre.x + half, centre.y),
            strokeWidth = unit * 0.042f,
            cap = StrokeCap.Round
        )
        // Plates, so it reads as a loaded bar rather than a broom handle.
        listOf(-1f, 1f).forEach { side ->
            drawCircle(Plate20, radius = unit * 0.048f, center = Offset(centre.x + side * half, centre.y))
        }
    } else {
        drawCircle(Slate600, radius = unit * 0.032f, center = at(pose.hand))
    }
}

/** Places one muscle on its bone in the pose the figure is currently in. */
private fun DrawScope.drawMarker(
    pose: Pose,
    muscle: MuscleGroup,
    colour: Color,
    unit: Float,
    at: (Offset) -> Offset
) {
    val marker = MARKERS[muscle] ?: return
    val (start, end) = when (marker.bone) {
        Bone.TORSO -> pose.shoulder to pose.hip
        Bone.UPPER_ARM -> pose.shoulder to pose.elbow
        Bone.FOREARM -> pose.elbow to pose.hand
        Bone.THIGH -> pose.hip to pose.knee
        Bone.SHIN -> pose.knee to pose.ankle
    }

    val a = at(start)
    val b = at(end)
    val dx = b.x - a.x
    val dy = b.y - a.y
    val boneLength = kotlin.math.hypot(dx, dy)
    if (boneLength <= 0f) return

    // Along the bone, then out towards the front of the body or away from it.
    val alongX = a.x + dx * marker.along
    val alongY = a.y + dy * marker.along

    val facing = pose.chestFacing
    val facingLength = kotlin.math.hypot(facing.x, facing.y).takeIf { it > 0f } ?: 1f
    // Sideways far enough to sit on the near or far side of the limb, close
    // enough to stay on it. A marker floating clear of the body looks like a
    // label pinned to the air rather than a muscle.
    val offset = marker.across * unit * 0.11f
    val centreX = alongX + (facing.x / facingLength) * offset
    val centreY = alongY + (facing.y / facingLength) * offset

    // The belly runs along the bone, which is the direction a real muscle
    // runs, and is what makes it shorten visibly as the joint closes.
    val half = marker.radius * marker.stretch * unit / 2f
    val ux = dx / boneLength
    val uy = dy / boneLength
    val width = marker.radius * 2f * unit

    drawPath(
        taperedLimb(
            Offset(centreX - ux * half, centreY - uy * half),
            Offset(centreX + ux * half, centreY + uy * half),
            width,
            width * 0.72f
        ),
        colour
    )
}

/**
 * The head: an oval rather than a circle, tilted to follow the neck.
 *
 * A circle reads as a ball on a stick. A skull is taller than it is wide, and
 * on a bench press it lies on its side -- which only looks right if the oval
 * turns with the body.
 */
private fun DrawScope.drawHead(pose: Pose, unit: Float, at: (Offset) -> Offset) {
    val centre = at(pose.head)
    val neck = at(pose.shoulder)
    // The angle the head is tilted at, measured from upright.
    val degrees = Math.toDegrees(
        kotlin.math.atan2((neck.x - centre.x).toDouble(), (neck.y - centre.y).toDouble())
    ).toFloat()

    val rx = unit * 0.070f
    val ry = unit * 0.086f
    rotate(-degrees, centre) {
        drawOval(
            color = Chalk,
            topLeft = Offset(centre.x - rx, centre.y - ry),
            size = androidx.compose.ui.geometry.Size(rx * 2f, ry * 2f)
        )
    }
}

private fun circlePath(centre: Offset, radius: Float): Path = Path().apply {
    addOval(
        androidx.compose.ui.geometry.Rect(
            centre.x - radius, centre.y - radius, centre.x + radius, centre.y + radius
        )
    )
}

/**
 * Several shapes merged into one outline.
 *
 * Used for the arm so it can carry a contour: outlining the upper arm, the
 * elbow and the forearm separately would draw a line across each joint, and an
 * arm with seams in it is worse than one that blends into the chest.
 */
private fun union(vararg parts: Path): Path = parts.reduce { merged, next ->
    Path().apply { op(merged, next, androidx.compose.ui.graphics.PathOperation.Union) }
}

/** A unit vector, or forward if the vector has no length to speak of. */
private fun normalise(v: Offset): Offset {
    val length = kotlin.math.hypot(v.x, v.y)
    return if (length > 0f) Offset(v.x / length, v.y / length) else Offset(1f, 0f)
}

/**
 * The direction "in front of" a bone: perpendicular to it, on whichever side
 * the chest is pointing. This is what lets one set of proportions serve a
 * standing figure and one lying on a bench.
 */
private fun frontNormal(from: Offset, to: Offset, facing: Offset): Offset {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val length = kotlin.math.hypot(dx, dy)
    if (length <= 0f) return facing
    val nx = -dy / length
    val ny = dx / length
    return if (nx * facing.x + ny * facing.y < 0f) Offset(-nx, -ny) else Offset(nx, ny)
}

/**
 * A body part as a smooth closed outline: down the front through each station,
 * back up the rear. The curve is what does the work -- the same points joined
 * with straight lines look like a cardboard cut-out.
 */
private fun fleshPath(
    from: Offset,
    to: Offset,
    front: Offset,
    stations: List<Station>,
    unit: Float
): Path {
    val frontEdge = ArrayList<Offset>(stations.size)
    val backEdge = ArrayList<Offset>(stations.size)
    stations.forEach { station ->
        val x = from.x + (to.x - from.x) * station.along
        val y = from.y + (to.y - from.y) * station.along
        frontEdge += Offset(x + front.x * station.front * unit, y + front.y * station.front * unit)
        backEdge += Offset(x - front.x * station.back * unit, y - front.y * station.back * unit)
    }
    return closedSpline(frontEdge + backEdge.asReversed())
}

/**
 * A closed Catmull-Rom curve through the points, converted to the cubics a
 * Path can hold. Passes through every point, which matters here: the stations
 * are the measurements, not suggestions.
 */
private fun closedSpline(points: List<Offset>): Path {
    val path = Path()
    val n = points.size
    if (n < 3) return path

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

/**
 * A limb as a quadrilateral with rounded ends, wider at one end than the other.
 */
private fun taperedLimb(from: Offset, to: Offset, startWidth: Float, endWidth: Float): Path {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val length = kotlin.math.hypot(dx, dy).takeIf { it > 0f } ?: return Path()
    val nx = -dy / length
    val ny = dx / length
    val s = startWidth / 2f
    val e = endWidth / 2f

    return Path().apply {
        moveTo(from.x + nx * s, from.y + ny * s)
        lineTo(to.x + nx * e, to.y + ny * e)
        // Rounded cap at the far end.
        quadraticTo(
            to.x + (dx / length) * e * 1.4f, to.y + (dy / length) * e * 1.4f,
            to.x - nx * e, to.y - ny * e
        )
        lineTo(from.x - nx * s, from.y - ny * s)
        quadraticTo(
            from.x - (dx / length) * s * 1.4f, from.y - (dy / length) * s * 1.4f,
            from.x + nx * s, from.y + ny * s
        )
        close()
    }
}
