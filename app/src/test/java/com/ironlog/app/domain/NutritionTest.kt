package com.ironlog.app.domain

import com.ironlog.app.data.entity.ActivityLevel
import com.ironlog.app.data.entity.DietGoal
import com.ironlog.app.data.entity.FoodGroup
import com.ironlog.app.data.entity.Meal
import com.ironlog.app.data.entity.Sex
import com.ironlog.app.data.seed.FoodSeed
import com.ironlog.app.domain.usecase.DietPlanner
import com.ironlog.app.domain.usecase.NutritionMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class NutritionTest {

    @Test
    fun `resting energy matches the published Mifflin-St Jeor worked examples`() {
        // 80 kg, 180 cm, 30-year-old man: 800 + 1125 - 150 + 5.
        assertEquals(1780.0, NutritionMath.restingKcal(Sex.MALE, 80.0, 180.0, 30), 1e-6)
        // 60 kg, 165 cm, 25-year-old woman: 600 + 1031.25 - 125 - 161.
        assertEquals(1345.25, NutritionMath.restingKcal(Sex.FEMALE, 60.0, 165.0, 25), 1e-6)
    }

    @Test
    fun `a cut sits under maintenance and a bulk over it, by the stated amounts`() {
        val maintain = NutritionMath.targets(DietGoal.MAINTAIN, Sex.MALE, 80.0, 180.0, 30, ActivityLevel.MODERATE)
        val cut = NutritionMath.targets(DietGoal.CUT, Sex.MALE, 80.0, 180.0, 30, ActivityLevel.MODERATE)
        val bulk = NutritionMath.targets(DietGoal.BULK, Sex.MALE, 80.0, 180.0, 30, ActivityLevel.MODERATE)
        assertEquals(maintain.kcal - 500, cut.kcal)
        assertEquals(maintain.kcal + 300, bulk.kcal)
    }

    @Test
    fun `the macros add back up to the energy target`() {
        val t = NutritionMath.targets(DietGoal.MAINTAIN, Sex.FEMALE, 65.0, 168.0, 28, ActivityLevel.LIGHT)
        // Rounding of each macro to whole grams can drift by a few kcal, no more.
        assertTrue(kotlin.math.abs(t.kcal - (t.proteinKcal + t.carbsKcal + t.fatKcal)) <= 12)
        assertEquals((65.0 * 1.8).roundToInt(), t.proteinG)
    }

    @Test
    fun `the target never drops below the floor, whatever the goal`() {
        val tiny = NutritionMath.targets(DietGoal.CUT, Sex.FEMALE, 42.0, 150.0, 70, ActivityLevel.SEDENTARY)
        assertEquals(NutritionMath.MIN_TARGET_KCAL, tiny.kcal)
    }

    @Test
    fun `a plan has a protein, a carbohydrate and a vegetable in every main meal`() {
        val targets = NutritionMath.targets(DietGoal.MAINTAIN, Sex.MALE, 80.0, 180.0, 30, ActivityLevel.MODERATE)
        val plan = DietPlanner.plan(FoodSeed.all, targets, seed = 7)
        listOf(Meal.LUNCH, Meal.DINNER).forEach { meal ->
            val groups = plan.meals.single { it.meal == meal }.portions.map { it.food.group }.toSet()
            assertTrue("$meal lacks protein", FoodGroup.PROTEIN in groups)
            assertTrue("$meal lacks carbohydrate", FoodGroup.CARB in groups)
            assertTrue("$meal lacks vegetables", FoodGroup.VEGETABLE in groups)
        }
    }

    @Test
    fun `a plan lands near the targets, protein first`() {
        val targets = NutritionMath.targets(DietGoal.CUT, Sex.MALE, 85.0, 178.0, 34, ActivityLevel.LIGHT)
        repeat(20) { seed ->
            val plan = DietPlanner.plan(FoodSeed.all, targets, seed)
            val totals = plan.totals
            assertTrue("seed $seed protein ${totals.proteinG} vs ${targets.proteinG}", totals.proteinG >= targets.proteinG * 0.8)
            assertTrue("seed $seed kcal ${totals.kcal} vs ${targets.kcal}", totals.kcal <= targets.kcal * 1.2)
            assertTrue("seed $seed kcal too low ${totals.kcal}", totals.kcal >= targets.kcal * 0.6)
        }
    }

    @Test
    fun `the same seed gives the same plan and a different seed a different one`() {
        val targets = NutritionMath.targets(DietGoal.MAINTAIN, Sex.FEMALE, 62.0, 166.0, 27, ActivityLevel.MODERATE)
        val a = DietPlanner.plan(FoodSeed.all, targets, seed = 3)
        val b = DietPlanner.plan(FoodSeed.all, targets, seed = 3)
        val c = DietPlanner.plan(FoodSeed.all, targets, seed = 4)
        assertEquals(a.meals.map { m -> m.portions.map { it.food.id to it.grams } }, b.meals.map { m -> m.portions.map { it.food.id to it.grams } })
        assertTrue(a.meals.map { m -> m.portions.map { it.food.id } } != c.meals.map { m -> m.portions.map { it.food.id } })
    }

    @Test
    fun `seeded foods have unique ids and every group is stocked`() {
        assertEquals(FoodSeed.all.size, FoodSeed.all.map { it.id }.distinct().size)
        FoodGroup.entries.forEach { group ->
            assertTrue("no $group in the library", FoodSeed.all.any { it.group == group })
        }
        assertTrue(FoodSeed.all.none { it.isCustom || it.ownerUserId != null })
    }
}
