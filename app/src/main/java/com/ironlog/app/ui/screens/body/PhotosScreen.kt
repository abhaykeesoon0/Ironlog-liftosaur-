package com.ironlog.app.ui.screens.body

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ironlog.app.core.permissions.AppPermission
import com.ironlog.app.core.permissions.rememberPermissionState
import com.ironlog.app.data.entity.PhotoPose
import com.ironlog.app.data.entity.ProgressPhotoEntity
import com.ironlog.app.data.entity.UnitSystem
import com.ironlog.app.ui.components.Caption
import com.ironlog.app.ui.components.ConfirmDialog
import com.ironlog.app.ui.components.EmptyState
import com.ironlog.app.ui.components.ErrorBanner
import com.ironlog.app.ui.components.FilterChipRow
import com.ironlog.app.ui.components.IronChip
import com.ironlog.app.ui.components.IronTopBar
import com.ironlog.app.ui.components.LoadingBlock
import com.ironlog.app.ui.components.PrimaryButton
import com.ironlog.app.ui.components.SecondaryButton
import com.ironlog.app.ui.format.formatFullDate
import com.ironlog.app.ui.format.formatShortDate
import com.ironlog.app.ui.format.formatWeight
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.Radius
import com.ironlog.app.ui.theme.Slate700
import com.ironlog.app.ui.theme.Slate900
import com.ironlog.app.ui.theme.Space
import java.io.File
import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Compare
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.ironlog.app.ui.components.PhotoCompareDialog

@Composable
fun PhotosScreen(
    onBack: () -> Unit,
    viewModel: PhotosViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unit by viewModel.unit.collectAsStateWithLifecycle()

    // No screenshots and no thumbnail in the recents switcher while body
    // photos are on screen. The flag is set for this screen only and
    // cleared on the way out, so the rest of the app can still be captured.
    // Unless the user has asked for the whole app to be uncapturable, in
    // which case the flag is the activity's to keep.
    val window = (LocalContext.current as? Activity)?.window
    val keepSecure by viewModel.screenshotsBlocked.collectAsStateWithLifecycle()
    DisposableEffect(window, keepSecure) {
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (!keepSecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    val camera = rememberPermissionState(AppPermission.CAMERA)
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        viewModel.onCaptureResult(ok)
    }
    // The system photo picker grants access to the chosen image only, so no
    // storage permission is needed for this path.
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onPicked(uri)
    }

    // Compare mode: pick two tiles, the earlier becomes "before".
    var comparing by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<List<ProgressPhotoEntity>>(emptyList()) }
    if (picked.size == 2) {
        val (before, after) = picked.sortedBy { it.takenAt }
        PhotoCompareDialog(
            beforePath = before.filePath,
            beforeLabel = "${before.pose.label} \u00b7 ${formatShortDate(before.takenAt)}",
            afterPath = after.filePath,
            afterLabel = "${after.pose.label} \u00b7 ${formatShortDate(after.takenAt)}",
            onDismiss = { picked = emptyList(); comparing = false }
        )
    }

    Column(Modifier.fillMaxSize()) {
        IronTopBar(title = "Progress photos", onBack = onBack)

        Column(Modifier.padding(horizontal = Space.lg)) {
            Caption("Photos stay private on this phone. They are never added to your gallery or uploaded.")
            Spacer(Modifier.height(Space.md))
            ErrorBanner(state.error)
            if (state.error != null) Spacer(Modifier.height(Space.sm))

            Text("Pose for the next photo", style = MaterialTheme.typography.labelMedium, color = ChalkDim)
            Spacer(Modifier.height(Space.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                PhotoPose.entries.forEach { pose ->
                    IronChip(pose.label, state.captureMode == pose) { viewModel.setCaptureMode(pose) }
                }
            }
            Spacer(Modifier.height(Space.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                PrimaryButton(
                    text = "Take photo",
                    leadingIcon = Icons.Default.CameraAlt,
                    loading = state.isBusy,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        camera.runWithPermission {
                            viewModel.prepareCapture()?.let { takePicture.launch(it) }
                        }
                    }
                )
                SecondaryButton(
                    text = "Gallery",
                    leadingIcon = Icons.Default.PhotoLibrary,
                    enabled = !state.isBusy,
                    modifier = Modifier.weight(1f).height(56.dp),
                    onClick = {
                        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                )
            }
            Spacer(Modifier.height(Space.lg))
            if (state.photos.size >= 2) {
                SecondaryButton(
                    text = when {
                        !comparing -> "Compare two photos"
                        picked.isEmpty() -> "Pick the first photo"
                        else -> "Pick the second photo"
                    },
                    leadingIcon = Icons.Default.Compare,
                    onClick = { comparing = !comparing; picked = emptyList() }
                )
                Spacer(Modifier.height(Space.md))
            }
            if (state.photos.isNotEmpty()) {
                FilterChipRow(
                    options = PhotoPose.entries,
                    selected = state.filter,
                    label = { it.label },
                    onSelect = viewModel::setFilter
                )
                Spacer(Modifier.height(Space.md))
            }
        }

        when {
            state.isLoading -> LoadingBlock()
            state.photos.isEmpty() -> EmptyState(
                icon = Icons.Default.Photo,
                title = "No photos yet",
                body = "A photo every few weeks, same pose and light, shows change the mirror misses."
            )
            state.visible.isEmpty() -> EmptyState(
                icon = Icons.Default.Photo,
                title = "No ${state.filter?.label?.lowercase()} photos",
                body = "Pick another pose or take one now."
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().padding(horizontal = Space.lg),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                items(state.visible, key = { it.id }) { photo ->
                    PhotoTile(
                        photo,
                        selected = picked.any { it.id == photo.id },
                        onClick = {
                            if (!comparing) viewModel.view(photo)
                            else picked = if (picked.any { it.id == photo.id }) picked.filter { it.id != photo.id } else (picked + photo).takeLast(2)
                        }
                    )
                }
            }
        }
    }

    state.viewing?.let { photo ->
        PhotoViewer(photo = photo, unit = unit, onDismiss = { viewModel.view(null) }, onDelete = { viewModel.delete(photo) })
    }
}

@Composable
private fun PhotoTile(photo: ProgressPhotoEntity, onClick: () -> Unit, selected: Boolean = false) {
    Box(
        Modifier
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(Radius.control))
            .background(Slate700)
            .then(if (selected) Modifier.border(3.dp, Chalk, RoundedCornerShape(Radius.control)) else Modifier)
            .semantics { this.selected = selected }
            .clickable(role = Role.Image, onClickLabel = "Open photo", onClick = onClick)
    ) {
        AsyncImage(
            model = File(photo.filePath),
            contentDescription = "${photo.pose.label} progress photo from ${formatShortDate(photo.takenAt)}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Text(
            formatShortDate(photo.takenAt),
            style = MaterialTheme.typography.labelSmall,
            color = Chalk,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(Space.xs)
                .background(Slate900.copy(alpha = 0.7f), RoundedCornerShape(Radius.chip))
                .padding(horizontal = Space.sm, vertical = 2.dp)
        )
    }
}

@Composable
private fun PhotoViewer(
    photo: ProgressPhotoEntity,
    unit: UnitSystem,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Slate900)
                .padding(Space.lg)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(formatFullDate(photo.takenAt), style = MaterialTheme.typography.titleMedium, color = Chalk)
                    Text(
                        listOfNotNull(photo.pose.label, photo.bodyweightKg?.let { unit.formatWeight(it) }).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ChalkDim
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close photo", tint = Chalk)
                }
            }
            Spacer(Modifier.height(Space.md))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = File(photo.filePath),
                    contentDescription = "${photo.pose.label} progress photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.height(Space.md))
            SecondaryButton(text = "Delete photo", onClick = { confirmDelete = true })
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this photo?",
            body = "The image is removed from this phone permanently.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}
