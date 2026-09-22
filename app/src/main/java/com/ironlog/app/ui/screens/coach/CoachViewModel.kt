package com.ironlog.app.ui.screens.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.core.ai.CloudAssistant
import com.ironlog.app.core.ai.OnDeviceAssistant
import com.ironlog.app.data.repository.BodyRepository
import com.ironlog.app.data.repository.ProfileRepository
import com.ironlog.app.data.repository.StrengthGoalRepository
import com.ironlog.app.data.repository.WorkoutRepository
import com.ironlog.app.data.repository.userMessage
import com.ironlog.app.domain.usecase.Coach
import com.ironlog.app.domain.usecase.Recovery
import com.ironlog.app.domain.usecase.TrainingMath
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class CoachMessage(
    val text: String,
    val fromUser: Boolean,
    val source: String? = null,
    val isReferral: Boolean = false
)

/** Which brain answered, shown so the user knows what they are reading. */
enum class CoachEngine(val label: String) {
    RULES("Built-in coach"),
    ON_DEVICE("Gemini Nano, on this phone"),
    CLOUD("Claude")
}

data class CoachUiState(
    val messages: List<CoachMessage> = listOf(
        CoachMessage(
            "Ask me about training, recovery or your own numbers. I answer from your log and a " +
                "built-in reference, on this phone.",
            fromUser = false
        )
    ),
    val isThinking: Boolean = false,
    val cloudAvailable: Boolean = false,
    val useCloud: Boolean = false,
    val onDeviceAvailable: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class CoachViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val profileRepository: ProfileRepository,
    private val goalRepository: StrengthGoalRepository,
    private val bodyRepository: BodyRepository,
    private val cloudAssistant: CloudAssistant,
    private val onDeviceAssistant: OnDeviceAssistant
) : ViewModel() {

    private val _state = MutableStateFlow(CoachUiState())
    val state: StateFlow<CoachUiState> = _state.asStateFlow()

    val suggestions: List<String> = Coach.SUGGESTIONS

    init {
        val hasKey = cloudAssistant.hasKey()
        // The cloud is offered when a key exists, but the offline coach stays
        // the default: it is the one that always works.
        _state.update { it.copy(cloudAvailable = hasKey) }

        viewModelScope.launch {
            // Only a handful of phones carry Gemini Nano, so this is a question
            // asked once and answered honestly rather than a feature assumed.
            val ready = onDeviceAssistant.availability() == OnDeviceAssistant.Availability.READY
            _state.update { it.copy(onDeviceAvailable = ready) }
        }
    }

    fun setUseCloud(enabled: Boolean) = _state.update { it.copy(useCloud = enabled) }

    fun ask(question: String) {
        val clean = question.trim()
        if (clean.isEmpty() || _state.value.isThinking) return

        _state.update {
            it.copy(
                messages = it.messages + CoachMessage(clean, fromUser = true),
                isThinking = true,
                error = null
            )
        }

        viewModelScope.launch {
            val context = gatherContext()

            // On-device first when the phone has it: a real model, no network,
            // nothing leaving the handset. The cloud is only used when the user
            // has asked for it, and the rules coach catches everything else.
            if (!_state.value.useCloud && _state.value.onDeviceAvailable) {
                val onDevice = onDeviceAssistant.ask(clean, describe(context))
                if (onDevice != null) {
                    reply(CoachMessage(onDevice, fromUser = false, source = CoachEngine.ON_DEVICE.label))
                    return@launch
                }
                // Fell through: the model was there a moment ago and is not now.
            }

            if (_state.value.useCloud && _state.value.cloudAvailable) {
                runCatching { cloudAssistant.ask(clean, describe(context)) }
                    .onSuccess { reply -> reply(CoachMessage(reply, fromUser = false, source = CoachEngine.CLOUD.label)) }
                    .onFailure { e ->
                        // Falls back rather than failing: the offline coach can
                        // still answer, which is better than an error bubble.
                        val offline = Coach.answer(clean, context)
                        reply(
                            CoachMessage(offline.text, fromUser = false, source = offline.source, isReferral = offline.isReferral)
                        )
                        _state.update { it.copy(error = e.userMessage("The assistant could not answer")) }
                    }
            } else {
                val answer = Coach.answer(clean, context)
                reply(CoachMessage(answer.text, fromUser = false, source = answer.source, isReferral = answer.isReferral))
            }
        }
    }

    private fun reply(message: CoachMessage) {
        _state.update { it.copy(messages = it.messages + message, isThinking = false) }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    /** Everything the coach is allowed to know, read once per question. */
    private suspend fun gatherContext(): Coach.Context {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())

        val history = runCatching { workoutRepository.observeHistory(30).first() }.getOrDefault(emptyList())
        val user = runCatching { profileRepository.observeUser().first() }.getOrNull()
        val muscleRows = runCatching { goalRepository.observeMuscleSessions().first() }.getOrDefault(emptyList())
        val measurements = runCatching { bodyRepository.observeMeasurements().first() }.getOrDefault(emptyList())

        fun dayOf(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val thisWeek = history.map { it.workout }.filter { !dayOf(it.startedAt).isBefore(weekStart) }

        val recovery = Recovery.states(
            muscleRows.map { Recovery.MuscleSession(it.muscle, it.trainedAt, it.volume) },
            System.currentTimeMillis()
        ).filterNot { it.isFresh }

        // The heaviest thing done on each of the last few exercises, which is
        // what a question about progress is usually about.
        val lifts = history.flatMap { detail ->
            detail.exercises.map { exercise ->
                Coach.Lift(
                    name = exercise.exercise.name,
                    bestKg = exercise.sets.filter { it.isCompleted && it.type.countsTowardVolume }
                        .maxOfOrNull { it.weightKg ?: 0.0 } ?: 0.0,
                    lastReps = exercise.sets.lastOrNull { it.isCompleted }?.reps ?: 0
                )
            }
        }.filter { it.bestKg > 0 }.distinctBy { it.name }.sortedByDescending { it.bestKg }.take(3)

        val weights = measurements.mapNotNull { m -> m.weightKg?.let { m.takenAt to it } }.sortedBy { it.first }

        return Coach.Context(
            displayName = user?.displayName.orEmpty(),
            workoutsThisWeek = thisWeek.size,
            weeklyGoal = user?.weeklyGoalWorkouts ?: 4,
            streakWeeks = TrainingMath.weeklyStreak(history.map { it.workout.startedAt }),
            recordsThisWeek = thisWeek.sumOf { it.prCount },
            volumeThisWeekKg = thisWeek.sumOf { it.totalVolumeKg },
            recovering = recovery.map {
                Coach.Recovering(it.muscle, (it.fraction * 100).toInt(), it.hoursRemaining)
            },
            topLifts = lifts,
            bodyweightKg = weights.lastOrNull()?.second,
            bodyweightChangeKg = if (weights.size >= 2) weights.last().second - weights.first().second else null
        )
    }

    /**
     * The same context as a few lines of text for the cloud model.
     *
     * Written out explicitly rather than serialising the whole log: this is the
     * payload that leaves the device, so it should be short, readable, and
     * obviously free of anything the user did not expect to send.
     */
    private fun describe(c: Coach.Context): String = buildString {
        appendLine("Here is the user's training data.")
        appendLine("Sessions this week: ${c.workoutsThisWeek} of a goal of ${c.weeklyGoal}.")
        appendLine("Weekly streak: ${c.streakWeeks}. Records this week: ${c.recordsThisWeek}.")
        appendLine("Volume this week: ${c.volumeThisWeekKg.toInt()} kg.")
        if (c.recovering.isNotEmpty()) {
            appendLine("Still recovering: " + c.recovering.joinToString { "${it.muscle.label} ${it.percent}%" })
        }
        if (c.topLifts.isNotEmpty()) {
            appendLine("Heaviest lifts: " + c.topLifts.joinToString { "${it.name} ${it.bestKg.toInt()} kg" })
        }
        c.bodyweightKg?.let { appendLine("Bodyweight: ${it.toInt()} kg.") }
    }
}
