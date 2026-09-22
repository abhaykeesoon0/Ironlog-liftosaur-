package com.ironlog.app.ui.screens.profile

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.core.media.PhotoStore
import com.ironlog.app.data.entity.UnitSystem
import com.ironlog.app.data.entity.UserEntity
import com.ironlog.app.data.repository.AuthRepository
import com.ironlog.app.data.repository.ProfileRepository
import com.ironlog.app.data.repository.WorkoutRepository
import com.ironlog.app.data.repository.userMessage
import com.ironlog.app.ui.format.lengthValue
import com.ironlog.app.ui.format.parseLengthToCm
import com.ironlog.app.ui.format.parseWeightToKg
import com.ironlog.app.ui.format.weightValue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ProfileData(
    val user: UserEntity? = null,
    val workoutCount: Int = 0,
    val recordCount: Int = 0
) {
    val unit: UnitSystem get() = user?.unitSystem ?: UnitSystem.METRIC
}

data class ProfileForm(
    val isEditing: Boolean = false,
    val name: String = "",
    val bodyweight: String = "",
    val height: String = "",
    val nameError: String? = null,
    val bodyweightError: String? = null,
    val heightError: String? = null,
    val isSaving: Boolean = false,
    val isAvatarBusy: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val workoutRepository: WorkoutRepository,
    private val authRepository: AuthRepository,
    private val photoStore: PhotoStore,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    val data: StateFlow<ProfileData> = combine(
        profileRepository.observeUser(),
        workoutRepository.observeWorkoutDates(),
        workoutRepository.observeRecordCount()
    ) { user, dates, records -> ProfileData(user, dates.size, records) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileData())

    private val _form = MutableStateFlow(ProfileForm())
    val form: StateFlow<ProfileForm> = _form.asStateFlow()

    fun startEditing() {
        val current = data.value
        val user = current.user ?: return
        _form.value = ProfileForm(
            isEditing = true,
            name = user.displayName,
            bodyweight = current.unit.weightValue(user.bodyweightKg),
            height = current.unit.lengthValue(user.heightCm)
        )
    }

    fun cancelEditing() = _form.update { ProfileForm() }

    fun onNameChange(v: String) = _form.update { it.copy(name = v, nameError = null, error = null) }
    fun onBodyweightChange(v: String) = _form.update { it.copy(bodyweight = v, bodyweightError = null, error = null) }
    fun onHeightChange(v: String) = _form.update { it.copy(height = v, heightError = null, error = null) }

    fun save() {
        val f = _form.value
        val unit = data.value.unit
        val weightKg = if (f.bodyweight.isBlank()) null else unit.parseWeightToKg(f.bodyweight)
        val heightCm = if (f.height.isBlank()) null else unit.parseLengthToCm(f.height)
        if (f.bodyweight.isNotBlank() && weightKg == null) {
            _form.update { it.copy(bodyweightError = "Enter a number") }
            return
        }
        if (f.height.isNotBlank() && heightCm == null) {
            _form.update { it.copy(heightError = "Enter a number") }
            return
        }
        _form.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            profileRepository.updateProfile(f.name, weightKg, heightCm)
                .onSuccess { _form.value = ProfileForm() }
                .onFailure { e ->
                    _form.update { it.copy(isSaving = false, error = e.userMessage("Could not save your profile")) }
                }
        }
    }

    /** Creates the private file the camera will write into; its path survives process death. */
    fun prepareAvatarCapture(): Uri? = runCatching { photoStore.newCaptureTarget() }
        .onSuccess { savedStateHandle[KEY_PENDING_AVATAR] = it.file.absolutePath }
        .onFailure { e -> _form.update { it.copy(error = e.userMessage("Could not open the camera")) } }
        .getOrNull()?.uri

    fun onAvatarCaptured(success: Boolean) {
        val path = savedStateHandle.get<String>(KEY_PENDING_AVATAR) ?: return
        savedStateHandle.remove<String>(KEY_PENDING_AVATAR)
        if (!success) {
            photoStore.delete(path)
            return
        }
        _form.update { it.copy(isAvatarBusy = true, error = null) }
        viewModelScope.launch {
            photoStore.process(File(path))
                .onSuccess { processed ->
                    profileRepository.setAvatar(processed).onFailure { e ->
                        photoStore.delete(processed)
                        _form.update { it.copy(error = e.userMessage("Could not save the photo")) }
                    }
                }
                .onFailure { e ->
                    photoStore.delete(path)
                    _form.update { it.copy(error = e.userMessage("Could not process the photo")) }
                }
            _form.update { it.copy(isAvatarBusy = false) }
        }
    }

    /** Picks one of the app's built-in figures instead of a photo. */
    fun chooseFigureAvatar(value: String) {
        viewModelScope.launch {
            profileRepository.setAvatar(value).onFailure { e ->
                _form.update { it.copy(error = e.userMessage("Could not set that avatar")) }
            }
        }
    }

    fun removeAvatar() {
        viewModelScope.launch {
            profileRepository.setAvatar(null).onFailure { e ->
                _form.update { it.copy(error = e.userMessage("Could not remove the photo")) }
            }
        }
    }

    fun dismissError() = _form.update { it.copy(error = null) }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    private companion object {
        const val KEY_PENDING_AVATAR = "pending_avatar_path"
    }
}
