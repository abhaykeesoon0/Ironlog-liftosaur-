package com.ironlog.app.ui.screens.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ironlog.app.core.permissions.AppPermission
import com.ironlog.app.core.permissions.rememberPermissionState
import com.ironlog.app.ui.components.ConfirmDialog
import com.ironlog.app.ui.components.ErrorBanner
import com.ironlog.app.ui.components.Hairline
import com.ironlog.app.ui.components.IronCard
import com.ironlog.app.ui.components.IronTextField
import com.ironlog.app.ui.components.PrimaryButton
import com.ironlog.app.ui.components.ScreenTitle
import com.ironlog.app.ui.components.SecondaryButton
import com.ironlog.app.ui.components.SectionHeader
import com.ironlog.app.ui.components.StatTile
import com.ironlog.app.ui.format.formatLength
import com.ironlog.app.ui.format.formatWeight
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.ChalkFaint
import com.ironlog.app.ui.theme.Slate700
import com.ironlog.app.ui.theme.Space
import java.io.File

@Composable
fun ProfileScreen(
    onOpenBody: () -> Unit,
    onOpenPhotos: () -> Unit,
    onOpenCardio: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLibrary: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    var confirmSignOut by remember { mutableStateOf(false) }

    val camera = rememberPermissionState(AppPermission.CAMERA)
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        viewModel.onAvatarCaptured(ok)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.lg)
    ) {
        ScreenTitle("Profile")

        ErrorBanner(form.error)
        if (form.error != null) Spacer(Modifier.height(Space.md))

        val user = data.user
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(
                path = user?.avatarPath,
                name = user?.displayName.orEmpty(),
                busy = form.isAvatarBusy,
                onClick = {
                    camera.runWithPermission {
                        viewModel.prepareAvatarCapture()?.let { takePicture.launch(it) }
                    }
                }
            )
            Spacer(Modifier.width(Space.lg))
            Column(Modifier.weight(1f)) {
                Text(
                    user?.displayName.orEmpty(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Chalk
                )
                Text(user?.email.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = ChalkDim)
                if (user?.avatarPath != null) {
                    TextButton(onClick = viewModel::removeAvatar, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text("Remove photo", color = ChalkFaint, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (!form.isEditing) {
                IconButton(onClick = viewModel::startEditing) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit profile", tint = Chalk)
                }
            }
        }

        Spacer(Modifier.height(Space.xl))

        if (form.isEditing) {
            IronCard {
                Column {
                    IronTextField(
                        value = form.name,
                        onValueChange = viewModel::onNameChange,
                        label = "Name",
                        error = form.nameError,
                        enabled = !form.isSaving
                    )
                    Spacer(Modifier.height(Space.md))
                    IronTextField(
                        value = form.bodyweight,
                        onValueChange = viewModel::onBodyweightChange,
                        label = "Bodyweight (${data.unit.weightSuffix})",
                        error = form.bodyweightError,
                        enabled = !form.isSaving,
                        keyboardType = KeyboardType.Decimal
                    )
                    Spacer(Modifier.height(Space.md))
                    IronTextField(
                        value = form.height,
                        onValueChange = viewModel::onHeightChange,
                        label = "Height (${data.unit.lengthSuffix})",
                        error = form.heightError,
                        enabled = !form.isSaving,
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    )
                    Spacer(Modifier.height(Space.lg))
                    PrimaryButton(text = "Save", onClick = viewModel::save, loading = form.isSaving)
                    Spacer(Modifier.height(Space.sm))
                    SecondaryButton(text = "Cancel", onClick = viewModel::cancelEditing, enabled = !form.isSaving)
                }
            }
        } else {
            IronCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatTile(value = data.workoutCount.toString(), label = "Workouts")
                    StatTile(value = data.recordCount.toString(), label = "Records")
                    StatTile(value = data.unit.formatWeight(user?.bodyweightKg), label = "Bodyweight")
                }
            }
            user?.heightCm?.let {
                Spacer(Modifier.height(Space.sm))
                Text("Height ${data.unit.formatLength(it)}", style = MaterialTheme.typography.bodySmall, color = ChalkFaint)
            }
        }

        Spacer(Modifier.height(Space.xl))
        SectionHeader("Track")
        NavRow(Icons.Default.MonitorWeight, "Body measurements", onOpenBody, "Weight, body fat and tape measurements")
        Hairline()
        NavRow(Icons.Default.PhotoLibrary, "Progress photos", onOpenPhotos, "Private to this phone")
        Hairline()
        NavRow(Icons.AutoMirrored.Filled.DirectionsRun, "Outdoor cardio", onOpenCardio, "GPS runs, walks and rides")
        Hairline()
        NavRow(Icons.Default.FitnessCenter, "Exercise library", onOpenLibrary, "Browse movements or add your own")

        Spacer(Modifier.height(Space.xl))
        SectionHeader("App")
        NavRow(Icons.Default.Settings, "Settings", onOpenSettings, "Units, rest timer, reminders, security, export")
        Hairline()
        NavRow(Icons.AutoMirrored.Filled.Logout, "Sign out", { confirmSignOut = true })

        Spacer(Modifier.height(Space.section))
    }

    if (confirmSignOut) {
        ConfirmDialog(
            title = "Sign out?",
            body = "Your data stays on this phone. You'll need your password to get back in.",
            confirmLabel = "Sign out",
            onConfirm = {
                confirmSignOut = false
                viewModel.signOut()
            },
            onDismiss = { confirmSignOut = false }
        )
    }
}

@Composable
private fun Avatar(path: String?, name: String, busy: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(Slate700)
            .clickable(role = Role.Button, onClickLabel = "Take a profile photo", onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when {
            busy -> CircularProgressIndicator(Modifier.size(24.dp), color = ChalkDim, strokeWidth = 2.dp)
            path != null -> AsyncImage(
                model = File(path),
                contentDescription = "Profile photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            name.isNotBlank() -> Text(
                initials(name),
                style = MaterialTheme.typography.headlineMedium,
                color = Chalk
            )
            else -> Icon(Icons.Default.CameraAlt, contentDescription = null, tint = ChalkDim)
        }
    }
}

private fun initials(name: String): String =
    name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
