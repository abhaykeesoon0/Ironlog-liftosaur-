package com.ironlog.app.ui.screens.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.ui.components.Caption
import com.ironlog.app.ui.components.ErrorBanner
import com.ironlog.app.ui.components.IronChip
import com.ironlog.app.ui.components.IronTextField
import com.ironlog.app.ui.components.IronTopBar
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.Radius
import com.ironlog.app.ui.theme.Slate700
import com.ironlog.app.ui.theme.Slate800
import com.ironlog.app.ui.theme.Space
import com.ironlog.app.ui.theme.Warning
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalUriHandler

/**
 * The coach.
 *
 * Every answer carries where it came from, because an assistant that states
 * things confidently without saying why is the easiest kind to be misled by.
 */
@Composable
fun CoachScreen(
    onBack: () -> Unit,
    viewModel: CoachViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Follow the conversation as it grows, the way a messaging app does.
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        IronTopBar(title = "Coach", onBack = onBack)
        ErrorBanner(state.error, Modifier.padding(horizontal = Space.lg))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            itemsIndexed(state.messages) { _, message -> Bubble(message) }
            if (state.isThinking) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = ChalkDim, strokeWidth = 2.dp)
                        Spacer(Modifier.width(Space.sm))
                        Caption("Thinking")
                    }
                }
            }
        }

        // Openers, so the first question is never a blank page.
        if (state.messages.size <= 1) {
            LazyColumn(
                modifier = Modifier.height(96.dp),
                contentPadding = PaddingValues(horizontal = Space.lg),
                verticalArrangement = Arrangement.spacedBy(Space.xs)
            ) {
                itemsIndexed(viewModel.suggestions.chunked(2)) { _, pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        pair.forEach { suggestion ->
                            IronChip(text = suggestion, selected = false) { viewModel.ask(suggestion) }
                        }
                    }
                }
            }
        }

        // What the coach may reach for. Web lookups are keyless and send only
        // the topic; the cloud sends the question with the user's figures.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.lg),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IronChip(
                text = if (state.webLookups) "Looking things up online" else "Offline only",
                selected = state.webLookups
            ) { viewModel.setWebLookups(!state.webLookups) }
            if (state.cloudAvailable) {
                IronChip(
                    text = if (state.useCloud) "Asking Claude" else "Built-in coach",
                    selected = state.useCloud
                ) { viewModel.setUseCloud(!state.useCloud) }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(Space.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IronTextField(
                value = draft,
                onValueChange = { draft = it },
                label = "Ask a question",
                imeAction = ImeAction.Send,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(Space.sm))
            IconButton(
                onClick = {
                    viewModel.ask(draft)
                    draft = ""
                },
                enabled = draft.isNotBlank() && !state.isThinking
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (draft.isBlank()) ChalkDim else Chalk
                )
            }
        }
    }
}

@Composable
private fun Bubble(message: CoachMessage) {
    val shape = RoundedCornerShape(Radius.card)
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.fromUser) Alignment.End else Alignment.Start
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.92f)
                .background(if (message.fromUser) Slate700 else Slate800, shape)
                .padding(Space.md)
        ) {
            Text(
                message.text,
                style = MaterialTheme.typography.bodyMedium,
                // A referral is the one answer that should not blend in.
                color = if (message.isReferral) Warning else Chalk
            )
            message.url?.takeIf { it.isNotBlank() }?.let { url ->
                val uriHandler = LocalUriHandler.current
                Text(
                    "Read the source",
                    style = MaterialTheme.typography.labelLarge,
                    color = Chalk,
                    modifier = Modifier
                        .padding(top = Space.xs)
                        .clickable(onClickLabel = "Open $url") { uriHandler.openUri(url) }
                )
            }
            message.source?.let {
                Spacer(Modifier.height(Space.xs))
                Caption("Source: $it")
            }
        }
    }
}
