package com.ironlog.app.ui.screens.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.core.ai.WgerCatalogue
import com.ironlog.app.data.repository.ExerciseRepository
import com.ironlog.app.data.repository.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnlineLibraryUiState(
    val entries: List<WgerCatalogue.Entry> = emptyList(),
    val imported: Set<Int> = emptySet(),
    val isLoading: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class OnlineLibraryViewModel @Inject constructor(
    private val catalogue: WgerCatalogue,
    private val exerciseRepository: ExerciseRepository
) : ViewModel() {

    private val _state = MutableStateFlow(OnlineLibraryUiState())
    val state: StateFlow<OnlineLibraryUiState> = _state.asStateFlow()

    init {
        loadMore()
    }

    fun loadMore() {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            runCatching { catalogue.page(offset = _state.value.entries.size) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            // Appended rather than replaced: this is a paged
                            // browse, and losing what the user already scrolled
                            // past on every page would be its own bug.
                            entries = it.entries + page.entries,
                            hasMore = page.hasMore,
                            isLoading = false
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(isLoading = false, error = e.userMessage("Could not load the online library"))
                    }
                }
        }
    }

    /**
     * Copies an entry into the user's own library.
     *
     * Saved as a custom exercise, which is what it is: it belongs to this user,
     * it can be edited, and the licence and author are written into the
     * instructions so the attribution survives the import.
     */
    fun import(entry: WgerCatalogue.Entry) {
        viewModelScope.launch {
            val instructions = buildString {
                if (entry.description.isNotBlank()) {
                    append(entry.description.take(400))
                    append("\n\n")
                }
                append("From wger.de · ${entry.licence} · ${entry.author}")
            }

            exerciseRepository.createCustom(
                name = entry.name,
                muscle = entry.muscle,
                category = entry.category,
                instructions = instructions
            )
                .onSuccess { _state.update { it.copy(imported = it.imported + entry.id) } }
                .onFailure { e ->
                    _state.update { it.copy(error = e.userMessage("Could not import that exercise")) }
                }
        }
    }
}
