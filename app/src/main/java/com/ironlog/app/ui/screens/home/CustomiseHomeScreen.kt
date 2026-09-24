package com.ironlog.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ironlog.app.data.entity.HomeSection
import com.ironlog.app.data.repository.ProfileRepository
import com.ironlog.app.data.repository.userMessage
import com.ironlog.app.ui.components.Caption
import com.ironlog.app.ui.components.ErrorBanner
import com.ironlog.app.ui.components.IronCard
import com.ironlog.app.ui.components.IronTopBar
import com.ironlog.app.ui.components.SecondaryButton
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.Slate600
import com.ironlog.app.ui.theme.Slate800
import com.ironlog.app.ui.theme.Space
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.flow.map

/** A Home card as the user sees it in the list: where it sits and whether it shows. */
data class HomeCardRow(val section: HomeSection, val shown: Boolean)

@HiltViewModel
class CustomiseHomeViewModel @Inject constructor(
    private val profileRepository: ProfileRepository
) : ViewModel() {

    /** Shown cards first in their order, then the hidden ones in their natural order. */
    val rows: StateFlow<List<HomeCardRow>> = profileRepository.homeSections
        .map { shown ->
            shown.map { HomeCardRow(it, true) } +
                HomeSection.entries.filter { it !in shown }.map { HomeCardRow(it, false) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun toggle(section: HomeSection) = change { shown ->
        if (section in shown) shown - section else shown + section
    }

    fun move(section: HomeSection, delta: Int) = change { shown ->
        val list = shown.toMutableList()
        val from = list.indexOf(section)
        val to = from + delta
        if (from < 0 || to !in list.indices) return@change shown
        list.removeAt(from)
        list.add(to, section)
        list
    }

    fun reset() = change { HomeSection.entries.toList() }

    private fun change(transform: (List<HomeSection>) -> List<HomeSection>) {
        viewModelScope.launch {
            val current = profileRepository.homeSections.first()
            profileRepository.setHomeSections(transform(current))
                .onFailure { e -> _error.value = e.userMessage("Couldn't save the layout") }
        }
    }

    fun dismissError() { _error.value = null }
}

/**
 * Which cards Home shows and in what order.
 *
 * Home had grown to a dozen cards, and a lifter who never runs does not want
 * a cardio card above their strength goals. So the order is theirs: switch
 * a card off and it goes, move it and it stays moved. The workout-in-progress
 * card is not in the list, because it is not decoration.
 */
@Composable
fun CustomiseHomeScreen(
    onBack: () -> Unit,
    viewModel: CustomiseHomeViewModel = hiltViewModel()
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val shownCount = rows.count { it.shown }

    Column(Modifier.fillMaxSize()) {
        IronTopBar(title = "Customise Home", onBack = onBack)
        ErrorBanner(error, Modifier.fillMaxWidth())
        LazyColumn(
            contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            item {
                Caption("Switch a card off to hide it. The arrows set the order. The workout in progress always comes first.")
                Spacer(Modifier.height(Space.sm))
            }
            itemsIndexed(rows, key = { _, row -> row.section.name }) { index, row ->
                val isFirstShown = index == 0
                val isLastShown = index == shownCount - 1
                IronCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                row.section.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (row.shown) Chalk else ChalkDim
                            )
                            Caption(row.section.blurb)
                        }
                        if (row.shown) {
                            IconButton(onClick = { viewModel.move(row.section, -1) }, enabled = !isFirstShown) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move ${row.section.label} up", tint = ChalkDim)
                            }
                            IconButton(onClick = { viewModel.move(row.section, 1) }, enabled = !isLastShown) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move ${row.section.label} down", tint = ChalkDim)
                            }
                        }
                        Switch(
                            checked = row.shown,
                            onCheckedChange = { viewModel.toggle(row.section) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Chalk,
                                checkedTrackColor = Slate600,
                                uncheckedThumbColor = ChalkDim,
                                uncheckedTrackColor = Slate800
                            )
                        )
                    }
                }
            }
            item {
                Spacer(Modifier.height(Space.md))
                SecondaryButton(text = "Reset to the default layout", onClick = viewModel::reset)
                Spacer(Modifier.height(Space.xl))
            }
        }
    }
}
