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
    else -> Slate900
}

fun badgeIcon(itemId: String?): ImageVector? = when (itemId) {
    "badge_dumbbell" -> Icons.Default.FitnessCenter
    "badge_flame" -> Icons.Default.LocalFireDepartment
    "badge_bolt" -> Icons.Default.Bolt
    "badge_crown" -> Icons.Default.WorkspacePremium
    else -> null
}

/** The emblem shown on a store row, so a badge is not bought sight unseen. */
fun previewIcon(item: StoreCatalogue.Item): ImageVector? = when (item.category) {
    StoreCatalogue.Category.BADGE -> badgeIcon(item.id)
    StoreCatalogue.Category.WIDGET -> Icons.Default.FitnessCenter
    StoreCatalogue.Category.BACKGROUND -> null
}
