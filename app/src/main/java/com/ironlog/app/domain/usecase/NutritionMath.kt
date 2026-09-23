package com.ironlog.app.domain.usecase

import com.ironlog.app.data.entity.ActivityLevel
import com.ironlog.app.data.entity.DietGoal
import com.ironlog.app.data.entity.Sex
import kotlin.math.roundToInt

/**
 * Energy and macronutrient arithmetic.
 *
 * Pure functions with everything passed in, so a target can be tested against
 * a published worked example rather than trusted. The equations are the ones
 * in common clinical use, and the app says so on screen: this is an estimate
 * to start from, not a prescription, and a dietitian is the person to see for
 * one of those.
 */
object NutritionMath {

    /** Below this the estimate stops being sensible for an adult, whatever the goal. */
    const val MIN_TARGET_KCAL = 1_200

    /** The share of energy from fat every target keeps, so hormones and joints are fed. */
    private const val FAT_SHARE = 0.25

    /**
     * Resting energy, by Mifflin-St Jeor: the equation with the smallest error
     * against measured metabolic rate in the comparisons that have been done.
     */
    fun restingKcal(sex: Sex, weightKg: Double, heightCm: Double, ageYears: Int): Double {
        val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * ageYears
        return when (sex) {
            Sex.MALE -> base + 5.0
            Sex.FEMALE -> base - 161.0
        }
    }

    /** Resting energy scaled by how active the rest of the day is. */
    fun maintenanceKcal(sex: Sex, weightKg: Double, heightCm: Double, ageYears: Int, activity: ActivityLevel): Double =
        restingKcal(sex, weightKg, heightCm, ageYears) * activity.factor

    data class Targets(val kcal: Int, val proteinG: Int, val carbsG: Int, val fatG: Int) {
        val proteinKcal: Int get() = proteinG * 4
        val carbsKcal: Int get() = carbsG * 4
        val fatKcal: Int get() = fatG * 9
    }

    /**
     * Daily targets for a goal.
     *
     * Protein is set per kilogram of bodyweight, higher on a cut where it does
     * the most to keep muscle, and it comes off the top. Fat takes a fixed
     * quarter of the energy. Whatever is left is carbohydrate, which is the
     * nutrient with the most room to move and the one that fuels the sessions.
     */
    fun targets(
        goal: DietGoal,
        sex: Sex,
        weightKg: Double,
        heightCm: Double,
        ageYears: Int,
        activity: ActivityLevel
    ): Targets {
        val maintenance = maintenanceKcal(sex, weightKg, heightCm, ageYears, activity)
        val kcal = (maintenance + goal.kcalDelta).roundToInt().coerceAtLeast(MIN_TARGET_KCAL)

        val proteinPerKg = when (goal) {
            DietGoal.CUT -> 2.2
            DietGoal.MAINTAIN -> 1.8
            DietGoal.BULK -> 1.8
        }
        val proteinG = (weightKg * proteinPerKg).roundToInt()
        val fatG = (kcal * FAT_SHARE / 9.0).roundToInt()
        val carbsG = ((kcal - proteinG * 4 - fatG * 9) / 4.0).roundToInt().coerceAtLeast(0)

        return Targets(kcal, proteinG, carbsG, fatG)
    }

    /** A day's running total. */
    data class Totals(val kcal: Double = 0.0, val proteinG: Double = 0.0, val carbsG: Double = 0.0, val fatG: Double = 0.0) {
        operator fun plus(other: Totals) =
            Totals(kcal + other.kcal, proteinG + other.proteinG, carbsG + other.carbsG, fatG + other.fatG)
    }

    /** The energy and macros in [grams] of a food described per hundred grams. */
    fun portion(grams: Double, kcalPer100g: Double, proteinPer100g: Double, carbsPer100g: Double, fatPer100g: Double): Totals {
        val scale = grams / 100.0
        return Totals(kcalPer100g * scale, proteinPer100g * scale, carbsPer100g * scale, fatPer100g * scale)
    }
}
