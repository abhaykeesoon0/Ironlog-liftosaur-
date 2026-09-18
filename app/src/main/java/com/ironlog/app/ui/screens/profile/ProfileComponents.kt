package com.ironlog.app.ui.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.ChalkFaint
import com.ironlog.app.ui.theme.NumericType
import com.ironlog.app.ui.theme.Slate500
import com.ironlog.app.ui.theme.Slate600
import com.ironlog.app.ui.theme.Slate700
import com.ironlog.app.ui.theme.Slate900
import com.ironlog.app.ui.theme.Space
import com.ironlog.app.ui.theme.Touch

/** A tappable row with a leading icon, title, optional caption and a chevron. */
@Composable
internal fun NavRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Touch.min + 8.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = ChalkDim, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(Space.lg))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Chalk)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ChalkFaint) }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = ChalkFaint)
    }
}

@Composable
internal fun IronSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Slate900,
            checkedTrackColor = Chalk,
            checkedBorderColor = Chalk,
            uncheckedThumbColor = ChalkFaint,
            uncheckedTrackColor = Slate700,
            uncheckedBorderColor = Slate600,
            disabledUncheckedTrackColor = Slate700,
            disabledUncheckedThumbColor = Slate500,
            disabledUncheckedBorderColor = Slate600
        )
    )
}

/** Title + caption on the left, a switch on the right. The whole row toggles. */
@Composable
internal fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    caption: String? = null,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Touch.min)
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
            .padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) Chalk else ChalkFaint)
            caption?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ChalkFaint) }
        }
        Spacer(Modifier.width(Space.md))
        // The row handles the click so the switch itself is display-only.
        IronSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** Minus / value / plus. Buttons carry content descriptions naming the setting. */
@Composable
internal fun Stepper(
    label: String,
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    caption: String? = null,
    canDecrement: Boolean = true,
    canIncrement: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = Touch.min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = Chalk)
            caption?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ChalkFaint) }
        }
        IconButton(onClick = onDecrement, enabled = canDecrement) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease $label", tint = if (canDecrement) Chalk else ChalkFaint)
        }
        Text(
            value,
            style = NumericType.setCell,
            color = Chalk,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 64.dp)
        )
        IconButton(onClick = onIncrement, enabled = canIncrement) {
            Icon(Icons.Default.Add, contentDescription = "Increase $label", tint = if (canIncrement) Chalk else ChalkFaint)
        }
    }
}
