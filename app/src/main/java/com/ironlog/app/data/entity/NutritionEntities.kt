package com.ironlog.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Food and what was eaten.
 *
 * Nutrition sits beside the training log rather than inside it: a meal is not
 * a set, and a food is not an exercise. The same rules hold, though. All
 * amounts are stored in grams and kilocalories, the seeded foods have no owner
 * and are shared across every account on the device, and anything a user types
 * goes through the validators before it is written.
 */

enum class Meal(val label: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACK("Snacks")
}

/** What a food is mostly for, which is how a plan decides where it goes. */
enum class FoodGroup(val label: String) {
    PROTEIN("Protein"),
    CARB("Carbohydrate"),
    VEGETABLE("Vegetables"),
    FRUIT("Fruit"),
    DAIRY("Dairy"),
    FAT("Fats and nuts"),
    SNACK("Snacks and treats")
}

/**
 * Where the calorie target sits against maintenance.
 *
 * The deficit and surplus are the modest ones the evidence supports: half a
 * kilo a week down, which keeps most of the strength, and a small surplus up,
 * which keeps most of it muscle. A crash diet is not on the list on purpose.
 */
enum class DietGoal(val label: String, val detail: String, val kcalDelta: Int) {
    CUT("Lose fat", "About 500 kcal under maintenance", -500),
    MAINTAIN("Maintain", "Eat at maintenance", 0),
    BULK("Build muscle", "About 300 kcal over maintenance", 300)
}

/** Activity outside the gym, as a multiplier on resting energy. */
enum class ActivityLevel(val label: String, val detail: String, val factor: Double) {
    SEDENTARY("Sedentary", "Desk job, little walking", 1.2),
    LIGHT("Lightly active", "On your feet some of the day", 1.375),
    MODERATE("Active", "On your feet most of the day", 1.55),
    VERY("Very active", "Physical work or two sessions a day", 1.725)
}

/** The two equations resting energy has been measured for. */
enum class Sex(val label: String) {
    MALE("Male"),
    FEMALE("Female")
}

@Entity(tableName = "foods")
data class FoodEntity(
    @PrimaryKey val id: String,
    val name: String,
    val group: FoodGroup,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    /** One ordinary portion, so a user can log "a banana" rather than "118 g". */
    val servingGrams: Double,
    val servingLabel: String,
    /** Seeded foods have no owner and are shared; a custom food belongs to one user. */
    val ownerUserId: String? = null,
    val isCustom: Boolean = false,
    /** The product code it was scanned from, so the same label is looked up once. */
    val barcode: String? = null
)

@Entity(
    tableName = "food_logs",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId", "loggedAt"]), Index(value = ["foodId"])]
)
data class FoodLogEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val foodId: String,
    val grams: Double,
    val meal: Meal,
    val loggedAt: Long,
    val note: String = ""
)

/**
 * One row per user: the goal they chose and the targets worked out from it.
 *
 * The targets are stored rather than recalculated so the numbers on the
 * screen do not drift when a weigh-in changes the profile. Saving the goal
 * again is how they are refreshed.
 */
@Entity(
    tableName = "nutrition_goals",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class NutritionGoalEntity(
    @PrimaryKey val userId: String,
    val goal: DietGoal,
    val sex: Sex,
    val ageYears: Int,
    val activity: ActivityLevel,
    val targetKcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val updatedAt: Long
)
