package com.ironlog.app.ui.screens.rewards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.domain.usecase.StoreCatalogue
import com.ironlog.app.ui.components.Caption
import com.ironlog.app.ui.components.ErrorBanner
import com.ironlog.app.ui.components.IronCard
import com.ironlog.app.ui.components.IronTopBar
import com.ironlog.app.ui.components.LoadingBlock
import com.ironlog.app.ui.components.SecondaryButton
import com.ironlog.app.ui.components.SectionHeader
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.ChalkFaint
import com.ironlog.app.ui.theme.NumericType
import com.ironlog.app.ui.theme.PrGold
import com.ironlog.app.ui.theme.Radius
import com.ironlog.app.ui.theme.Slate600
import com.ironlog.app.ui.theme.Slate700
import com.ironlog.app.ui.theme.Space

/**
 * The store.
 *
 * Three shelves. Backgrounds and badges change how the app looks; widgets add
 * a card to Home that saves some arithmetic. Nothing needed to log a workout
 * is sold here, and nothing costs real money -- coins come from training.
 */
@Composable
fun StoreScreen(
    onBack: () -> Unit,
    viewModel: StoreViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        IronTopBar(
            title = "Store",
            onBack = onBack,
            actions = {
                CoinPill(state.balance)
                Spacer(Modifier.width(Space.sm))
            }
        )

        if (state.isLoading) {
            LoadingBlock(Modifier.fillMaxSize())
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.md)
            ) {
                item { ErrorBanner(state.error) }

                StoreCatalogue.Category.entries.forEach { category ->
                    item(key = "header-$category") {
                        Column {
                            SectionHeader(category.label)
                            Caption(category.blurb)
                        }
                    }
                    items(state.inCategory(category), key = { it.item.id }) { row ->
                        StoreRowCard(
                            row = row,
                            onBuy = { viewModel.buy(row.item) },
                            onEquip = { viewModel.equip(row.item) }
                        )
                    }
                }
                item { Spacer(Modifier.height(Space.xl)) }
            }
        }
    }
}

@Composable
private fun StoreRowCard(row: StoreRow, onBuy: () -> Unit, onEquip: () -> Unit) {
    val item = row.item
    IronCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // A background is shown as the colour itself; nobody can judge
            // "Deep navy" from the words alone.
            if (item.category == StoreCatalogue.Category.BACKGROUND) {
                Box(
                    Modifier
                        .size(40.dp)
                        .background(backgroundColour(item.id), RoundedCornerShape(Radius.control))
                        .border(1.dp, Slate600, RoundedCornerShape(Radius.control))
                        .semantics { contentDescription = "${item.name} preview" }
                )
                Spacer(Modifier.width(Space.md))
            } else {
                previewIcon(item)?.let { icon ->
                    Box(
                        Modifier
                            .size(40.dp)
                            .background(Slate700, RoundedCornerShape(Radius.control)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = Chalk, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(Space.md))
                }
            }

            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, color = Chalk)
                Caption(item.detail)
            }
            Spacer(Modifier.width(Space.sm))

            // The action sits in a fixed column: SecondaryButton fills the width
            // it is given, and left unbounded in a Row it starves the name beside
            // it down to nothing.
            Column(
                modifier = Modifier.width(ACTION_WIDTH),
                horizontalAlignment = Alignment.End
            ) {
                when {
                    row.isEquipped -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = PrGold, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Space.xs))
                        Text("In use", style = MaterialTheme.typography.labelMedium, color = PrGold)
                    }

                    row.isOwned -> SecondaryButton(text = "Use", onClick = onEquip)

                    else -> Column(horizontalAlignment = Alignment.End) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.semantics { contentDescription = "${item.price} coins" }
                        ) {
                            Icon(
                                Icons.Default.MonetizationOn,
                                contentDescription = null,
                                tint = if (row.isAffordable) PrGold else ChalkFaint,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(Space.xs))
                            Text(
                                item.price.toString(),
                                style = NumericType.small,
                                color = if (row.isAffordable) Chalk else ChalkFaint
                            )
                        }
                        Spacer(Modifier.height(Space.xs))
                        if (row.isAffordable) {
                            SecondaryButton(text = "Buy", onClick = onBuy)
                        } else {
                            Text("Train for it", style = MaterialTheme.typography.labelMedium, color = ChalkDim)
                        }
                    }
                }
            }
        }
    }
}

/** Enough for "Train for it" on one line, and for a button to look like one. */
private val ACTION_WIDTH = 108.dp
