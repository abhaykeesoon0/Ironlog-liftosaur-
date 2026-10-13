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
import com.ironlog.app.core.permissions.AppPermission
import com.ironlog.app.core.permissions.rememberPermissionState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import com.ironlog.app.core.ai.LocalModel
import com.ironlog.app.core.ai.LocalModelState
import com.ironlog.app.ui.components.IronCard
import com.ironlog.app.ui.components.PrimaryButton
import com.ironlog.app.ui.theme.ChalkFaint
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
        LocalModelCard(
            model = state.localModel,
            onDownload = viewModel::downloadModel,
            onCancel = viewModel::cancelModelDownload,
            onRemove = viewModel::removeModel
        )

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

        // Whether the coach may reach for the web. Lookups are on by default,
        // keyless, and send only the topic of the question, never the log; the
        // chip is here, where it is used, so switching it off is one tap.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.lg),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IronChip(
                text = if (state.webLookups) "Looking things up online" else "Offline only",
                selected = state.webLookups
            ) { viewModel.setWebLookups(!state.webLookups) }
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

/**
 * The model that runs on the phone: offered, downloading, or ready.
 *
 * Kept to a few lines so the chat stays the point of the screen. The size is
 * stated before the download and mobile data is off unless asked for, because
 * a file this large should never be a surprise on a data plan.
 */
@Composable
private fun LocalModelCard(
    model: LocalModelState,
    onDownload: (LocalModel, Boolean) -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit
) {
    var choice by remember { mutableStateOf(LocalModel.COMPACT) }
    var allowMetered by remember { mutableStateOf(false) }
    // The progress bar lives in a notification. Asked for with the reason
    // first; a refusal still starts the download, just without the bar.
    val notifications = rememberPermissionState(AppPermission.NOTIFICATIONS)
    val start = { notifications.runWithPermission(onDenied = { onDownload(choice, allowMetered) }) { onDownload(choice, allowMetered) } }

    Column(Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.xs)) {
        when (model.status) {
            LocalModelState.Status.READY -> Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Caption("Model on this phone: ${model.model?.label}. Answers without a key or a connection.", Modifier.weight(1f))
                TextButton(onClick = onRemove) { Text("Remove", color = ChalkDim) }
            }

            LocalModelState.Status.DOWNLOADING -> Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Caption("Downloading ${model.model?.label} model · ${model.percent}%", Modifier.weight(1f))
                    TextButton(onClick = onCancel) { Text("Cancel", color = ChalkDim) }
                }
                LinearProgressIndicator(
                    progress = { model.percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = Chalk,
                    trackColor = ChalkFaint
                )
                model.error?.let { Caption(it, Modifier.padding(top = Space.xs)) }
            }

            LocalModelState.Status.NONE -> IronCard {
                Column {
                    Text("Answer with a model on this phone", style = MaterialTheme.typography.titleMedium, color = Chalk)
                    Caption(
                        "A real language model, downloaded once and run on the handset. No key, no account, and " +
                            "nothing leaves the phone afterwards. It reads your figures and, unless lookups are off, the web."
                    )
                    Spacer(Modifier.height(Space.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        LocalModel.entries.forEach { option ->
                            IronChip(text = "${option.label} · ${option.sizeLabel}", selected = choice == option) { choice = option }
                        }
                    }
                    Caption(choice.blurb, Modifier.padding(top = Space.xs))
                    Spacer(Modifier.height(Space.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalAlignment = Alignment.CenterVertically) {
                        IronChip(text = if (allowMetered) "Wi-Fi or mobile data" else "Wi-Fi only", selected = allowMetered) {
                            allowMetered = !allowMetered
                        }
                    }
                    model.error?.let { ErrorBanner(it, Modifier.padding(top = Space.sm)) }
                    Spacer(Modifier.height(Space.md))
                    PrimaryButton(text = "Download ${choice.sizeLabel}", onClick = start)
                }
            }
        }
    }
}
