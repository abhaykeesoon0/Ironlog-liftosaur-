package com.ironlog.app.ui.screens.rewards

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.ironlog.app.domain.usecase.StoreCatalogue
import com.ironlog.app.ui.theme.Slate900
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star

/**
 * The look of a bought item: which colour a background paints, and which
 * emblem a badge shows.
 *
 * Kept beside the UI rather than in the catalogue so the domain list stays free
 * of Compose types, and kept as an exhaustive lookup with a fallback so an
 * unknown id -- an item removed in a later version, still owned by someone --
 * degrades to the default instead of crashing.
 */

/**
 * Every option is dark. The app is dark-only by design: it is used in gyms,
 * often at night, and a light background would halve the contrast of the chart
 * palette. Buying a background changes the shade, never that decision.
 */
fun backgroundColour(itemId: String?): Color = when (itemId) {
    "bg_charcoal" -> Color(0xFF131313)
    "bg_navy" -> Color(0xFF0B1220)
    "bg_ink" -> Color(0xFF08090A)
    "bg_plum" -> Color(0xFF140E16)
    "bg_forest" -> Color(0xFF0A130E)
    "bg_oxblood" -> Color(0xFF160A0C)
    "bg_midnight" -> Color(0xFF060914)
    else -> Slate900
}

fun badgeIcon(itemId: String?): ImageVector? = when (itemId) {
    "badge_dumbbell" -> Icons.Default.FitnessCenter
    "badge_flame" -> Icons.Default.LocalFireDepartment
    "badge_bolt" -> Icons.Default.Bolt
    "badge_crown" -> Icons.Default.WorkspacePremium
    "badge_star" -> Icons.Default.Star
    "badge_shield" -> Icons.Default.Shield
    "badge_trophy" -> Icons.Default.EmojiEvents
    "badge_diamond" -> Icons.Default.Diamond
    else -> null
}

/** The words a title puts under the name. The catalogue name is the title itself. */
fun titleText(itemId: String?): String? = itemId?.let { StoreCatalogue.byId(it) }
    ?.takeIf { it.category == StoreCatalogue.Category.TITLE }?.name

/** The emblem shown on a store row, so a badge is not bought sight unseen. */
fun previewIcon(item: StoreCatalogue.Item): ImageVector? = when (item.category) {
    StoreCatalogue.Category.BADGE -> badgeIcon(item.id)
    StoreCatalogue.Category.WIDGET -> Icons.Default.FitnessCenter
    StoreCatalogue.Category.TITLE -> Icons.Default.Badge
    StoreCatalogue.Category.BACKGROUND -> null
}
