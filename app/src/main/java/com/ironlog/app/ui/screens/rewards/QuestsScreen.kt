package com.ironlog.app.ui.screens.rewards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.domain.usecase.Quests
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
import com.ironlog.app.ui.theme.Plate10
import com.ironlog.app.ui.theme.PrGold
import com.ironlog.app.ui.theme.Radius
import com.ironlog.app.ui.theme.Slate600
import com.ironlog.app.ui.theme.Slate700
import com.ironlog.app.ui.theme.Space
import kotlin.math.roundToInt

/**
 * Quests and the wallet.
 *
 * Progress comes from logged training, so nothing here can be advanced by
 * opening the app or pressing a button. Claiming is the only action, and a
 * quest pays once per day or per week.
 */
@Composable
fun QuestsScreen(
    onBack: () -> Unit,
    onOpenStore: () -> Unit,
    viewModel: QuestsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The reward toast is informational; clearing it after a moment keeps it
    // from sitting on screen until the next claim.
    LaunchedEffect(state.justEarned) {
        if (state.justEarned != null) {
            kotlinx.coroutines.delay(2_500)
            viewModel.dismissEarned()
        }
    }

    Column(Modifier.fillMaxSize()) {
        IronTopBar(
            title = "Quests",
            onBack = onBack,
            actions = {
                CoinPill(state.balance)
                IconButton(onClick = onOpenStore) {
                    Icon(Icons.Default.Storefront, contentDescription = "Open the store", tint = Chalk)
                }
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
                state.justEarned?.let { coins ->
                    item {
                        IronCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = PrGold)
                                Spacer(Modifier.width(Space.sm))
                                Text("+$coins coins", style = MaterialTheme.typography.titleMedium, color = PrGold)
                            }
                        }
                    }
                }

                val daily = state.quests.filter { it.quest.period == Quests.Period.DAILY }
                val weekly = state.quests.filter { it.quest.period == Quests.Period.WEEKLY }

                item { SectionHeader("Today") }
                items(daily, key = { it.quest.id }) { QuestCard(it) { viewModel.claim(it.quest) } }

                item { SectionHeader("This week") }
                items(weekly, key = { it.quest.id }) { QuestCard(it) { viewModel.claim(it.quest) } }

                item {
                    Caption("Daily quests reset at midnight. Weekly quests reset on Monday.")
                }
                item { Spacer(Modifier.height(Space.xl)) }
            }
        }
    }
}

/** The wallet, shown wherever coins can be spent or earned. */
@Composable
fun CoinPill(balance: Int, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radius.chip)
    Row(
        modifier
            .background(Slate700, shape)
            .border(1.dp, Slate600, shape)
            .padding(horizontal = Space.sm, vertical = 4.dp)
            .semantics { contentDescription = "$balance coins" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.MonetizationOn,
            contentDescription = null,
            tint = PrGold,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(Space.xs))
        Text(
            balance.toString(),
            style = NumericType.small,
            color = Chalk,
            modifier = Modifier.clearAndSetSemantics { }
        )
    }
}

@Composable
private fun QuestCard(progress: Quests.Progress, onClaim: () -> Unit) {
    val quest = progress.quest
    IronCard {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(quest.title, style = MaterialTheme.typography.titleMedium, color = Chalk)
                    Caption(quest.detail)
                }
                Spacer(Modifier.width(Space.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.MonetizationOn,
                        contentDescription = null,
                        tint = if (progress.isClaimed) ChalkFaint else PrGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(Space.xs))
                    Text(
                        quest.coins.toString(),
                        style = NumericType.small,
                        color = if (progress.isClaimed) ChalkFaint else PrGold
                    )
                }
            }

            Spacer(Modifier.height(Space.md))
            val shown = formatProgress(progress)
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .semantics { contentDescription = "${quest.title}: $shown" },
                color = if (progress.isComplete) PrGold else Plate10,
                trackColor = Slate700,
                drawStopIndicator = {}
            )
            Spacer(Modifier.height(Space.xs))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Caption(shown)
                when {
                    progress.isClaimed -> Text(
                        "Claimed",
                        style = MaterialTheme.typography.labelMedium,
                        color = ChalkDim
                    )
                    progress.isClaimable -> SecondaryButton(text = "Claim", onClick = onClaim)
                    else -> Text(
                        "Keep going",
                        style = MaterialTheme.typography.labelMedium,
                        color = ChalkFaint
                    )
                }
            }
        }
    }
}

/**
 * Progress in the quest's own terms: a duration reads as minutes, volume in
 * kilograms, and a count of sessions as a plain number.
 */
private fun formatProgress(progress: Quests.Progress): String {
    val quest = progress.quest
    val current = progress.current
    return when (quest.id) {
        "daily_volume" -> "${current.roundToInt()} / ${quest.target.roundToInt()} kg"
        "weekly_long_session" -> "${(current / 60).roundToInt()} / ${(quest.target / 60).roundToInt()} min"
        else -> "${current.roundToInt()} / ${quest.target.roundToInt()}"
    }
}
