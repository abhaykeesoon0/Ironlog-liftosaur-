package com.ironlog.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.ChalkFaint
import com.ironlog.app.ui.theme.Slate900
import com.ironlog.app.ui.theme.Space

/**
 * What the app shows while it works out who is signed in.
 *
 * This used to be a spinner on a flat background, which is the visual
 * equivalent of a held breath. It is now the app introducing itself: the mark
 * and the name over a background that is lit rather than filled, with the light
 * drifting slowly so the screen is alive while it waits.
 *
 * The gradient is here rather than in the system's launch window because that
 * window only accepts a single colour. The two share the same slate, so the
 * handover from one to the other is not a flash.
 */
@Composable
fun LaunchScreen(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "launch")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift"
    )

    Box(
        modifier
            .fillMaxSize()
            .background(Slate900)
            .semantics { contentDescription = "IronLog is starting" },
        contentAlignment = Alignment.Center
    ) {
        // The light, filling the whole screen: a wash from the top left and a
        // deeper one drawn up from the bottom, so the background has somewhere
        // to fall away to rather than being one even sheet of grey.
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF18222D), Slate900, Color(0xFF080B0E)),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height)
                )
            )
            glow(
                centre = Offset(size.width * (0.24f + drift * 0.10f), size.height * 0.26f),
                radius = size.maxDimension * 0.62f,
                alpha = 0.10f
            )
            glow(
                centre = Offset(size.width * (0.82f - drift * 0.08f), size.height * 0.78f),
                radius = size.maxDimension * 0.48f,
                alpha = 0.05f
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Barbell(Modifier.size(width = 148.dp, height = 84.dp))
            Spacer(Modifier.height(Space.lg))
            Text(
                text = "IRONLOG",
                color = Chalk,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                // Letter-spaced, because a short word set wide reads as a mark
                // rather than as a piece of running text.
                letterSpacing = 10.sp,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                text = "Lift. Log. Repeat.",
                color = ChalkDim,
                letterSpacing = 2.sp,
                style = MaterialTheme.typography.bodySmall
            )
        }

        // A thin rule that fills and empties rather than a spinner, which on a
        // launch screen reads as waiting for something to go wrong.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp)
                .width(96.dp)
                .height(2.dp)
                .background(ChalkFaint.copy(alpha = 0.25f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.25f + drift * 0.75f)
                    .height(2.dp)
                    .background(ChalkDim)
            )
        }
    }
}

/** A soft circle of light, fading to nothing at its edge. */
private fun DrawScope.glow(centre: Offset, radius: Float, alpha: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = alpha), Color.Transparent),
            center = centre,
            radius = radius
        ),
        radius = radius,
        center = centre
    )
}

/**
 * The same barbell as the launcher icon, drawn rather than scaled from it, so
 * the mark on the launch screen is the mark on the home screen.
 */
@Composable
private fun Barbell(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        // The mark's own bounds inside the icon's 108-unit canvas, scaled to
        // fill whatever box it is given without distorting the proportions.
        val markLeft = 23.5f
        val markTop = 38f
        val markWidth = 61f
        val markHeight = 32f

        val scale = minOf(size.width / markWidth, size.height / markHeight)
        val originX = (size.width - markWidth * scale) / 2f
        val originY = (size.height - markHeight * scale) / 2f

        fun piece(x: Float, y: Float, width: Float, height: Float, radius: Float, colour: Color) {
            drawRoundRect(
                color = colour,
                topLeft = Offset(originX + (x - markLeft) * scale, originY + (y - markTop) * scale),
                size = Size(width * scale, height * scale),
                cornerRadius = CornerRadius(radius * scale, radius * scale)
            )
        }

        // Lifted straight from ic_launcher_foreground.xml, so the two cannot
        // drift apart without somebody noticing.
        piece(34f, 52.4f, 40f, 3.2f, 1.6f, Chalk)      // bar
        piece(36.5f, 47f, 3.5f, 14f, 1.2f, Chalk)      // collars
        piece(68f, 47f, 3.5f, 14f, 1.2f, Chalk)
        piece(28.5f, 38f, 6.5f, 32f, 3f, Chalk)        // heavy plates
        piece(73f, 38f, 6.5f, 32f, 3f, Chalk)
        piece(23.5f, 44.5f, 4.5f, 19f, 2f, ChalkDim)   // light plates
        piece(80f, 44.5f, 4.5f, 19f, 2f, ChalkDim)
    }
}
