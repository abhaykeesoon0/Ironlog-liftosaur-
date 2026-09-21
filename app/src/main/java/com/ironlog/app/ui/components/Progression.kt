package com.ironlog.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ironlog.app.domain.usecase.Gamification
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.ChalkFaint
import com.ironlog.app.ui.theme.NumericType
import com.ironlog.app.ui.theme.Plate20
import com.ironlog.app.ui.theme.PrGold
import com.ironlog.app.ui.theme.Radius
import com.ironlog.app.ui.theme.Slate600
import com.ironlog.app.ui.theme.Slate700
import com.ironlog.app.ui.theme.Slate900
import com.ironlog.app.ui.theme.Space

/**
 * The progression furniture: achievement chips, the level bar, the streak
 * banner and the month heatmap.
 *
 * Colour discipline holds here. Gold stays on records, exactly as the PR badge
 * uses it, and the heatmap spends blue because it is a chart. Everything else
 * is chalk on slate -- a reward the user earned should read as information,
 * not as a carnival.
 */

/** One earned chip. Gold for records, outlined slate for everything else. */
@Composable
fun AchievementChip(achievement: Gamification.Achievement, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radius.chip)
    when (achievement.tone) {
        Gamification.Tone.GOLD -> Text(
            text = achievement.label,
            style = MaterialTheme.typography.labelSmall,
            color = Slate900,
            modifier = modifier
                .background(PrGold, shape)
                .padding(horizontal = Space.sm, vertical = 3.dp)
        )

        Gamification.Tone.NEUTRAL -> Text(
            text = achievement.label,
            style = MaterialTheme.typography.labelSmall,
            color = ChalkDim,
            modifier = modifier
                .background(Slate700, shape)
                .border(1.dp, Slate600, shape)
                .padding(horizontal = Space.sm, vertical = 3.dp)
        )
    }
}

/**
 * A row of chips, capped so a big session does not turn the card into a wall
 * of badges. Anything over the cap is summarised as "+2 more".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AchievementRow(
    achievements: List<Gamification.Achievement>,
    modifier: Modifier = Modifier,
    max: Int = Gamification.MAX_CHIPS_PER_CARD
) {
    if (achievements.isEmpty()) return
    val shown = achievements.take(max)
    val hidden = achievements.size - shown.size
    // Wrapping rather than a Row: three chips with long labels overflow a narrow
    // phone, and a clipped achievement is worse than one on a second line.
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalArrangement = Arrangement.spacedBy(Space.xs)
    ) {
        shown.forEach { AchievementChip(it) }
        if (hidden > 0) {
            Text(
                "+$hidden more",
                style = MaterialTheme.typography.labelSmall,
                color = ChalkFaint,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

/** A flat progress track. Used by both the level bar and the milestone banner. */
@Composable
private fun ProgressTrack(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = Chalk
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(Radius.panel))
            .background(Slate700)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(RoundedCornerShape(Radius.panel))
                .background(color)
        )
    }
}

/**
 * Level, title and the bar towards the next level.
 *
 * The XP figure is deliberately secondary: the number that means something to
 * a lifter is the level, and XP is the mechanism behind it.
 */
@Composable
fun LevelCard(
    progress: Gamification.LevelProgress,
    modifier: Modifier = Modifier
) {
    IronCard(modifier) {
        Column {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        "LEVEL ${progress.level}",
                        style = MaterialTheme.typography.labelLarge,
                        color = ChalkDim
                    )
                    Text(progress.title, style = MaterialTheme.typography.titleLarge, color = Chalk)
                }
                Text(
                    "${progress.totalXp} XP",
                    style = NumericType.small,
                    color = ChalkDim
                )
            }
            Spacer(Modifier.height(Space.md))
            ProgressTrack(progress.fraction)
            Spacer(Modifier.height(Space.sm))
            Text(
                "${progress.xpIntoLevel} / ${progress.xpForLevel} XP · ${progress.xpToNext} to level ${progress.level + 1}",
                style = MaterialTheme.typography.bodySmall,
                color = ChalkFaint
            )
        }
    }
}

/**
 * Streak and the next milestone.
 *
 * A streak the user does not have is not shown as a zero -- a prominent nought
 * reads as failure on the day someone comes back after a break, which is the
 * worst possible moment to say it.
 */
@Composable
fun StreakCard(
    streakWeeks: Int,
    finishedCount: Int,
    nextMilestone: Gamification.Milestone?,
    modifier: Modifier = Modifier
) {
    IronCard(modifier) {
        Column {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                StatTile(
                    value = if (streakWeeks > 0) "$streakWeeks" else "—",
                    label = if (streakWeeks == 1) "Week streak" else "Weeks in a row",
                    accent = if (streakWeeks > 0) PrGold else Chalk
                )
                StatTile(value = "$finishedCount", label = "Sessions logged")
            }
            if (nextMilestone != null) {
                val remaining = nextMilestone.count - finishedCount
                Spacer(Modifier.height(Space.md))
                ProgressTrack(finishedCount.toFloat() / nextMilestone.count)
                Spacer(Modifier.height(Space.sm))
                Text(
                    "Next: ${nextMilestone.name} · $remaining to go",
                    style = MaterialTheme.typography.bodySmall,
                    color = ChalkFaint
                )
            }
        }
    }
}

/** One day in the heatmap. [volumeKg] of zero means a rest day. */
data class HeatmapDay(val dayOfMonth: Int, val volumeKg: Double)

/**
 * A month of training days, shaded by volume -- the contribution-graph idea.
 *
 * Shade comes from the day's share of the heaviest day in the month, in four
 * steps. A continuous ramp looks precise but nobody can read a 3% difference in
 * opacity, and four steps are enough to see a hard week from a light one.
 */
@Composable
fun MonthHeatmap(
    days: List<HeatmapDay>,
    firstDayOffset: Int,
    modifier: Modifier = Modifier,
    cell: androidx.compose.ui.unit.Dp = 16.dp
) {
    if (days.isEmpty()) return
    val peak = days.maxOf { it.volumeKg }
    val trained = days.count { it.volumeKg > 0 }
    val description = "Training days this month: $trained of ${days.size}."

    Column(
        modifier
            .fillMaxWidth()
            .semantics { contentDescription = description }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = ChalkFaint,
                    modifier = Modifier.width(cell)
                )
            }
        }
        Spacer(Modifier.height(Space.xs))

        // Blank leading cells so the first of the month sits under its weekday.
        val cells = List(firstDayOffset) { null } + days
        cells.chunked(DAYS_IN_WEEK).forEach { week ->
            Row(
                Modifier.padding(bottom = Space.xs),
                horizontalArrangement = Arrangement.spacedBy(Space.xs)
            ) {
                week.forEach { day ->
                    Box(
                        Modifier
                            .size(cell)
                            .clip(RoundedCornerShape(Radius.panel))
                            .background(heatColour(day?.volumeKg ?: 0.0, peak))
                            .clearAndSetSemantics { }
                    )
                }
            }
        }
    }
}

private const val DAYS_IN_WEEK = 7

/** Slate for a rest day, then three steps of blue for light, solid and heavy. */
private fun heatColour(volumeKg: Double, peak: Double): Color = when {
    volumeKg <= 0.0 || peak <= 0.0 -> Slate700
    volumeKg >= peak * 0.66 -> Plate20
    volumeKg >= peak * 0.33 -> Plate20.copy(alpha = 0.66f)
    else -> Plate20.copy(alpha = 0.36f)
}
