package com.ironlog.app.ui.screens.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ironlog.app.data.entity.PlateSet
import com.ironlog.app.data.entity.UnitSystem
import com.ironlog.app.domain.usecase.TrainingMath
import com.ironlog.app.ui.components.Caption
import com.ironlog.app.ui.components.IronTextField
import com.ironlog.app.ui.format.formatWeight
import com.ironlog.app.ui.format.parseWeightToKg
import com.ironlog.app.ui.format.trimmed
import com.ironlog.app.ui.format.weightValue
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.NumericType
import com.ironlog.app.ui.theme.Plate10
import com.ironlog.app.ui.theme.Plate15
import com.ironlog.app.ui.theme.Plate20
import com.ironlog.app.ui.theme.Plate25
import com.ironlog.app.ui.theme.Plate2_5
import com.ironlog.app.ui.theme.Plate5
import com.ironlog.app.ui.theme.Slate500
import com.ironlog.app.ui.theme.Slate600
import com.ironlog.app.ui.theme.Slate800
import com.ironlog.app.ui.theme.Space
import com.ironlog.app.ui.theme.Warning

/**
 * What to load on each side of the bar. Plates are drawn in their competition
 * colours, which is exactly the "colour is data" use the palette reserves them for.
 * Calculation stays in kilograms (gym plates are kilo plates); the target and the
 * result are shown in the user's unit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlateCalculatorSheet(
    exerciseName: String,
    barWeightKg: Double,
    initialTargetKg: Double?,
    unit: UnitSystem,
    plateSet: PlateSet,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember { mutableStateOf(unit.weightValue(initialTargetKg)) }
    val targetKg = unit.parseWeightToKg(text)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Slate800) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = Space.lg)
                .padding(bottom = Space.xl)
        ) {
            Text("Plate calculator", style = MaterialTheme.typography.titleLarge, color = Chalk)
            Caption("$exerciseName · bar ${unit.formatWeight(barWeightKg)}")
            Spacer(Modifier.height(Space.lg))
            IronTextField(
                value = text,
                onValueChange = { text = it.take(7) },
                label = "Target weight (${unit.weightSuffix})",
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done,
                error = if (text.isNotBlank() && targetKg == null) "Enter a weight" else null
            )
            Spacer(Modifier.height(Space.lg))

            when {
                targetKg == null -> Caption("Enter the total weight you want on the bar.")
                targetKg < barWeightKg -> Caption("That's less than the bar itself (${unit.formatWeight(barWeightKg)}).")
                else -> {
                    val loading = TrainingMath.platesPerSide(targetKg, barWeightKg, TrainingMath.platesFor(plateSet))
                    Text("Each side", style = MaterialTheme.typography.labelMedium, color = ChalkDim)
                    Spacer(Modifier.height(Space.sm))
                    if (loading.plates.isEmpty()) {
                        Caption("Just the bar.")
                    } else {
                        PlateStack(loading.plates, plateSet)
                        Spacer(Modifier.height(Space.sm))
                        Text(
                            loading.plates.groupBy { it }.entries.joinToString("  ·  ") { (plate, list) ->
                                "${list.size} × ${TrainingMath.plateLabel(plateSet, plate)}"
                            },
                            style = NumericType.small,
                            color = Chalk
                        )
                    }
                    Spacer(Modifier.height(Space.md))
                    if (loading.isExact) {
                        Text(
                            "Total ${unit.formatWeight(loading.achievableTotalKg)}",
                            style = NumericType.stat,
                            color = Chalk
                        )
                    } else {
                        Text(
                            "Closest is ${unit.formatWeight(loading.achievableTotalKg)}",
                            style = NumericType.stat,
                            color = Warning
                        )
                        Caption("${unit.formatWeight(loading.remainderKg * 2)} can't be made with the plates you have.")
                    }
                }
            }
        }
    }
}

@Composable
private fun PlateStack(plates: List<Double>, plateSet: PlateSet) {
    val description = plates.joinToString { TrainingMath.plateLabel(plateSet, it) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .semantics { contentDescription = "Plates each side: $description" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Bar sleeve, then the plates from heaviest (inside) outwards.
        Box(Modifier.width(28.dp).height(10.dp).background(Slate500))
        plates.forEach { plate ->
            val (color, heightFraction) = plateStyle(plate)
            Box(
                Modifier
                    .padding(horizontal = 1.dp)
                    .width(if (plate >= 10) 14.dp else 10.dp)
                    .height(96.dp * heightFraction)
                    .background(color, RoundedCornerShape(3.dp))
                    .border(1.dp, Slate600, RoundedCornerShape(3.dp))
            )
        }
        Box(Modifier.width(14.dp).height(10.dp).background(Slate500))
    }
}

/** Competition colour and relative diameter for each plate denomination. */
private fun plateStyle(kg: Double): Pair<Color, Float> = when {
    kg >= 25 -> Plate25 to 1f
    kg >= 20 -> Plate20 to 1f
    kg >= 15 -> Plate15 to 0.9f
    kg >= 10 -> Plate10 to 0.8f
    kg >= 5 -> Plate5 to 0.55f
    kg >= 2.5 -> Plate2_5 to 0.45f
    else -> Plate5 to 0.38f
}
