package com.ironlog.app.core.permissions

import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.Slate800

/**
 * The permission flow used everywhere in the app:
 *
 *   1. Already granted            -> run the action immediately, no dialog.
 *   2. Not yet asked              -> show our own rationale sheet first, then the
 *                                    system dialog only if the user agrees.
 *   3. Denied once, can ask again -> rationale sheet again, restating what breaks.
 *   4. Permanently denied         -> explain, and offer a jump to app Settings.
 *
 * The rationale always precedes the system prompt. Firing the OS dialog cold, with
 * no context, is the single biggest cause of permanent denials, and a permanent
 * denial cannot be undone from inside the app.
 */
class PermissionState internal constructor(
    val permission: AppPermission,
    private val context: Context,
    private val requestSystemDialog: () -> Unit
) {
    var isGranted by mutableStateOf(permission.isGranted(context))
        internal set

    /** True once the OS reports it will no longer show the dialog. */
    var isPermanentlyDenied by mutableStateOf(false)
        internal set

    /** Drives our own explanation sheet. */
    var showRationale by mutableStateOf(false)
        internal set

    internal var pendingAction: (() -> Unit)? = null
    internal var pendingDenied: (() -> Unit)? = null

    /**
     * Entry point for callers: "do this, asking for the permission if needed".
     *
     * [onDenied] runs if the user declines, either on our sheet or in the system
     * dialog. Optional permissions use it to carry on without the feature, e.g.
     * a workout still starts when notifications are refused.
     */
    fun runWithPermission(onDenied: (() -> Unit)? = null, action: () -> Unit) {
        refresh()
        if (isGranted) {
            action()
            return
        }
        pendingAction = action
        pendingDenied = onDenied
        showRationale = true
    }

    /** Called when the user accepts the rationale sheet. */
    fun confirmRationale() {
        showRationale = false
        if (isPermanentlyDenied) openAppSettings(context) else requestSystemDialog()
    }

    fun dismissRationale() {
        showRationale = false
        pendingAction = null
        pendingDenied?.invoke()
        pendingDenied = null
    }

    fun refresh() {
        isGranted = permission.isGranted(context)
        if (isGranted) isPermanentlyDenied = false
    }

    internal fun onResult(granted: Boolean) {
        isGranted = granted
        if (granted) {
            isPermanentlyDenied = false
            pendingAction?.invoke()
        } else {
            val manifestPermission = permission.manifestPermission
            val activity = context as? Activity
            // shouldShowRequestPermissionRationale returns false *after* a denial
            // only when the system has stopped showing the dialog entirely.
            isPermanentlyDenied = manifestPermission != null && activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, manifestPermission)
            pendingDenied?.invoke()
        }
        pendingAction = null
        pendingDenied = null
    }
}

@Composable
fun rememberPermissionState(permission: AppPermission): PermissionState {
    val context = LocalContext.current
    var holder by remember { mutableStateOf<PermissionState?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> holder?.onResult(granted) }

    val state = remember(permission) {
        PermissionState(
            permission = permission,
            context = context,
            requestSystemDialog = {
                val manifestPermission = permission.manifestPermission
                if (manifestPermission == null) {
                    // Nothing to request on this OS version; treat as granted.
                    holder?.onResult(true)
                } else {
                    launcher.launch(manifestPermission)
                }
            }
        ).also { holder = it }
    }

    // Returning from system Settings is the only way a permanently denied
    // permission can become granted, so the state is re-read on every resume.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state.refresh() }

    PermissionRationaleDialog(state)
    return state
}

/**
 * Our explanation, shown before the system dialog. After a permanent denial the
 * same sheet explains what is lost and offers the route to Settings instead.
 */
@Composable
private fun PermissionRationaleDialog(state: PermissionState) {
    if (!state.showRationale) return
    val permission = state.permission
    AlertDialog(
        onDismissRequest = state::dismissRationale,
        containerColor = Slate800,
        title = { Text(permission.title, color = Chalk) },
        text = {
            Text(
                if (state.isPermanentlyDenied) {
                    permission.deniedConsequence + "\n\nYou can turn it on in Settings > Permissions."
                } else {
                    permission.rationale
                },
                color = ChalkDim
            )
        },
        confirmButton = {
            TextButton(onClick = state::confirmRationale) {
                Text(if (state.isPermanentlyDenied) "Open settings" else "Continue", color = Chalk)
            }
        },
        dismissButton = {
            TextButton(onClick = state::dismissRationale) {
                Text("Not now", color = ChalkDim)
            }
        }
    )
}
