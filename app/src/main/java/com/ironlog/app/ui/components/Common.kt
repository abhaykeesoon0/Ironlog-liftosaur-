package com.ironlog.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.ChalkFaint
import com.ironlog.app.ui.theme.Danger
import com.ironlog.app.ui.theme.NumericType
import com.ironlog.app.ui.theme.Radius
import com.ironlog.app.ui.theme.Slate600
import com.ironlog.app.ui.theme.Slate700
import com.ironlog.app.ui.theme.Slate800
import com.ironlog.app.ui.theme.Space
import com.ironlog.app.ui.theme.Touch

/**
 * Buttons are built from scratch rather than themed Material buttons, because the
 * app needs a taller-than-default primary (56dp) that is comfortable to hit with
 * chalky hands, and a strict rule that only one colour ever signals "action".
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(Touch.primaryButton),
        enabled = enabled && !loading,
        shape = RoundedCornerShape(Radius.control),
        color = if (enabled) Chalk else Slate700,
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary else ChalkFaint
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    leadingIcon?.let {
                        Icon(it, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(Space.sm))
                    }
                    Text(text, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(Touch.min),
        enabled = enabled,
        shape = RoundedCornerShape(Radius.control),
        color = Color.Transparent,
        contentColor = if (enabled) Chalk else ChalkFaint,
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate600)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                leadingIcon?.let {
                    Icon(it, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Space.sm))
                }
                Text(text, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * Text input with inline validation.
 *
 * The error message is announced through [supportingText] rather than being drawn
 * as a separate Text, so TalkBack reads it as part of the field instead of as
 * unrelated floating text. The field also expands rather than shifting the layout
 * when an error appears.
 */
@Composable
fun IronTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    trailingContent: @Composable (() -> Unit)? = null
) {
    var passwordVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = ChalkFaint) } },
        isError = error != null,
        supportingText = error?.let { { Text(it, color = Danger) } },
        singleLine = singleLine,
        shape = RoundedCornerShape(Radius.control),
        visualTransformation = when {
            isPassword && !passwordVisible -> PasswordVisualTransformation()
            else -> VisualTransformation.None
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
            imeAction = imeAction
        ),
        trailingIcon = when {
            isPassword -> {
                {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password"
                            else "Show password",
                            tint = ChalkDim
                        )
                    }
                }
            }
            trailingContent != null -> trailingContent
            else -> null
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Slate800,
            unfocusedContainerColor = Slate800,
            disabledContainerColor = Slate800,
            focusedBorderColor = Chalk,
            unfocusedBorderColor = Slate600,
            errorBorderColor = Danger,
            focusedLabelColor = Chalk,
            unfocusedLabelColor = ChalkFaint,
            cursorColor = Chalk
        )
    )
}

@Composable
fun IronCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(Radius.card)
    val base = modifier
        .fillMaxWidth()
        .background(Slate800, shape)
        .border(1.dp, Slate600.copy(alpha = 0.6f), shape)

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = Slate800,
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate600.copy(alpha = 0.6f))
        ) {
            Box(Modifier.padding(Space.lg)) { content() }
        }
    } else {
        Box(base.padding(Space.lg)) { content() }
    }
}

/** A labelled figure. The number is the hero; the label stays quiet underneath. */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = Chalk
) {
    Column(modifier) {
        Text(value, style = NumericType.stat, color = accent)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = ChalkDim)
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Space.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Chalk)
        action?.invoke()
    }
}

/**
 * Empty states name what is missing and give one way to fix it. A blank screen
 * with a shrug icon tells the user nothing about what to do next.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ChalkFaint,
            modifier = Modifier.size(44.dp)
        )
        Spacer(Modifier.height(Space.lg))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = Chalk,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Space.sm))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = ChalkDim,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Space.xl))
            PrimaryButton(text = actionLabel, onClick = onAction)
        }
    }
}

/** Inline error banner used when an operation fails but the screen still works. */
@Composable
fun ErrorBanner(message: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = message != null,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(Danger.copy(alpha = 0.12f), RoundedCornerShape(Radius.control))
                .border(1.dp, Danger.copy(alpha = 0.4f), RoundedCornerShape(Radius.control))
                .padding(Space.md)
        ) {
            Text(
                text = message.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = Danger
            )
        }
    }
}

@Composable
fun LoadingBlock(modifier: Modifier = Modifier, label: String? = null) {
    Column(
        modifier = modifier.fillMaxWidth().heightIn(min = 120.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp).clearAndSetSemantics { },
            strokeWidth = 2.dp,
            color = ChalkDim
        )
        label?.let {
            Spacer(Modifier.height(Space.md))
            Text(it, style = MaterialTheme.typography.bodySmall, color = ChalkDim)
        }
    }
}
