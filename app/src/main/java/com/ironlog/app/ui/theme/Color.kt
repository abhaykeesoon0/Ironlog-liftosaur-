package com.ironlog.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette notes
 * -------------
 * The app is read mid-set, in gym lighting, at arm's length. Everything is built
 * on a cool slate ramp so the numbers carry the contrast rather than the chrome.
 *
 * Chalk (near-white) is the only action colour. The saturated colours are taken
 * from Olympic bumper plates and are *reserved for data*: charts, the plate
 * calculator, PR badges. They never appear as decoration.
 */

// Slate ramp -- base surfaces
val Slate900 = Color(0xFF0E1216) // app background
val Slate800 = Color(0xFF151A20) // cards, sheets
val Slate700 = Color(0xFF1C232B) // raised rows, input fields
val Slate600 = Color(0xFF263039) // hairlines, dividers, outlines
val Slate500 = Color(0xFF3A4653) // disabled surfaces

// Text ramp
val Chalk = Color(0xFFF2F5F7) // primary text + primary buttons
val ChalkDim = Color(0xFFA7B4C0) // secondary text
val ChalkFaint = Color(0xFF6B7987) // tertiary text, placeholders

// Bumper plate colours -- data only
val Plate25 = Color(0xFFE03131) // red
val Plate20 = Color(0xFF1C7ED6) // blue
val Plate15 = Color(0xFFF2B705) // yellow
val Plate10 = Color(0xFF2F9E44) // green
val Plate5 = Color(0xFFE9ECEF) // white
val Plate2_5 = Color(0xFF212529) // black

// Semantic
val Success = Plate10
val Warning = Plate15
val Danger = Plate25
val Info = Plate20
val PrGold = Color(0xFFFFC94D) // personal record highlight

/** Ordered palette used by charts that need N distinguishable series. */
val ChartSeries = listOf(Plate20, Plate15, Plate10, Plate25, Color(0xFF9775FA), Color(0xFF15AABF))
