package com.ironlog.app.ui.screens.body

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.core.security.Validators
import com.ironlog.app.data.entity.BodyMeasurementEntity
import com.ironlog.app.data.entity.UnitSystem
import com.ironlog.app.data.repository.BodyRepository
import com.ironlog.app.data.repository.MeasurementInput
import com.ironlog.app.data.repository.ProfileRepository
import com.ironlog.app.data.repository.userMessage
import com.ironlog.app.ui.format.parseLengthToCm
import com.ironlog.app.ui.format.parseWeightToKg
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which measurement a form field holds; drives parsing and labels. */
enum class MeasureField(val label: String, val kind: Kind) {
    WEIGHT("Bodyweight", Kind.WEIGHT),
    BODY_FAT("Body fat %", Kind.PERCENT),
    CHEST("Chest", Kind.LENGTH),
    WAIST("Waist", Kind.LENGTH),
    HIPS("Hips", Kind.LENGTH),
    LEFT_ARM("Left arm", Kind.LENGTH),
    RIGHT_ARM("Right arm", Kind.LENGTH),
    THIGH("Thigh", Kind.LENGTH),
    CALF("Calf", Kind.LENGTH),
    NECK("Neck", Kind.LENGTH);

    enum class Kind { WEIGHT, PERCENT, LENGTH }
}

data class MeasurementForm(
    val isOpen: Boolean = false,
    val values: Map<MeasureField, String> = emptyMap(),
    val note: String = "",
    val fieldErrors: Map<MeasureField, String> = emptyMap(),
    val error: String? = null,
    val isSaving: Boolean = false
)

data class BodyData(
    val unit: UnitSystem = UnitSystem.METRIC,
    val measurements: List<BodyMeasurementEntity> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class BodyViewModel @Inject constructor(
    private val bodyRepository: BodyRepository,
    profileRepository: ProfileRepository
) : ViewModel() {

    val data: StateFlow<BodyData> = combine(
        profileRepository.observeUser(),
        bodyRepository.observeMeasurements()
    ) { user, items -> BodyData(user?.unitSystem ?: UnitSystem.METRIC, items, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BodyData())

    private val _form = MutableStateFlow(MeasurementForm())
    val form: StateFlow<MeasurementForm> = _form.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun openForm() = _form.update { MeasurementForm(isOpen = true) }
    fun closeForm() = _form.update { MeasurementForm() }

    fun onValueChange(field: MeasureField, value: String) = _form.update {
        it.copy(values = it.values + (field to value), fieldErrors = it.fieldErrors - field, error = null)
    }

    fun onNoteChange(value: String) = _form.update { it.copy(note = value) }

    fun save() {
        val f = _form.value
        val unit = data.value.unit
        val parsed = mutableMapOf<MeasureField, Double>()
        val errors = mutableMapOf<MeasureField, String>()

        f.values.forEach { (field, text) ->
            if (text.isBlank()) return@forEach
            val value = when (field.kind) {
                MeasureField.Kind.WEIGHT -> unit.parseWeightToKg(text)
                MeasureField.Kind.PERCENT -> Validators.parseMeasurement(text, max = 70.0)
                MeasureField.Kind.LENGTH -> unit.parseLengthToCm(text)
            }
            if (value == null) errors[field] = "Check this value" else parsed[field] = value
        }
        if (errors.isNotEmpty()) {
            _form.update { it.copy(fieldErrors = errors) }
            return
        }

        val input = MeasurementInput(
            weightKg = parsed[MeasureField.WEIGHT],
            bodyFatPercent = parsed[MeasureField.BODY_FAT],
            chestCm = parsed[MeasureField.CHEST],
            waistCm = parsed[MeasureField.WAIST],
            hipsCm = parsed[MeasureField.HIPS],
            leftArmCm = parsed[MeasureField.LEFT_ARM],
            rightArmCm = parsed[MeasureField.RIGHT_ARM],
            thighCm = parsed[MeasureField.THIGH],
            calfCm = parsed[MeasureField.CALF],
            neckCm = parsed[MeasureField.NECK],
            note = f.note
        )
        _form.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            bodyRepository.addMeasurement(input)
                .onSuccess { _form.value = MeasurementForm() }
                .onFailure { e ->
                    _form.update { it.copy(isSaving = false, error = e.userMessage("Could not save the measurement")) }
                }
        }
    }

    fun delete(item: BodyMeasurementEntity) {
        viewModelScope.launch {
            bodyRepository.deleteMeasurement(item).onFailure { e ->
                _error.value = e.userMessage("Could not delete the measurement")
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }
}
