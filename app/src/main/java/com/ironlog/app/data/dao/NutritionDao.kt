package com.ironlog.app.data.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import com.ironlog.app.data.entity.FoodEntity
import com.ironlog.app.data.entity.FoodLogEntity
import com.ironlog.app.data.entity.NutritionGoalEntity
import kotlinx.coroutines.flow.Flow

/** A logged portion together with the food it is a portion of. */
data class FoodLogWithFood(
    @Embedded val log: FoodLogEntity,
    @Relation(parentColumn = "foodId", entityColumn = "id")
    val food: FoodEntity
) {
    private val scale: Double get() = log.grams / 100.0
    val kcal: Double get() = food.kcalPer100g * scale
    val proteinG: Double get() = food.proteinPer100g * scale
    val carbsG: Double get() = food.carbsPer100g * scale
    val fatG: Double get() = food.fatPer100g * scale
}

@Dao
interface NutritionDao {

    /** The shared library plus this user's own foods, in one list. */
    @Query("SELECT * FROM foods WHERE ownerUserId IS NULL OR ownerUserId = :userId ORDER BY name")
    fun observeFoods(userId: String): Flow<List<FoodEntity>>

    @Query("SELECT COUNT(*) FROM foods WHERE ownerUserId IS NULL")
    suspend fun countSeeded(): Int

    @Upsert suspend fun upsertFoods(items: List<FoodEntity>)
    @Upsert suspend fun upsertFood(item: FoodEntity)

    @Query("SELECT * FROM foods WHERE id = :id LIMIT 1")
    suspend fun findFood(id: String): FoodEntity?

    /** A food already scanned from this code, shared or the user's own. */
    @Query("SELECT * FROM foods WHERE barcode = :barcode AND (ownerUserId IS NULL OR ownerUserId = :userId) LIMIT 1")
    suspend fun findByBarcode(userId: String, barcode: String): FoodEntity?

    @Upsert suspend fun upsertLog(item: FoodLogEntity)

    @Query("DELETE FROM food_logs WHERE id = :id AND userId = :userId")
    suspend fun deleteLog(userId: String, id: String)

    @Transaction
    @Query(
        """
        SELECT * FROM food_logs
        WHERE userId = :userId AND loggedAt >= :from AND loggedAt < :to
        ORDER BY loggedAt ASC
        """
    )
    fun observeLogsBetween(userId: String, from: Long, to: Long): Flow<List<FoodLogWithFood>>

    @Query("SELECT * FROM nutrition_goals WHERE userId = :userId LIMIT 1")
    fun observeGoal(userId: String): Flow<NutritionGoalEntity?>

    @Upsert suspend fun upsertGoal(goal: NutritionGoalEntity)
}
