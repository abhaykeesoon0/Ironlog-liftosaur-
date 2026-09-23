package com.ironlog.app.ui.screens.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.ui.components.Caption
import com.ironlog.app.ui.components.EmptyState
import com.ironlog.app.ui.components.ErrorBanner
import com.ironlog.app.ui.components.Hairline
import com.ironlog.app.ui.components.IronCard
import com.ironlog.app.ui.components.IronTopBar
import com.ironlog.app.ui.components.LoadingBlock
import com.ironlog.app.ui.components.PrimaryButton
import com.ironlog.app.ui.components.SecondaryButton
import com.ironlog.app.ui.components.SectionHeader
import com.ironlog.app.ui.components.StatTile
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.NumericType
import com.ironlog.app.ui.theme.Space
import kotlin.math.roundToInt

/**
 * A day of meals built for the goal.
 *
 * Shown as a menu rather than a spreadsheet: four meals, the foods in each,
 * the amount of each, and the totals against the target at the top. Shuffle
 * is a new draw from the same library; "log it" writes the whole day into the
 * food log in one go, which is how a plan becomes a record.
 */
@Composable
fun PlanScreen(
    onBack: () -> Unit,
    viewModel: PlanViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.message) {
        if (state.message != null) {
            kotlinx.coroutines.delay(2_500)
            viewModel.dismissMessage()
        }
    }

    Column(Modifier.fillMaxSize()) {
        IronTopBar(title = "Diet plan", onBack = onBack)

        when {
            state.isLoading -> LoadingBlock(Modifier.fillMaxSize())
            state.goal == null || state.plan == null -> EmptyState(
                icon = Icons.Default.Restaurant,
                title = "Set a goal first",
                body = "A plan is built for a target, and the target comes from your goal.",
                modifier = Modifier.fillMaxSize()
            )
            else -> {
                val plan = state.plan!!
                val goal = state.goal!!
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm),
                    verticalArrangement = Arrangement.spacedBy(Space.md)
                ) {
                    item { ErrorBanner(state.error) }
                    state.message?.let { item { Caption(it) } }

                    item {
                        IronCard {
                            Column {
                                Text(
                                    "${goal.goal.label} · ${goal.targetKcal} kcal a day",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Chalk
                                )
                                Spacer(Modifier.height(Space.md))
                                val t = plan.totals
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    StatTile("${t.kcal.roundToInt()}", "kcal")
                                    StatTile("${t.proteinG.roundToInt()} g", "protein")
                                    StatTile("${t.carbsG.roundToInt()} g", "carbs")
                                    StatTile("${t.fatG.roundToInt()} g", "fat")
                                }
                                Spacer(Modifier.height(Space.xs))
                                Caption("Target: ${goal.targetKcal} kcal · ${goal.proteinG} g · ${goal.carbsG} g · ${goal.fatG} g")
                                Spacer(Modifier.height(Space.md))
                                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                                    SecondaryButton(
                                        text = "Shuffle",
                                        onClick = viewModel::shuffle,
                                        leadingIcon = Icons.Default.Shuffle,
                                        modifier = Modifier.weight(1f)
                                    )
                                    PrimaryButton(
                                        text = "Log this day",
                                        onClick = viewModel::logPlan,
                                        loading = state.isLogging,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    plan.meals.forEach { meal ->
                        item(key = "header-${meal.meal.name}") {
                            SectionHeader(meal.meal.label) {
                                Text("${meal.totals.kcal.roundToInt()} kcal", style = NumericType.small, color = ChalkDim)
                            }
                        }
                        item(key = "meal-${meal.meal.name}") {
                            IronCard {
                                Column {
                                    meal.portions.forEachIndexed { index, portion ->
                                        if (index > 0) Hairline(Modifier.padding(vertical = Space.sm))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(portion.food.name, style = MaterialTheme.typography.bodyLarge, color = Chalk)
                                                val p = portion.totals
                                                Caption("P ${p.proteinG.roundToInt()} · C ${p.carbsG.roundToInt()} · F ${p.fatG.roundToInt()}")
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("${portion.grams.roundToInt()} g", style = NumericType.setCell, color = Chalk)
                                                Caption("${portion.totals.kcal.roundToInt()} kcal")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Caption(
                            "Built from the food library by a few plain rules: every main meal has a " +
                                "protein, a carbohydrate and a vegetable, and portions are sized to hit " +
                                "the protein target first. Swap anything you would not eat."
                        )
                    }
                    item { Spacer(Modifier.height(Space.xl)) }
                }
            }
        }
    }
}
