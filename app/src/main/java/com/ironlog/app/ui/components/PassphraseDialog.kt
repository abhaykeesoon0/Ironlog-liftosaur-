package com.ironlog.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.ChalkFaint
import com.ironlog.app.ui.theme.Slate700
import com.ironlog.app.ui.theme.Space

/**
 * Asks for a passphrase and hands it over as a CharArray, which the caller
 * wipes when it is done.
 *
 * A Compose text field holds its value as a String, which cannot be cleared
 * from the heap, so the passphrase exists as a String for as long as the
 * dialog is open. That is the same trade the sign-in screen makes: the field
 * is short-lived, and the copy that travels through the app is the array,
 * which is zeroed after use.
 */
@Composable
fun PassphraseDialog(
    title: String,
    body: String,
    confirmLabel: String,
    minLength: Int,
    onConfirm: (CharArray) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf("") }
    val ready = value.length >= minLength

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Chalk) },
        text = {
            Column {
                Text(body, style = MaterialTheme.typography.bodyMedium, color = ChalkDim)
                Spacer(Modifier.height(Space.md))
                IronTextField(
                    value = value,
                    onValueChange = { value = it.take(128) },
                    label = "Passphrase",
                    isPassword = true,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                )
                if (minLength > 1) {
                    Spacer(Modifier.height(Space.xs))
                    Caption(if (ready) "Long enough." else "${minLength - value.length} more characters needed.")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = ready,
                onClick = {
                    val chars = value.toCharArray()
                    value = ""
                    onConfirm(chars)
                }
            ) { Text(confirmLabel, color = if (ready) Chalk else ChalkFaint) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = ChalkDim) }
        },
        containerColor = Slate700
    )
}
