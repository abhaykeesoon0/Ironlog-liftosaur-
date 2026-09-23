package com.ironlog.app.ui.components

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ironlog.app.ui.theme.Slate800
import com.ironlog.app.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** How long each end of the repetition is held before the demonstration turns over. */
private const val HOLD_MS = 1100L
private const val FADE_MS = 260

/**
 * An exercise demonstrated by its two photographs, the start and the finish of
 * one repetition, shown in turn.
 *
 * Two frames rather than a film. It is what the source dataset publishes, it is
 * how printed exercise charts have always shown a lift, and the two positions
 * are the information: where the weight begins and where it ends. A crossfade
 * between them reads as the movement rather than as a slideshow.
 *
 * The frames ship inside the app, so this works with no network, which the rest
 * of the app also assumes.
 */
@Composable
fun ExerciseDemo(
    exerciseId: String,
    exerciseName: String,
    modifier: Modifier = Modifier
) {
    val assets = LocalContext.current.assets
    var frames by remember(exerciseId) { mutableStateOf<List<ImageBitmap>?>(null) }
    var shown by remember(exerciseId) { mutableIntStateOf(0) }

    LaunchedEffect(exerciseId) {
        frames = withContext(Dispatchers.IO) { loadFrames(assets, exerciseId) }
    }

    val loaded = frames
    // No photographs is a state rather than a failure: an exercise the user
    // wrote themselves has none, and the rest of the dialog still describes it.
    if (loaded.isNullOrEmpty()) return

    if (loaded.size > 1) {
        LaunchedEffect(exerciseId) {
            while (true) {
                delay(HOLD_MS)
                shown = (shown + 1) % loaded.size
            }
        }
    }

    Column(modifier.fillMaxWidth()) {
        Crossfade(
            targetState = shown,
            animationSpec = tween(FADE_MS),
            label = "demo frame",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 2f)
                .clip(RoundedCornerShape(Space.sm))
                .background(Slate800)
                .semantics {
                    contentDescription = "$exerciseName, the start and finish of one repetition"
                }
        ) { index ->
            Image(
                bitmap = loaded[index.coerceIn(loaded.indices)],
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(Space.xs))
        // The photographs are somebody else's work, so they are credited even
        // though the licence they are published under does not demand it.
        Caption("Photographs from free-exercise-db, published into the public domain.")
    }
}

/**
 * The frames for one exercise, or nothing.
 *
 * A missing folder is the ordinary case for a custom exercise, so it is not
 * treated as an error; a frame that is present but will not decode is skipped
 * rather than failing the whole demonstration, which would take a working first
 * frame down with a corrupt second one.
 */
private fun loadFrames(assets: AssetManager, exerciseId: String): List<ImageBitmap> =
    (0..1).mapNotNull { index ->
        runCatching {
            assets.open("demo/$exerciseId/$index.webp").use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        }.getOrNull()
    }
