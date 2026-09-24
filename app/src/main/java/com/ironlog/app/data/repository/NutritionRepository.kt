package com.ironlog.app.data.repository

import com.ironlog.app.core.security.Validators
import com.ironlog.app.data.dao.FoodLogWithFood
import com.ironlog.app.data.dao.NutritionDao
import com.ironlog.app.data.dao.UserDao
import com.ironlog.app.data.entity.ActivityLevel
import com.ironlog.app.data.entity.DietGoal
import com.ironlog.app.data.entity.FoodEntity
import com.ironlog.app.data.entity.FoodGroup
import com.ironlog.app.data.entity.FoodLogEntity
import com.ironlog.app.data.entity.Meal
import com.ironlog.app.data.entity.NutritionGoalEntity
import com.ironlog.app.data.entity.Sex
import com.ironlog.app.domain.usecase.NutritionMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import com.ironlog.app.core.nutrition.OpenFoodFacts

/** What the goal form collects. Height and weight fall back to the profile when left out. */
data class GoalInput(
    val goal: DietGoal,
    val sex: Sex,
    val ageYears: Int,
    val activity: ActivityLevel,
    val heightCm: Double? = null,
    val weightKg: Double? = null
)

/** A food the user is adding to the library, per hundred grams. */
data class FoodInput(
    val name: String,
    val group: FoodGroup,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val servingGrams: Double,
    /** Set when the figures came from a scanned label. */
    val barcode: String? = null
)

/** What a scan came back with: a food already in the library, or one to confirm. */
sealed interface ScanResult {
    data class Known(val food: FoodEntity) : ScanResult
    data class Found(val input: FoodInput, val source: String) : ScanResult
}

interface NutritionRepository {
    fun observeFoods(): Flow<List<FoodEntity>>
    /** Everything logged on the local calendar day that [at] falls in. */
    fun observeDay(at: Long): Flow<List<FoodLogWithFood>>
    fun observeLogsSince(since: Long): Flow<List<FoodLogWithFood>>
    fun observeGoal(): Flow<NutritionGoalEntity?>

    suspend fun logFood(foodId: String, grams: Double, meal: Meal, at: Long, note: String = ""): Result<Unit>
    suspend fun deleteLog(id: String): Result<Unit>
    /** Puts a removed entry back, for the undo bar. */
    suspend fun restoreLog(entry: FoodLogEntity): Result<Unit>
    suspend fun createFood(input: FoodInput): Result<FoodEntity>
    suspend fun saveGoal(input: GoalInput): Result<NutritionGoalEntity>

    /**
     * Resolves a barcode: the library first, so a product scanned before never
     * needs the network again, and Open Food Facts only when it is new.
     */
    suspend fun scan(barcode: String): Result<ScanResult>
}

@Singleton
class NutritionRepositoryImpl @Inject constructor(
    private val nutritionDao: NutritionDao,
    private val userDao: UserDao,
    private val currentUser: CurrentUser,
    private val openFoodFacts: OpenFoodFacts
) : NutritionRepository {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    override fun observeFoods(): Flow<List<FoodEntity>> =
        currentUser.idOrNull()?.let { nutritionDao.observeFoods(it) } ?: flowOf(emptyList())

    override fun observeDay(at: Long): Flow<List<FoodLogWithFood>> {
        val userId = currentUser.idOrNull() ?: return flowOf(emptyList())
        // Local midnight to local midnight: a late snack belongs to the evening
        // it was eaten in, not to the next UTC day.
        val day = java.time.Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        val from = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return nutritionDao.observeLogsBetween(userId, from, to)
    }

    override fun observeLogsSince(since: Long): Flow<List<FoodLogWithFood>> =
        currentUser.idOrNull()?.let { nutritionDao.observeLogsBetween(it, since, Long.MAX_VALUE) }
            ?: flowOf(emptyList())

    override fun observeGoal(): Flow<NutritionGoalEntity?> =
        currentUser.idOrNull()?.let { nutritionDao.observeGoal(it) } ?: flowOf(null)

    override suspend fun logFood(foodId: String, grams: Double, meal: Meal, at: Long, note: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val userId = currentUser.require()
                val food = nutritionDao.findFood(foodId) ?: throw UserFacingException("That food is not in the library")
                if (food.ownerUserId != null && food.ownerUserId != userId) throw UserFacingException("That food is not in the library")
                if (grams !in 1.0..5_000.0) throw UserFacingException("Enter an amount between 1 g and 5 kg")
                nutritionDao.upsertLog(
                    FoodLogEntity(
                        id = UUID.randomUUID().toString(),
                        userId = userId,
                        foodId = food.id,
                        grams = grams,
                        meal = meal,
                        loggedAt = at,
                        note = Validators.sanitiseText(note, 200)
                    )
                )
            }
        }

    override suspend fun restoreLog(entry: FoodLogEntity): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (entry.userId != currentUser.require()) throw UserFacingException("Entry not found")
            nutritionDao.upsertLog(entry)
        }
    }

    override suspend fun deleteLog(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { nutritionDao.deleteLog(currentUser.require(), id) }
    }

    override suspend fun createFood(input: FoodInput): Result<FoodEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = currentUser.require()
            val name = Validators.sanitiseSingleLine(input.name, 60)
            if (name.length < 2) throw UserFacingException("Give the food a name")
            listOf(input.kcalPer100g, input.proteinPer100g, input.carbsPer100g, input.fatPer100g).forEach {
                if (it < 0.0 || it > 1_000.0) throw UserFacingException("Check the figures per 100 g")
            }
            if (input.servingGrams !in 1.0..2_000.0) throw UserFacingException("Check the serving size")
            val food = FoodEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                group = input.group,
                kcalPer100g = input.kcalPer100g,
                proteinPer100g = input.proteinPer100g,
                carbsPer100g = input.carbsPer100g,
                fatPer100g = input.fatPer100g,
                servingGrams = input.servingGrams,
                servingLabel = "1 serving",
                ownerUserId = userId,
                isCustom = true,
                barcode = input.barcode?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() }
            )
            nutritionDao.upsertFood(food)
            food
        }
    }

    /**
     * Works the targets out and stores them with the goal.
     *
     * Height and weight come from the form when given and from the profile
     * otherwise, and a weight typed here is written back to the profile, the
     * same as a weigh-in on the body screen, so the two never disagree.
     */
    override suspend fun saveGoal(input: GoalInput): Result<NutritionGoalEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = currentUser.require()
            val user = userDao.findById(userId) ?: throw UserFacingException("Sign in again")
            val weight = input.weightKg ?: user.bodyweightKg
                ?: throw UserFacingException("Enter your bodyweight so the target can be worked out")
            val height = input.heightCm ?: user.heightCm
                ?: throw UserFacingException("Enter your height so the target can be worked out")
            if (input.ageYears !in 13..100) throw UserFacingException("Enter an age between 13 and 100")
            if (weight !in 20.0..400.0) throw UserFacingException("Check the bodyweight")
            if (height !in 100.0..250.0) throw UserFacingException("Check the height")

            val targets = NutritionMath.targets(input.goal, input.sex, weight, height, input.ageYears, input.activity)
            val goal = NutritionGoalEntity(
                userId = userId,
                goal = input.goal,
                sex = input.sex,
                ageYears = input.ageYears,
                activity = input.activity,
                targetKcal = targets.kcal,
                proteinG = targets.proteinG,
                carbsG = targets.carbsG,
                fatG = targets.fatG,
                updatedAt = System.currentTimeMillis()
            )
            nutritionDao.upsertGoal(goal)
            if (user.bodyweightKg != weight || user.heightCm != height) {
                userDao.update(user.copy(bodyweightKg = weight, heightCm = height))
            }
            goal
        }
    }

    override suspend fun scan(barcode: String): Result<ScanResult> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = currentUser.require()
            val clean = barcode.filter { it.isDigit() }
            if (clean.length !in 8..14) throw UserFacingException("That does not look like a product barcode")
            nutritionDao.findByBarcode(userId, clean)?.let { return@runCatching ScanResult.Known(it) }

            val product = openFoodFacts.lookup(clean)
            ScanResult.Found(
                input = FoodInput(
                    name = listOfNotNull(product.brand, product.name).joinToString(" "),
                    group = FoodGroup.SNACK,
                    kcalPer100g = product.kcalPer100g,
                    proteinPer100g = product.proteinPer100g,
                    carbsPer100g = product.carbsPer100g,
                    fatPer100g = product.fatPer100g,
                    servingGrams = product.servingGrams ?: 100.0,
                    barcode = clean
                ),
                source = "Open Food Facts, under the Open Database Licence"
            )
        }
    }

    /** Today, in local time, as a date. Exposed for the screens' "today" reads. */
    fun today(): LocalDate = LocalDate.now(zone)
}
