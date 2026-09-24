package com.ironlog.app.data.repository

import com.ironlog.app.core.location.CardioProgress
import com.ironlog.app.core.location.GeoMath
import com.ironlog.app.core.location.RoutePoint
import com.ironlog.app.core.security.Validators
import com.ironlog.app.core.sensors.StepCounter
import com.ironlog.app.data.dao.CardioDao
import com.ironlog.app.data.dao.UserDao
import com.ironlog.app.data.entity.CardioSessionEntity
import com.ironlog.app.data.entity.CardioType
import com.ironlog.app.data.entity.DailyActivityEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface CardioRepository {
    fun observeSessions(): Flow<List<CardioSessionEntity>>
    fun observeSession(id: String): Flow<CardioSessionEntity?>
    fun decodeRoute(session: CardioSessionEntity): List<RoutePoint>
    suspend fun save(
        type: CardioType,
        startedAt: Long,
        progress: CardioProgress,
        steps: Int,
        note: String,
        avgHeartRate: Int? = null,
        maxHeartRate: Int? = null
    ): Result<String>
    suspend fun delete(id: String): Result<Unit>

    /** Folds a raw pedometer reading into today's total and returns that total. */
    suspend fun recordStepReading(raw: Float): Result<Int>
    fun observeTodaySteps(): Flow<Int>
    fun observeStepsSince(days: Int): Flow<List<DailyActivityEntity>>
}

@Singleton
class CardioRepositoryImpl @Inject constructor(
    private val cardioDao: CardioDao,
    private val userDao: UserDao,
    private val stepCounter: StepCounter,
    private val currentUser: CurrentUser
) : CardioRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val routeSerializer = ListSerializer(RoutePoint.serializer())

    override fun observeSessions(): Flow<List<CardioSessionEntity>> =
        currentUser.idOrNull()?.let { cardioDao.observeSessions(it) } ?: flowOf(emptyList())

    override fun observeSession(id: String): Flow<CardioSessionEntity?> = cardioDao.observeSession(id)

    override fun decodeRoute(session: CardioSessionEntity): List<RoutePoint> =
        runCatching { json.decodeFromString(routeSerializer, session.routeJson) }.getOrDefault(emptyList())

    override suspend fun save(
        type: CardioType,
        startedAt: Long,
        progress: CardioProgress,
        steps: Int,
        note: String,
        avgHeartRate: Int?,
        maxHeartRate: Int?
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = currentUser.require()
            if (progress.elapsedSeconds < MIN_SESSION_SECONDS) {
                throw UserFacingException("That session was too short to save")
            }
            val bodyweight = userDao.findById(userId)?.bodyweightKg ?: DEFAULT_BODYWEIGHT_KG
            val session = CardioSessionEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                type = type,
                startedAt = startedAt,
                finishedAt = System.currentTimeMillis(),
                distanceMeters = progress.distanceMeters,
                durationSeconds = progress.elapsedSeconds,
                avgPaceSecondsPerKm = GeoMath.avgPaceSecondsPerKm(progress.distanceMeters, progress.elapsedSeconds),
                elevationGainMeters = GeoMath.elevationGainMeters(progress.points),
                estimatedCalories = GeoMath.estimateCalories(
                    progress.distanceMeters, progress.elapsedSeconds, bodyweight, met(type)
                ),
                steps = steps.coerceAtLeast(0),
                routeJson = json.encodeToString(routeSerializer, progress.points),
                note = Validators.sanitiseText(note, 300),
                avgHeartRate = avgHeartRate?.takeIf { it in 20..250 },
                maxHeartRate = maxHeartRate?.takeIf { it in 20..250 }
            )
            cardioDao.upsertSession(session)
            session.id
        }
    }

    override suspend fun delete(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val session = cardioDao.findSession(id) ?: throw UserFacingException("Session not found")
            if (session.userId != currentUser.require()) throw UserFacingException("Session not found")
            cardioDao.deleteSession(id)
        }
    }

    override suspend fun recordStepReading(raw: Float): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = currentUser.require()
            val today = LocalDate.now().toEpochDay()
            val stored = cardioDao.dailyActivity(userId, today)
            val storedBaseline = stored?.sensorBaseline

            // Invariant: steps = raw - baseline. A reading below the baseline means
            // the device rebooted and the hardware counter restarted at zero; the
            // steps already counted today are kept by shifting the baseline down
            // (it may go negative) rather than being lost.
            val baseline = when {
                storedBaseline == null -> stepCounter.resolve(raw, null).baseline
                raw < storedBaseline -> -(stored?.steps ?: 0).toFloat()
                else -> storedBaseline
            }
            val steps = (raw - baseline).toInt().coerceAtLeast(0)

            cardioDao.upsertDailyActivity(
                DailyActivityEntity(
                    userId = userId,
                    dateEpochDay = today,
                    steps = steps,
                    sensorBaseline = baseline,
                    updatedAt = System.currentTimeMillis()
                )
            )
            steps
        }
    }

    override fun observeTodaySteps(): Flow<Int> {
        val userId = currentUser.idOrNull() ?: return flowOf(0)
        return cardioDao.observeDailyActivity(userId, LocalDate.now().toEpochDay()).map { it?.steps ?: 0 }
    }

    override fun observeStepsSince(days: Int): Flow<List<DailyActivityEntity>> {
        val userId = currentUser.idOrNull() ?: return flowOf(emptyList())
        return cardioDao.observeActivityRange(userId, LocalDate.now().minusDays(days.toLong()).toEpochDay())
    }

    /** Compendium of Physical Activities MET values at moderate intensity. */
    private fun met(type: CardioType): Double = when (type) {
        CardioType.RUN -> 9.8
        CardioType.WALK -> 3.5
        CardioType.CYCLE -> 7.5
        CardioType.HIKE -> 6.0
    }

    private companion object {
        const val MIN_SESSION_SECONDS = 10
        const val DEFAULT_BODYWEIGHT_KG = 70.0
    }
}
