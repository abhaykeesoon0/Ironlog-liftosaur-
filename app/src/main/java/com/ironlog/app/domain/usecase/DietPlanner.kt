package com.ironlog.app.domain.usecase

import com.ironlog.app.data.entity.FoodEntity
import com.ironlog.app.data.entity.FoodGroup
import com.ironlog.app.data.entity.Meal
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * A day of meals that lands near the targets, built from the food library.
 *
 * Not a nutritionist and not a language model: a plan is assembled from a few
 * rules a sensible person would use. Each meal gets a share of the day, every
 * main meal has a protein, a carbohydrate and a vegetable, and portions are
 * sized so the protein target is met first, since that is the one that
 * matters most to someone who lifts. The rest of the energy is filled with
 * carbohydrate and fat in the proportions the targets ask for.
 *
 * Given the same seed it produces the same plan, so "shuffle" is a new seed
 * rather than a surprise, and a plan can be tested.
 */
object DietPlanner {

    data class Portion(val food: FoodEntity, val grams: Double, val isProtein: Boolean = false) {
        val totals: NutritionMath.Totals
            get() = NutritionMath.portion(grams, food.kcalPer100g, food.proteinPer100g, food.carbsPer100g, food.fatPer100g)
    }

    data class PlannedMeal(val meal: Meal, val portions: List<Portion>) {
        val totals: NutritionMath.Totals get() = portions.fold(NutritionMath.Totals()) { sum, p -> sum + p.totals }
    }

    data class Plan(val meals: List<PlannedMeal>, val targets: NutritionMath.Targets) {
        val totals: NutritionMath.Totals get() = meals.fold(NutritionMath.Totals()) { sum, m -> sum + m.totals }
    }

    /** How the day's energy is split across the meals. */
    private val MEAL_SHARE = mapOf(
        Meal.BREAKFAST to 0.25,
        Meal.LUNCH to 0.30,
        Meal.DINNER to 0.35,
        Meal.SNACK to 0.10
    )

    /** A portion is never smaller than a mouthful or bigger than a plate. */
    private const val MIN_GRAMS = 20.0
    private const val MAX_GRAMS = 400.0

    /** Enough protein per hundred grams to be worth sizing a portion around. */
    private const val PROTEIN_RICH_PER_100G = 8.0

    fun plan(foods: List<FoodEntity>, targets: NutritionMath.Targets, seed: Int = 0): Plan {
        val random = Random(seed)
        val library = foods.filter { !it.isCustom }.ifEmpty { foods }
        fun pick(group: FoodGroup): FoodEntity? = library.filter { it.group == group }.randomOrNull(random)

        // A breakfast or snack protein has to actually carry protein: a glass
        // of milk cannot reach its share inside a sensible portion, but a pot
        // of skyr or a scoop of whey can.
        fun pickProteinRich(vararg groups: FoodGroup): FoodEntity? =
            library.filter { it.group in groups && it.proteinPer100g >= PROTEIN_RICH_PER_100G }
                .randomOrNull(random) ?: pick(groups.first())

        val meals = Meal.entries.map { meal ->
            val share = MEAL_SHARE.getValue(meal)
            val kcal = targets.kcal * share
            val protein = targets.proteinG * share
            val carbs = targets.carbsG * share

            val portions = when (meal) {
                Meal.BREAKFAST -> listOfNotNull(
                    pickProteinRich(FoodGroup.DAIRY)?.let { toProtein(it, protein) },
                    pick(FoodGroup.CARB)?.let { toCarbs(it, carbs) },
                    pick(FoodGroup.FRUIT)?.let { Portion(it, it.servingGrams) }
                )
                Meal.LUNCH, Meal.DINNER -> listOfNotNull(
                    pick(FoodGroup.PROTEIN)?.let { toProtein(it, protein) },
                    pick(FoodGroup.CARB)?.let { toCarbs(it, carbs) },
                    pick(FoodGroup.VEGETABLE)?.let { Portion(it, 150.0) },
                    pick(FoodGroup.FAT)?.let { Portion(it, it.servingGrams) }
                )
                Meal.SNACK -> listOfNotNull(
                    // A snack carries its share of the protein too, or a tenth
                    // of the day's protein is never planned for.
                    pickProteinRich(FoodGroup.DAIRY, FoodGroup.SNACK)?.let { toProtein(it, protein) },
                    pick(FoodGroup.FRUIT)?.let { Portion(it, it.servingGrams) }
                )
            }
            PlannedMeal(meal, trimToEnergy(portions, kcal))
        }
        return Plan(meals, targets)
    }

    /** Grams of a food that carry [proteinG] of protein, within sensible bounds. */
    private fun toProtein(food: FoodEntity, proteinG: Double): Portion {
        val perGram = food.proteinPer100g / 100.0
        val grams = if (perGram > 0) proteinG / perGram else food.servingGrams
        return Portion(food, grams.coerceIn(MIN_GRAMS, MAX_GRAMS).roundTo(5), isProtein = true)
    }

    /** Grams of a food that carry [carbsG] of carbohydrate, within sensible bounds. */
    private fun toCarbs(food: FoodEntity, carbsG: Double): Portion {
        val perGram = food.carbsPer100g / 100.0
        val grams = if (perGram > 0) carbsG / perGram else food.servingGrams
        return Portion(food, grams.coerceIn(MIN_GRAMS, MAX_GRAMS).roundTo(5))
    }

    /**
     * Scales a meal back if it overshoots its energy share by a wide margin.
     *
     * Protein first, as promised: the fillers -- the carbohydrate, the fat, the
     * fruit -- give way before the protein does, and the vegetables are never
     * touched. Only if the protein portion alone is over the meal's energy does
     * it give too, because a target has to be met somewhere.
     */
    private fun trimToEnergy(portions: List<Portion>, targetKcal: Double): List<Portion> {
        fun kcalOf(list: List<Portion>) = list.sumOf { it.totals.kcal }
        if (kcalOf(portions) <= targetKcal * 1.15) return portions

        fun isFixed(p: Portion) = p.isProtein || p.food.group == FoodGroup.VEGETABLE
        val fixedKcal = portions.filter(::isFixed).sumOf { it.totals.kcal }
        val fillerKcal = portions.filterNot(::isFixed).sumOf { it.totals.kcal }
        val room = (targetKcal - fixedKcal).coerceAtLeast(0.0)

        var trimmed = portions
        if (fillerKcal > 0.0) {
            val factor = (room / fillerKcal).coerceAtMost(1.0)
            trimmed = trimmed.map { p ->
                if (isFixed(p)) p else p.copy(grams = (p.grams * factor).coerceAtLeast(MIN_GRAMS).roundTo(5))
            }
        }
        if (kcalOf(trimmed) > targetKcal * 1.15) {
            val factor = targetKcal / kcalOf(trimmed)
            trimmed = trimmed.map { p ->
                if (p.isProtein) p.copy(grams = (p.grams * factor).coerceAtLeast(MIN_GRAMS).roundTo(5)) else p
            }
        }
        return trimmed
    }

    private fun Double.roundTo(step: Int): Double = (this / step).roundToInt() * step.toDouble()
}
