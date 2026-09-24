package com.ironlog.app.ui.screens.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.data.entity.BodyMeasurementEntity
import com.ironlog.app.data.entity.UnitSystem
import com.ironlog.app.ui.components.ChartPoint
import com.ironlog.app.ui.components.ConfirmDialog
import com.ironlog.app.ui.components.EmptyState
import com.ironlog.app.ui.components.ErrorBanner
import com.ironlog.app.ui.components.IronCard
import com.ironlog.app.ui.components.IronTextField
import com.ironlog.app.ui.components.IronTopBar
import com.ironlog.app.ui.components.LineChart
import com.ironlog.app.ui.components.LoadingBlock
import com.ironlog.app.ui.components.SectionHeader
import com.ironlog.app.ui.components.StatTile
import com.ironlog.app.ui.format.formatLength
import com.ironlog.app.ui.format.formatRelativeDay
import com.ironlog.app.ui.format.formatShortDate
import com.ironlog.app.ui.format.formatWeight
import com.ironlog.app.ui.format.lengthValue
import com.ironlog.app.ui.format.trimmed
import com.ironlog.app.ui.format.weightValue
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.ChalkFaint
import com.ironlog.app.ui.theme.Plate15
import com.ironlog.app.ui.theme.Plate20
import com.ironlog.app.ui.theme.Slate800
import com.ironlog.app.ui.theme.Space
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import com.ironlog.app.ui.components.UndoSnackbar

@Composable
fun BodyScreen(
    onBack: () -> Unit,
    viewModel: BodyViewModel = hiltViewModel()
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<BodyMeasurementEntity?>(null) }
    val unit = data.unit

    val undo by viewModel.undo.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    UndoSnackbar(snackbar, undo) { viewModel.consumeUndo() }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        IronTopBar(title = "Body measurements", onBack = onBack) {
            IconButton(onClick = viewModel::openForm) {
                Icon(Icons.Default.Add, contentDescription = "Add measurement", tint = Chalk)
            }
        }

        when {
            data.isLoading -> LoadingBlock(Modifier.fillMaxSize())
            data.measurements.isEmpty() -> EmptyState(
                icon = Icons.Default.MonitorWeight,
                title = "No measurements yet",
                body = "Log your bodyweight and tape measurements to see how they trend over time.",
                actionLabel = "Add measurement",
                onAction = viewModel::openForm
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = Space.lg),
                verticalArrangement = Arrangement.spacedBy(Space.md)
            ) {
                item { ErrorBanner(error) }
                item { LatestSummary(data.measurements.first(), unit) }

                val ascending = data.measurements.sortedBy { it.takenAt }
                val weightPoints = ascending.mapNotNull { m ->
                    m.weightKg?.let { ChartPoint(formatShortDate(m.takenAt), unit.weightValue(it).toFloatOrNull() ?: 0f) }
                }
                val waistPoints = ascending.mapNotNull { m ->
                    m.waistCm?.let { ChartPoint(formatShortDate(m.takenAt), unit.lengthValue(it).toFloatOrNull() ?: 0f) }
                }
                if (weightPoints.size >= 2) {
                    item {
                        Column {
                        SectionHeader("Bodyweight")
                        IronCard {
                            LineChart(
                                points = weightPoints,
                                color = Plate20,
                                valueFormatter = { "${it.toDouble().trimmed(1)} ${unit.weightSuffix}" },
                                description = "Bodyweight over time"
                            )
                        }
                        }
                    }
                }
                if (waistPoints.size >= 2) {
                    item {
                        Column {
                        SectionHeader("Waist")
                        IronCard {
                            LineChart(
                                points = waistPoints,
                                color = Plate15,
                                valueFormatter = { "${it.toDouble().trimmed(1)} ${unit.lengthSuffix}" },
                                description = "Waist measurement over time"
                            )
                        }
                        }
                    }
                }
                item { SectionHeader("History") }
                items(data.measurements, key = { it.id }) { m ->
                    MeasurementRow(m, unit, onDelete = { pendingDelete = m })
                }
                item { Spacer(Modifier.height(Space.section)) }
            }
        }
    }

    if (form.isOpen) {
        AddMeasurementDialog(
            form = form,
            unit = unit,
            onValueChange = viewModel::onValueChange,
            onNoteChange = viewModel::onNoteChange,
            onSave = viewModel::save,
            onDismiss = viewModel::closeForm
        )
    }

    pendingDelete?.let { item ->
        ConfirmDialog(
            title = "Delete measurement?",
            body = "The entry from ${formatRelativeDay(item.takenAt)} will be removed.",
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.delete(item)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(Space.lg))
    }
}

@Composable
private fun LatestSummary(latest: BodyMeasurementEntity, unit: UnitSystem) {
    IronCard {
        Column {
            Text("Latest · ${formatRelativeDay(latest.takenAt)}", style = MaterialTheme.typography.bodySmall, color = ChalkDim)
            Spacer(Modifier.height(Space.md))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatTile(unit.formatWeight(latest.weightKg), "Weight")
                StatTile(latest.bodyFatPercent?.let { "${it.trimmed(1)}%" } ?: "—", "Body fat")
                StatTile(unit.formatLength(latest.waistCm), "Waist")
            }
        }
    }
}

@Composable
private fun MeasurementRow(m: BodyMeasurementEntity, unit: UnitSystem, onDelete: () -> Unit) {
    val parts = buildList {
        m.weightKg?.let { add(unit.formatWeight(it)) }
        m.bodyFatPercent?.let { add("${it.trimmed(1)}% fat") }
        m.chestCm?.let { add("Chest ${unit.formatLength(it)}") }
        m.waistCm?.let { add("Waist ${unit.formatLength(it)}") }
        m.hipsCm?.let { add("Hips ${unit.formatLength(it)}") }
        m.leftArmCm?.let { add("L arm ${unit.formatLength(it)}") }
        m.rightArmCm?.let { add("R arm ${unit.formatLength(it)}") }
        m.thighCm?.let { add("Thigh ${unit.formatLength(it)}") }
        m.calfCm?.let { add("Calf ${unit.formatLength(it)}") }
        m.neckCm?.let { add("Neck ${unit.formatLength(it)}") }
    }
    IronCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(formatRelativeDay(m.takenAt), style = MaterialTheme.typography.titleMedium, color = Chalk)
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = ChalkDim)
                if (m.note.isNotBlank()) {
                    Text(m.note, style = MaterialTheme.typography.bodySmall, color = ChalkFaint)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete measurement", tint = ChalkFaint)
            }
        }
    }
}

@Composable
private fun AddMeasurementDialog(
    form: MeasurementForm,
    unit: UnitSystem,
    onValueChange: (MeasureField, String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!form.isSaving) onDismiss() },
        containerColor = Slate800,
        title = { Text("Add measurement", color = Chalk) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Text("Fill in any you measured; the rest can stay blank.", color = ChalkDim, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(Space.md))
                ErrorBanner(form.error)
                MeasureField.entries.forEach { field ->
                    val suffix = when (field.kind) {
                        MeasureField.Kind.WEIGHT -> unit.weightSuffix
                        MeasureField.Kind.LENGTH -> unit.lengthSuffix
                        MeasureField.Kind.PERCENT -> null
                    }
                    IronTextField(
                        value = form.values[field].orEmpty(),
                        onValueChange = { onValueChange(field, it) },
                        label = if (suffix != null) "${field.label} ($suffix)" else field.label,
                        error = form.fieldErrors[field],
                        enabled = !form.isSaving,
                        keyboardType = KeyboardType.Decimal
                    )
                    Spacer(Modifier.height(Space.sm))
                }
                IronTextField(
                    value = form.note,
                    onValueChange = onNoteChange,
                    label = "Note",
                    singleLine = false,
                    enabled = !form.isSaving
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !form.isSaving) {
                Text(if (form.isSaving) "Saving…" else "Save", color = Chalk)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !form.isSaving) { Text("Cancel", color = ChalkDim) }
        }
    )
}
