package com.ironlog.app.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ironlog.app.ui.components.PrimaryButton
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.Slate600
import com.ironlog.app.ui.theme.Space
import kotlinx.coroutines.launch

/**
 * Three pages before the first sign-in: what the app is, where the data
 * stays, and how it asks for things.
 *
 * Short on purpose. An introduction that needs scrolling is a manual, and
 * nobody reads one before a workout. The third page is the one that earns
 * its place: it says, before any permission dialog appears, that each one
 * will be asked for in context with a reason -- which is the policy the rest
 * of the app follows, stated once where it can be read.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pages = listOf(
        Page(
            Icons.Default.FitnessCenter,
            "Log every set",
            "Weights, reps and rest, with records spotted as you go. Routines, a coach, progress charts, " +
                "nutrition and a body map of what you have trained."
        ),
        Page(
            Icons.Default.Lock,
            "Your data stays on this phone",
            "No account server, no cloud, no tracking. The log lives in the app's private storage, and the " +
                "only copies that ever leave are the ones you export yourself."
        ),
        Page(
            Icons.Default.PhoneAndroid,
            "Permissions are asked for in context",
            "Camera for photos and barcodes, location for outdoor cardio, microphone for logging by voice. " +
                "Each is asked for when you first use the feature, with the reason first, and each is optional."
        )
    )
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex

    Column(
        Modifier
            .fillMaxSize()
            // The app draws edge to edge. Without this the Skip button sits
            // under the status bar, where the system takes the taps.
            .safeDrawingPadding()
            .padding(Space.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (!last) TextButton(onClick = onDone) { Text("Skip", color = ChalkDim) }
        }
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) { index ->
            val page = pages[index]
            Column(
                Modifier.fillMaxSize().padding(horizontal = Space.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(page.icon, contentDescription = null, tint = Chalk, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(Space.xl))
                Text(page.title, style = MaterialTheme.typography.headlineMedium, color = Chalk, textAlign = TextAlign.Center)
                Spacer(Modifier.height(Space.md))
                Text(page.body, style = MaterialTheme.typography.bodyLarge, color = ChalkDim, textAlign = TextAlign.Center)
            }
        }
        Row(
            Modifier.semantics { contentDescription = "Page ${pager.currentPage + 1} of ${pages.size}" },
            horizontalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            pages.indices.forEach { i ->
                Box(
                    Modifier
                        .size(8.dp)
                        .background(if (i == pager.currentPage) Chalk else Slate600, CircleShape)
                )
            }
        }
        Spacer(Modifier.height(Space.lg))
        PrimaryButton(
            text = if (last) "Get started" else "Next",
            onClick = {
                if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            }
        )
        Spacer(Modifier.height(Space.md))
    }
}

private data class Page(val icon: ImageVector, val title: String, val body: String)
