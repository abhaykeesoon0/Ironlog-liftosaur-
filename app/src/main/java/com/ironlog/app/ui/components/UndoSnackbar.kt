package com.ironlog.app.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Something just removed, and the way to put it back.
 *
 * A delete in this app is never final at the moment of the tap. The row goes,
 * a bar offers to bring it back for a few seconds, and only when the bar has
 * gone is the removal real. That is cheaper than a confirmation dialog on
 * every delete and safer than none, and it is what people expect from a phone.
 */
data class Undo(val text: String, val restore: suspend () -> Unit)

/**
 * Shows [undo] on [host] with an Undo action, runs the restore if it is
 * pressed, and tells the caller when the bar has gone either way.
 */
@Composable
fun UndoSnackbar(host: SnackbarHostState, undo: Undo?, onConsumed: () -> Unit) {
    LaunchedEffect(undo) {
        val current = undo ?: return@LaunchedEffect
        val result = host.showSnackbar(
            message = current.text,
            actionLabel = "Undo",
            duration = SnackbarDuration.Short,
            withDismissAction = false
        )
        if (result == SnackbarResult.ActionPerformed) current.restore()
        onConsumed()
    }
}
