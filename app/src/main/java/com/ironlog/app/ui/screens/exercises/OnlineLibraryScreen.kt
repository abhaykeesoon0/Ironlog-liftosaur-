package com.ironlog.app.ui.screens.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.ui.components.Caption
import com.ironlog.app.ui.components.ErrorBanner
import com.ironlog.app.ui.components.IronCard
import com.ironlog.app.ui.components.IronTopBar
import com.ironlog.app.ui.components.LoadingBlock
import com.ironlog.app.ui.components.SecondaryButton
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.PrGold
import com.ironlog.app.ui.theme.Space

/**
 * The wger online database, browsable and importable.
 *
 * Separate from the built-in library rather than merged into it: this is the
 * one screen that fetches anything, the user chose to open it, and an imported
 * exercise carries a licence and an author that the local ones do not.
 */
@Composable
fun OnlineLibraryScreen(
    onBack: () -> Unit,
    viewModel: OnlineLibraryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        IronTopBar(title = "Online library", onBack = onBack)

        when {
            state.isLoading && state.entries.isEmpty() -> LoadingBlock(Modifier.fillMaxSize())
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.md)
            ) {
                item { ErrorBanner(state.error) }
                item {
                    Caption(
                        "From wger.de, an open fitness database. This screen is the only part of the " +
                            "app that goes online besides the assistant — nothing is sent, and nothing " +
                            "is downloaded until you import it."
                    )
                }

                items(state.entries, key = { it.id }) { entry ->
                    IronCard {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        entry.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Chalk,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Caption("${entry.muscle.label} · ${entry.equipment}")
                                }
                                Spacer(Modifier.width(Space.sm))
                                if (entry.id in state.imported) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = PrGold,
                                            modifier = Modifier.width(18.dp)
                                        )
                                        Spacer(Modifier.width(Space.xs))
                                        Text("Added", style = MaterialTheme.typography.labelMedium, color = PrGold)
                                    }
                                } else {
                                    SecondaryButton(
                                        text = "Import",
                                        onClick = { viewModel.import(entry) },
                                        modifier = Modifier.width(110.dp)
                                    )
                                }
                            }
                            if (entry.description.isNotBlank()) {
                                Spacer(Modifier.height(Space.sm))
                                Text(
                                    entry.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ChalkDim,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.height(Space.xs))
                            // Attribution travels with the data. It is a
                            // condition of the licence, not a courtesy.
                            Caption("${entry.licence} · ${entry.author}")
                        }
                    }
                }

                if (state.hasMore) {
                    item {
                        SecondaryButton(
                            text = if (state.isLoading) "Loading" else "Load more",
                            onClick = viewModel::loadMore,
                            enabled = !state.isLoading
                        )
                    }
                }
                item { Spacer(Modifier.height(Space.xl)) }
            }
        }
    }
}
