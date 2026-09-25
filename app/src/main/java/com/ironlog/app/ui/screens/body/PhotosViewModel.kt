package com.ironlog.app.ui.screens.body

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.core.media.PhotoStore
import com.ironlog.app.data.entity.PhotoPose
import com.ironlog.app.data.entity.ProgressPhotoEntity
import com.ironlog.app.data.entity.UnitSystem
import com.ironlog.app.data.repository.BodyRepository
import com.ironlog.app.data.repository.ProfileRepository
import com.ironlog.app.data.repository.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class PhotosUiState(
    val photos: List<ProgressPhotoEntity> = emptyList(),
    val filter: PhotoPose? = null,
    val captureMode: PhotoPose = PhotoPose.FRONT,
    val viewing: ProgressPhotoEntity? = null,
    val isBusy: Boolean = false,
    val error: String? = null,
    val isLoading: Boolean = true
) {
    val visible: List<ProgressPhotoEntity>
        get() = if (filter == null) photos else photos.filter { it.pose == filter }
}

private data class PhotoControls(
    val filter: PhotoPose? = null,
    val captureMode: PhotoPose = PhotoPose.FRONT,
    val viewingId: String? = null,
    val isBusy: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class PhotosViewModel @Inject constructor(
    private val bodyRepository: BodyRepository,
    private val photoStore: PhotoStore,
    profileRepository: ProfileRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    /** Whether the whole app is already uncapturable, so this screen need not undo the flag on exit. */
    val screenshotsBlocked: StateFlow<Boolean> = profileRepository.screenshotsBlocked
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val controls = MutableStateFlow(PhotoControls())

    val unit: StateFlow<UnitSystem> = profileRepository.observeUser()
        .map { it?.unitSystem ?: UnitSystem.METRIC }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UnitSystem.METRIC)

    val state: StateFlow<PhotosUiState> = combine(bodyRepository.observePhotos(), controls) { photos, c ->
        PhotosUiState(
            photos = photos,
            filter = c.filter,
            captureMode = c.captureMode,
            viewing = photos.firstOrNull { it.id == c.viewingId },
            isBusy = c.isBusy,
            error = c.error,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PhotosUiState())

    fun setFilter(pose: PhotoPose?) = controls.update { it.copy(filter = pose) }
    fun setCaptureMode(pose: PhotoPose) = controls.update { it.copy(captureMode = pose) }
    fun view(photo: ProgressPhotoEntity?) = controls.update { it.copy(viewingId = photo?.id) }
    fun dismissError() = controls.update { it.copy(error = null) }

    /**
     * Creates the private destination file and returns the one-shot URI the
     * camera app may write to. The path is kept in saved state because the
     * camera can push this process out of memory before it returns.
     */
    fun prepareCapture(): Uri? = runCatching { photoStore.newCaptureTarget() }
        .onSuccess {
            savedStateHandle[KEY_PENDING] = it.file.absolutePath
            savedStateHandle[KEY_POSE] = controls.value.captureMode.name
        }
        .onFailure { e -> controls.update { it.copy(error = e.userMessage("Could not open the camera")) } }
        .getOrNull()?.uri

    fun onCaptureResult(success: Boolean) {
        val path = savedStateHandle.get<String>(KEY_PENDING) ?: return
        val pose = savedStateHandle.get<String>(KEY_POSE)
            ?.let { runCatching { PhotoPose.valueOf(it) }.getOrNull() } ?: controls.value.captureMode
        savedStateHandle.remove<String>(KEY_PENDING)
        if (!success) {
            // Cancelled: the camera may have left an empty file behind.
            photoStore.delete(path)
            return
        }
        store(pose) { photoStore.process(File(path)).onFailure { photoStore.delete(path) } }
    }

    fun onPicked(uri: Uri?) {
        uri ?: return
        store(controls.value.captureMode) { photoStore.importFromUri(uri) }
    }

    private fun store(pose: PhotoPose, produce: suspend () -> Result<String>) {
        controls.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            produce()
                .onSuccess { path ->
                    bodyRepository.addPhoto(path, pose, "").onFailure { e ->
                        photoStore.delete(path)
                        controls.update { it.copy(error = e.userMessage("Could not save the photo")) }
                    }
                }
                .onFailure { e -> controls.update { it.copy(error = e.userMessage("Could not process the photo")) } }
            controls.update { it.copy(isBusy = false) }
        }
    }

    fun delete(photo: ProgressPhotoEntity) {
        controls.update { it.copy(viewingId = null) }
        viewModelScope.launch {
            bodyRepository.deletePhoto(photo.id).onFailure { e ->
                controls.update { it.copy(error = e.userMessage("Could not delete the photo")) }
            }
        }
    }

    private companion object {
        const val KEY_PENDING = "pending_photo_path"
        const val KEY_POSE = "pending_photo_pose"
    }
}
