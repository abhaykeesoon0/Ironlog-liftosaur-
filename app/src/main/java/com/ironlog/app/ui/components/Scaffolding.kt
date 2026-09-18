package com.ironlog.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ironlog.app.data.entity.RecordType
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.ChalkFaint
import com.ironlog.app.ui.theme.Danger
import com.ironlog.app.ui.theme.PrGold
import com.ironlog.app.ui.theme.Radius
import com.ironlog.app.ui.theme.Slate600
import com.ironlog.app.ui.theme.Slate700
import com.ironlog.app.ui.theme.Slate800
import com.ironlog.app.ui.theme.Slate900
import com.ironlog.app.ui.theme.Space
import com.ironlog.app.ui.theme.Touch

/**
 * Top bar for pushed screens. Built from a Row rather than Material's TopAppBar
 * so it needs no experimental opt-in and sits on the same surface as the content.
 */
@Composable
fun IronTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = if (onBack != null) Space.xs else Space.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Chalk)
            }
            Spacer(Modifier.width(Space.xs))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = Chalk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        actions()
    }
}

/** Large page title used at the top of the bottom-tab screens. */
@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    androidx.compose.foundation.layout.Column(modifier.padding(top = Space.xl, bottom = Space.lg)) {
        Text(
            title,
            style = MaterialTheme.typography.headlineLarge,
            color = Chalk,
            modifier = Modifier.semantics { heading() }
        )
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = ChalkDim)
        }
    }
}

/**
 * Confirmation for destructive actions. The confirm button names the action
 * ("Delete workout") rather than saying "OK", so the consequence is on the button.
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Slate800,
        title = { Text(title, color = Chalk) },
        text = { Text(body, color = ChalkDim) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) Danger else Chalk)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = ChalkDim) }
        }
    )
}

/** Horizontally scrolling single-select filter chips. `null` is the "All" option. */
@Composable
fun <T> FilterChipRow(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T?) -> Unit,
    modifier: Modifier = Modifier,
    allLabel: String = "All"
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        item { IronChip(allLabel, selected == null) { onSelect(null) } }
        items(options) { option ->
            IronChip(label(option), selected == option) { onSelect(if (selected == option) null else option) }
        }
    }
}

@Composable
fun IronChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        shape = RoundedCornerShape(Radius.chip),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Slate800,
            labelColor = ChalkDim,
            selectedContainerColor = Chalk,
            selectedLabelColor = Slate900
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Slate600,
            selectedBorderColor = Chalk
        ),
        modifier = Modifier.heightIn(min = Touch.min)
    )
}

/** Gold personal-record badge. Colour is reserved for data, and a PR is data. */
@Composable
fun PrBadge(type: RecordType?, modifier: Modifier = Modifier) {
    type ?: return
    val label = when (type) {
        RecordType.HEAVIEST_WEIGHT -> "PR · Weight"
        RecordType.BEST_ESTIMATED_1RM -> "PR · 1RM"
        RecordType.BEST_SET_VOLUME -> "PR · Volume"
        RecordType.MOST_REPS -> "PR · Reps"
        RecordType.BEST_SESSION_VOLUME -> "PR · Session"
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = Slate900,
        modifier = modifier
            .background(PrGold, RoundedCornerShape(Radius.chip))
            .padding(horizontal = Space.sm, vertical = 2.dp)
    )
}

/** Muted inline caption, e.g. under a stat or beside a toggle. */
@Composable
fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = ChalkFaint, modifier = modifier)
}

/** Thin divider on the slate ramp. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 1.dp, max = 1.dp)
            .background(Slate700)
    )
}
