package com.ironlog.app.ui.screens.rewards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.data.entity.StorePurchaseEntity
import com.ironlog.app.data.repository.RewardsRepository
import com.ironlog.app.data.repository.StrengthGoalRepository
import com.ironlog.app.data.repository.WorkoutRepository
import com.ironlog.app.data.repository.userMessage
import com.ironlog.app.domain.usecase.Quests
import com.ironlog.app.domain.usecase.StoreCatalogue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class QuestsUiState(
    val isLoading: Boolean = true,
    val balance: Int = 0,
    val quests: List<Quests.Progress> = emptyList(),
    val justEarned: Int? = null,
    val error: String? = null
)

@HiltViewModel
class QuestsViewModel @Inject constructor(
    private val rewardsRepository: RewardsRepository,
    workoutRepository: WorkoutRepository,
    goalRepository: StrengthGoalRepository
) : ViewModel() {

    private val _state = MutableStateFlow(QuestsUiState())
    val state: StateFlow<QuestsUiState> = _state.asStateFlow()

    private val zone: ZoneId get() = ZoneId.systemDefault()

    init {
        val keys = Quests.currentKeys(System.currentTimeMillis(), zone)
        viewModelScope.launch {
            combine(
                workoutRepository.observeHistory(HISTORY_WINDOW),
                goalRepository.observeMuscleSessions(),
                rewardsRepository.observeClaims(keys.values.toList()),
                rewardsRepository.observeBalance()
            ) { history, muscleRows, claims, balance ->
                val today = LocalDate.now(zone)
                val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())

                fun dayOf(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

                val workouts = history.map { it.workout }
                val todays = workouts.filter { dayOf(it.startedAt) == today }
                val thisWeek = workouts.filter { !dayOf(it.startedAt).isBefore(weekStart) }

                val inputs = Quests.Inputs(
                    workoutsToday = todays.size,
                    volumeTodayKg = todays.sumOf { it.totalVolumeKg },
                    workoutsThisWeek = thisWeek.size,
                    recordsThisWeek = thisWeek.sumOf { it.prCount },
                    musclesThisWeek = muscleRows
                        .filter { !dayOf(it.trainedAt).isBefore(weekStart) }
                        .map { it.muscle }
                        .distinct()
                        .size,
                    longestSessionSecondsThisWeek = thisWeek.maxOfOrNull { it.durationSeconds } ?: 0
                )

                QuestsUiState(
                    isLoading = false,
                    balance = balance,
                    quests = Quests.progress(inputs, claims.map { it.questId }.toSet())
                )
            }
                .catch { e -> _state.update { it.copy(isLoading = false, error = e.userMessage("Could not load your quests")) } }
                .collect { next ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            balance = next.balance,
                            quests = next.quests
                        )
                    }
                }
        }
    }

    fun claim(quest: Quests.Quest) {
        viewModelScope.launch {
            // The key is recomputed at the moment of claiming rather than kept
            // from when the screen opened, so a claim made just after midnight
            // lands on the day it was actually earned.
            val periodKey = Quests.periodKey(quest.period, System.currentTimeMillis(), zone)
            rewardsRepository.claim(quest.id, periodKey)
                .onSuccess { coins -> _state.update { it.copy(justEarned = coins) } }
                .onFailure { e -> _state.update { it.copy(error = e.userMessage("Could not claim that reward")) } }
        }
    }

    fun dismissEarned() = _state.update { it.copy(justEarned = null) }
    fun dismissError() = _state.update { it.copy(error = null) }

    private companion object {
        /** A fortnight is more than enough to answer "today" and "this week". */
        const val HISTORY_WINDOW = 30
    }
}

/** One row in the store, with what the viewer can do about it. */
data class StoreRow(
    val item: StoreCatalogue.Item,
    val isOwned: Boolean,
    val isEquipped: Boolean,
    val isAffordable: Boolean
)

data class StoreUiState(
    val isLoading: Boolean = true,
    val balance: Int = 0,
    val rows: List<StoreRow> = emptyList(),
    val error: String? = null
) {
    fun inCategory(category: StoreCatalogue.Category): List<StoreRow> =
        rows.filter { it.item.category == category }
}

@HiltViewModel
class StoreViewModel @Inject constructor(
    private val rewardsRepository: RewardsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(StoreUiState())
    val state: StateFlow<StoreUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                rewardsRepository.observePurchases(),
                rewardsRepository.observeBalance()
            ) { purchases, balance ->
                val owned = purchases.associateBy { it.itemId }
                StoreUiState(
                    isLoading = false,
                    balance = balance,
                    rows = StoreCatalogue.ITEMS.map { item ->
                        val purchase = owned[item.id]
                        StoreRow(
                            item = item,
                            // The default background is owned by everyone, whether
                            // or not there is a row recording it.
                            isOwned = purchase != null || item.price == 0,
                            isEquipped = purchase?.isEquipped == true,
                            isAffordable = balance >= item.price
                        )
                    }
                )
            }
                .catch { e -> _state.update { it.copy(isLoading = false, error = e.userMessage("Could not open the store")) } }
                .collect { next -> _state.value = next.copy(error = _state.value.error) }
        }
    }

    fun buy(item: StoreCatalogue.Item) {
        viewModelScope.launch {
            rewardsRepository.buy(item.id)
                .onFailure { e -> _state.update { it.copy(error = e.userMessage("Could not buy that")) } }
        }
    }

    fun equip(item: StoreCatalogue.Item) {
        viewModelScope.launch {
            rewardsRepository.equip(item.id)
                .onFailure { e -> _state.update { it.copy(error = e.userMessage("Could not equip that")) } }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }
}

/**
 * What the rest of the app needs to know about purchases: the equipped
 * background, badge and widgets.
 */
@HiltViewModel
class EquippedViewModel @Inject constructor(
    rewardsRepository: RewardsRepository
) : ViewModel() {

    val equipped: StateFlow<Equipped> = rewardsRepository.observePurchases()
        .map { purchases -> Equipped.from(purchases) }
        .catch { emit(Equipped()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Equipped())
}

data class Equipped(
    val backgroundId: String = StoreCatalogue.DEFAULT_BACKGROUND,
    val badgeId: String? = null,
    val widgetIds: Set<String> = emptySet()
) {
    companion object {
        fun from(purchases: List<StorePurchaseEntity>): Equipped {
            val equipped = purchases.filter { it.isEquipped }.mapNotNull { StoreCatalogue.byId(it.itemId) }
            return Equipped(
                backgroundId = equipped.firstOrNull { it.category == StoreCatalogue.Category.BACKGROUND }?.id
                    ?: StoreCatalogue.DEFAULT_BACKGROUND,
                badgeId = equipped.firstOrNull { it.category == StoreCatalogue.Category.BADGE }?.id,
                widgetIds = equipped.filter { it.category == StoreCatalogue.Category.WIDGET }.map { it.id }.toSet()
            )
        }
    }
}
