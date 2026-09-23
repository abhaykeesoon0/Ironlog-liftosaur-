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
import com.ironlog.app.data.entity.SetType
import com.ironlog.app.data.repository.BodyRepository
import com.ironlog.app.data.repository.CardioRepository
import com.ironlog.app.domain.usecase.TrainingMath

data class QuestsUiState(
    val isLoading: Boolean = true,
    val balance: Int = 0,
    val streakWeeks: Int = 0,
    val quests: List<Quests.Progress> = emptyList(),
    val justEarned: Int? = null,
    val error: String? = null
)

@HiltViewModel
class QuestsViewModel @Inject constructor(
    private val rewardsRepository: RewardsRepository,
    workoutRepository: WorkoutRepository,
    goalRepository: StrengthGoalRepository,
    cardioRepository: CardioRepository,
    bodyRepository: BodyRepository
) : ViewModel() {

    private val _state = MutableStateFlow(QuestsUiState())
    val state: StateFlow<QuestsUiState> = _state.asStateFlow()

    private val zone: ZoneId get() = ZoneId.systemDefault()

    init {
        val keys = Quests.currentKeys(System.currentTimeMillis(), zone)
        // Seven sources, and combine takes five, so the log and the ledger are
        // folded first and the activity sources joined to them after.
        val log = combine(
            workoutRepository.observeHistory(HISTORY_WINDOW),
            goalRepository.observeMuscleSessions(),
            rewardsRepository.observeClaims(keys.values.toList()),
            rewardsRepository.observeBalance()
        ) { history, muscleRows, claims, balance -> Log(history, muscleRows, claims, balance) }
        val activity = combine(
            cardioRepository.observeSessions(),
            cardioRepository.observeStepsSince(7),
            bodyRepository.observeMeasurements()
        ) { sessions, steps, measurements -> Activity(sessions, steps, measurements) }

        viewModelScope.launch {
            combine(log, activity) { (history, muscleRows, claims, balance), (cardio, steps, measurements) ->
                val today = LocalDate.now(zone)
                val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
                val monthStart = today.withDayOfMonth(1)

                fun dayOf(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

                val workouts = history.map { it.workout }
                val todays = history.filter { dayOf(it.workout.startedAt) == today }
                val thisWeek = workouts.filter { !dayOf(it.startedAt).isBefore(weekStart) }
                val thisMonth = workouts.filter { !dayOf(it.startedAt).isBefore(monthStart) }

                // An exercise is new this week if the first time it was ever
                // logged falls inside the week. History arrives newest first.
                val firstSeen = history.asReversed()
                    .flatMap { detail -> detail.exercises.map { it.exercise.id to detail.workout.startedAt } }
                    .groupBy({ it.first }, { it.second })
                    .mapValues { (_, starts) -> starts.min() }

                val stepsByDay = steps.associate { it.dateEpochDay to it.steps }
                val stepsThisWeek = (0..6).map { today.minusDays(it.toLong()) }
                    .filter { !it.isBefore(weekStart) }
                    .sumOf { stepsByDay[it.toEpochDay()] ?: 0 }

                val inputs = Quests.Inputs(
                    workoutsToday = todays.size,
                    volumeTodayKg = todays.sumOf { it.workout.totalVolumeKg },
                    musclesToday = muscleRows.filter { dayOf(it.trainedAt) == today }
                        .map { it.muscle }.distinct().size,
                    warmupSetsToday = todays.sumOf { detail ->
                        detail.exercises.sumOf { ex -> ex.sets.count { it.isCompleted && it.type == SetType.WARMUP } }
                    },
                    bodyweightLoggedToday = measurements.any { it.weightKg != null && dayOf(it.takenAt) == today },
                    stepsToday = stepsByDay[today.toEpochDay()] ?: 0,
                    workoutsThisWeek = thisWeek.size,
                    recordsThisWeek = thisWeek.sumOf { it.prCount },
                    musclesThisWeek = muscleRows
                        .filter { !dayOf(it.trainedAt).isBefore(weekStart) }
                        .map { it.muscle }
                        .distinct()
                        .size,
                    longestSessionSecondsThisWeek = thisWeek.maxOfOrNull { it.durationSeconds } ?: 0,
                    volumeThisWeekKg = thisWeek.sumOf { it.totalVolumeKg },
                    cardioSessionsThisWeek = cardio.count { !dayOf(it.startedAt).isBefore(weekStart) },
                    stepsThisWeek = stepsThisWeek,
                    newExercisesThisWeek = firstSeen.values.count { !dayOf(it).isBefore(weekStart) },
                    workoutsThisMonth = thisMonth.size,
                    recordsThisMonth = thisMonth.sumOf { it.prCount },
                    volumeThisMonthKg = thisMonth.sumOf { it.totalVolumeKg },
                    lifetimeWorkouts = workouts.size,
                    lifetimeRecords = workouts.sumOf { it.prCount },
                    lifetimeVolumeKg = workouts.sumOf { it.totalVolumeKg },
                    distinctExercisesEver = firstSeen.size,
                    streakWeeks = TrainingMath.weeklyStreak(workouts.map { it.startedAt })
                )

                QuestsUiState(
                    isLoading = false,
                    balance = balance,
                    streakWeeks = inputs.streakWeeks,
                    quests = Quests.progress(inputs, claims.map { it.questId }.toSet())
                )
            }
                .catch { e -> _state.update { it.copy(isLoading = false, error = e.userMessage("Could not load your quests")) } }
                .collect { next ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            balance = next.balance,
                            streakWeeks = next.streakWeeks,
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

    /** One pass of the log side: workouts, muscle sessions, claims and coins. */
    private data class Log(
        val history: List<com.ironlog.app.data.dao.WorkoutWithDetail>,
        val muscleRows: List<com.ironlog.app.data.dao.MuscleSessionRow>,
        val claims: List<com.ironlog.app.data.entity.QuestClaimEntity>,
        val balance: Int
    )

    /** One pass of the activity side: cardio, steps and the scale. */
    private data class Activity(
        val cardio: List<com.ironlog.app.data.entity.CardioSessionEntity>,
        val steps: List<com.ironlog.app.data.entity.DailyActivityEntity>,
        val measurements: List<com.ironlog.app.data.entity.BodyMeasurementEntity>
    )

    private companion object {
        /**
         * The milestones count a lifetime, so the whole log is read. It is a
         * coursework-sized log; a thousand sessions is years of training.
         */
        const val HISTORY_WINDOW = 1_000
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
    val titleId: String? = null,
    val widgetIds: Set<String> = emptySet()
) {
    companion object {
        fun from(purchases: List<StorePurchaseEntity>): Equipped {
            val equipped = purchases.filter { it.isEquipped }.mapNotNull { StoreCatalogue.byId(it.itemId) }
            return Equipped(
                backgroundId = equipped.firstOrNull { it.category == StoreCatalogue.Category.BACKGROUND }?.id
                    ?: StoreCatalogue.DEFAULT_BACKGROUND,
                badgeId = equipped.firstOrNull { it.category == StoreCatalogue.Category.BADGE }?.id,
                titleId = equipped.firstOrNull { it.category == StoreCatalogue.Category.TITLE }?.id,
                widgetIds = equipped.filter { it.category == StoreCatalogue.Category.WIDGET }.map { it.id }.toSet()
            )
        }
    }
}
