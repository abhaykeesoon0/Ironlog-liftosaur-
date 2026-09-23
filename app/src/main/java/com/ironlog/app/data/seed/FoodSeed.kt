package com.ironlog.app.data.seed

import com.ironlog.app.data.entity.FoodEntity
import com.ironlog.app.data.entity.FoodGroup

/**
 * The food library the app ships with.
 *
 * Sixty ordinary foods, per hundred grams, with one sensible serving each.
 * Values are typical figures of the kind printed on packaging and in national
 * food composition tables; a branded product will differ, which is what
 * custom foods are for. Enough to log most meals and to build a plan from,
 * small enough to scroll.
 *
 * Seeded rows have no owner, like the exercise library, so they are shared
 * across every account on the device and never appear as somebody's own.
 */
object FoodSeed {

    private fun food(
        id: String, name: String, group: FoodGroup,
        kcal: Double, protein: Double, carbs: Double, fat: Double,
        serving: Double, servingLabel: String
    ) = FoodEntity(
        id = "food-$id", name = name, group = group,
        kcalPer100g = kcal, proteinPer100g = protein, carbsPer100g = carbs, fatPer100g = fat,
        servingGrams = serving, servingLabel = servingLabel
    )

    val all: List<FoodEntity> = listOf(
        // -------------------------------------------------------------- protein
        food("chicken-breast", "Chicken breast, cooked", FoodGroup.PROTEIN, 165.0, 31.0, 0.0, 3.6, 150.0, "1 breast"),
        food("chicken-thigh", "Chicken thigh, cooked", FoodGroup.PROTEIN, 209.0, 26.0, 0.0, 11.0, 120.0, "1 thigh"),
        food("turkey-mince", "Turkey mince, cooked", FoodGroup.PROTEIN, 170.0, 27.0, 0.0, 6.0, 125.0, "1 portion"),
        food("beef-mince-5", "Beef mince 5%, cooked", FoodGroup.PROTEIN, 175.0, 26.0, 0.0, 7.5, 125.0, "1 portion"),
        food("beef-steak", "Beef steak, grilled", FoodGroup.PROTEIN, 210.0, 29.0, 0.0, 10.0, 170.0, "1 steak"),
        food("pork-loin", "Pork loin, grilled", FoodGroup.PROTEIN, 196.0, 27.0, 0.0, 9.5, 150.0, "1 chop"),
        food("salmon", "Salmon fillet, baked", FoodGroup.PROTEIN, 208.0, 22.0, 0.0, 13.0, 140.0, "1 fillet"),
        food("cod", "Cod fillet, baked", FoodGroup.PROTEIN, 105.0, 23.0, 0.0, 0.9, 150.0, "1 fillet"),
        food("tuna-tin", "Tuna, tinned in water", FoodGroup.PROTEIN, 116.0, 26.0, 0.0, 1.0, 100.0, "1 tin"),
        food("prawns", "Prawns, cooked", FoodGroup.PROTEIN, 99.0, 24.0, 0.2, 0.3, 100.0, "1 portion"),
        food("eggs", "Eggs, whole", FoodGroup.PROTEIN, 155.0, 13.0, 1.1, 11.0, 100.0, "2 eggs"),
        food("egg-whites", "Egg whites", FoodGroup.PROTEIN, 52.0, 11.0, 0.7, 0.2, 100.0, "3 whites"),
        food("tofu", "Tofu, firm", FoodGroup.PROTEIN, 144.0, 17.0, 3.0, 8.7, 150.0, "1 portion"),
        food("tempeh", "Tempeh", FoodGroup.PROTEIN, 192.0, 20.0, 8.0, 11.0, 100.0, "1 portion"),
        food("lentils", "Lentils, cooked", FoodGroup.PROTEIN, 116.0, 9.0, 20.0, 0.4, 200.0, "1 portion"),
        food("chickpeas", "Chickpeas, cooked", FoodGroup.PROTEIN, 164.0, 8.9, 27.0, 2.6, 150.0, "1 portion"),
        food("whey", "Whey protein powder", FoodGroup.PROTEIN, 380.0, 78.0, 6.0, 5.0, 30.0, "1 scoop"),

        // ---------------------------------------------------------------- carbs
        food("rice-white", "White rice, cooked", FoodGroup.CARB, 130.0, 2.7, 28.0, 0.3, 200.0, "1 portion"),
        food("rice-brown", "Brown rice, cooked", FoodGroup.CARB, 123.0, 2.7, 26.0, 1.0, 200.0, "1 portion"),
        food("pasta", "Pasta, cooked", FoodGroup.CARB, 158.0, 5.8, 31.0, 0.9, 200.0, "1 portion"),
        food("potato", "Potato, boiled", FoodGroup.CARB, 87.0, 1.9, 20.0, 0.1, 250.0, "1 portion"),
        food("sweet-potato", "Sweet potato, baked", FoodGroup.CARB, 90.0, 2.0, 21.0, 0.2, 200.0, "1 potato"),
        food("oats", "Porridge oats, dry", FoodGroup.CARB, 379.0, 13.0, 68.0, 6.5, 50.0, "1 bowl"),
        food("bread-wholemeal", "Wholemeal bread", FoodGroup.CARB, 247.0, 13.0, 41.0, 3.4, 80.0, "2 slices"),
        food("bread-white", "White bread", FoodGroup.CARB, 265.0, 9.0, 49.0, 3.2, 80.0, "2 slices"),
        food("bagel", "Bagel", FoodGroup.CARB, 250.0, 10.0, 49.0, 1.5, 90.0, "1 bagel"),
        food("quinoa", "Quinoa, cooked", FoodGroup.CARB, 120.0, 4.4, 21.0, 1.9, 185.0, "1 portion"),
        food("couscous", "Couscous, cooked", FoodGroup.CARB, 112.0, 3.8, 23.0, 0.2, 180.0, "1 portion"),
        food("tortilla", "Tortilla wrap", FoodGroup.CARB, 312.0, 8.0, 51.0, 8.0, 60.0, "1 wrap"),
        food("rice-cakes", "Rice cakes", FoodGroup.CARB, 387.0, 8.0, 82.0, 2.8, 18.0, "2 cakes"),

        // ----------------------------------------------------------- vegetables
        food("broccoli", "Broccoli, steamed", FoodGroup.VEGETABLE, 35.0, 2.4, 7.0, 0.4, 150.0, "1 portion"),
        food("spinach", "Spinach, raw", FoodGroup.VEGETABLE, 23.0, 2.9, 3.6, 0.4, 80.0, "1 handful"),
        food("mixed-salad", "Mixed salad leaves", FoodGroup.VEGETABLE, 17.0, 1.5, 2.5, 0.3, 80.0, "1 bowl"),
        food("green-beans", "Green beans, cooked", FoodGroup.VEGETABLE, 35.0, 1.9, 7.9, 0.3, 120.0, "1 portion"),
        food("carrot", "Carrots, cooked", FoodGroup.VEGETABLE, 35.0, 0.8, 8.2, 0.2, 100.0, "1 portion"),
        food("pepper", "Bell pepper, raw", FoodGroup.VEGETABLE, 31.0, 1.0, 6.0, 0.3, 120.0, "1 pepper"),
        food("tomato", "Tomatoes", FoodGroup.VEGETABLE, 18.0, 0.9, 3.9, 0.2, 120.0, "1 tomato"),
        food("cucumber", "Cucumber", FoodGroup.VEGETABLE, 15.0, 0.7, 3.6, 0.1, 100.0, "1 portion"),
        food("mushrooms", "Mushrooms, cooked", FoodGroup.VEGETABLE, 28.0, 2.2, 4.0, 0.5, 100.0, "1 portion"),
        food("peas", "Peas, cooked", FoodGroup.VEGETABLE, 84.0, 5.4, 15.0, 0.4, 100.0, "1 portion"),
        food("frozen-veg", "Mixed frozen vegetables", FoodGroup.VEGETABLE, 65.0, 3.3, 12.0, 0.6, 150.0, "1 portion"),

        // ---------------------------------------------------------------- fruit
        food("banana", "Banana", FoodGroup.FRUIT, 89.0, 1.1, 23.0, 0.3, 118.0, "1 banana"),
        food("apple", "Apple", FoodGroup.FRUIT, 52.0, 0.3, 14.0, 0.2, 180.0, "1 apple"),
        food("orange", "Orange", FoodGroup.FRUIT, 47.0, 0.9, 12.0, 0.1, 130.0, "1 orange"),
        food("blueberries", "Blueberries", FoodGroup.FRUIT, 57.0, 0.7, 14.0, 0.3, 100.0, "1 punnet"),
        food("strawberries", "Strawberries", FoodGroup.FRUIT, 32.0, 0.7, 7.7, 0.3, 150.0, "1 punnet"),
        food("grapes", "Grapes", FoodGroup.FRUIT, 69.0, 0.7, 18.0, 0.2, 100.0, "1 handful"),
        food("mango", "Mango", FoodGroup.FRUIT, 60.0, 0.8, 15.0, 0.4, 165.0, "1 cup"),

        // ---------------------------------------------------------------- dairy
        food("greek-yoghurt", "Greek yoghurt, 0%", FoodGroup.DAIRY, 59.0, 10.0, 3.6, 0.4, 170.0, "1 pot"),
        food("skyr", "Skyr", FoodGroup.DAIRY, 63.0, 11.0, 4.0, 0.2, 150.0, "1 pot"),
        food("cottage-cheese", "Cottage cheese", FoodGroup.DAIRY, 98.0, 11.0, 3.4, 4.3, 150.0, "1 portion"),
        food("milk-semi", "Semi-skimmed milk", FoodGroup.DAIRY, 47.0, 3.4, 4.8, 1.7, 250.0, "1 glass"),
        food("milk-whole", "Whole milk", FoodGroup.DAIRY, 64.0, 3.3, 4.7, 3.6, 250.0, "1 glass"),
        food("cheddar", "Cheddar cheese", FoodGroup.DAIRY, 403.0, 25.0, 1.3, 33.0, 30.0, "1 slice"),
        food("mozzarella", "Mozzarella", FoodGroup.DAIRY, 280.0, 28.0, 3.1, 17.0, 60.0, "half a ball"),

        // ------------------------------------------------------------ fats, nuts
        food("olive-oil", "Olive oil", FoodGroup.FAT, 884.0, 0.0, 0.0, 100.0, 10.0, "1 tbsp"),
        food("butter", "Butter", FoodGroup.FAT, 717.0, 0.9, 0.1, 81.0, 10.0, "1 knob"),
        food("peanut-butter", "Peanut butter", FoodGroup.FAT, 588.0, 25.0, 20.0, 50.0, 30.0, "1 tbsp"),
        food("almonds", "Almonds", FoodGroup.FAT, 579.0, 21.0, 22.0, 50.0, 30.0, "1 handful"),
        food("walnuts", "Walnuts", FoodGroup.FAT, 654.0, 15.0, 14.0, 65.0, 30.0, "1 handful"),
        food("avocado", "Avocado", FoodGroup.FAT, 160.0, 2.0, 8.5, 15.0, 100.0, "half an avocado"),
        food("hummus", "Hummus", FoodGroup.FAT, 166.0, 8.0, 14.0, 10.0, 50.0, "2 tbsp"),

        // --------------------------------------------------------------- snacks
        food("dark-chocolate", "Dark chocolate 70%", FoodGroup.SNACK, 598.0, 7.8, 46.0, 43.0, 25.0, "3 squares"),
        food("protein-bar", "Protein bar", FoodGroup.SNACK, 380.0, 33.0, 38.0, 12.0, 60.0, "1 bar"),
        food("crisps", "Crisps", FoodGroup.SNACK, 536.0, 6.6, 53.0, 35.0, 25.0, "1 bag"),
        food("biscuits", "Digestive biscuits", FoodGroup.SNACK, 478.0, 6.5, 64.0, 21.0, 30.0, "2 biscuits"),
        food("ice-cream", "Vanilla ice cream", FoodGroup.SNACK, 207.0, 3.5, 24.0, 11.0, 100.0, "2 scoops"),
        food("popcorn", "Popcorn, plain", FoodGroup.SNACK, 387.0, 13.0, 78.0, 4.5, 30.0, "1 bowl")
    )
}
