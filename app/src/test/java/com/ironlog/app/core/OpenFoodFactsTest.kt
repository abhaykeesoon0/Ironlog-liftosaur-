package com.ironlog.app.core

import com.ironlog.app.core.nutrition.OpenFoodFacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OpenFoodFactsTest {

    /** A trimmed copy of the live response for 3017624010701, taken 24 Sep 2026. */
    private val nutella = """
        {"code":"3017624010701","product":{"brands":"Ferrero","code":"3017624010701",
         "nutriments":{"carbohydrates_100g":57.5,"energy-kcal_100g":539,"energy_100g":2227.9,
                       "fat_100g":30.9,"proteins_100g":6.3},
         "product_name":"Nutella"},"status":1,"status_verbose":"product found"}
    """.trimIndent()

    @Test
    fun `a product is read with its figures per hundred grams`() {
        val food = OpenFoodFacts.parse(nutella)
        assertNotNull(food)
        assertEquals("Nutella", food!!.name)
        assertEquals("Ferrero", food.brand)
        assertEquals(539.0, food.kcalPer100g, 1e-6)
        assertEquals(6.3, food.proteinPer100g, 1e-6)
        assertEquals(57.5, food.carbsPer100g, 1e-6)
        assertEquals(30.9, food.fatPer100g, 1e-6)
        assertEquals("3017624010701", food.barcode)
        assertNull(food.servingGrams)
    }

    @Test
    fun `kilojoules are converted when a label carries no kilocalories`() {
        val kjOnly = """{"status":1,"product":{"product_name":"Oat bar","nutriments":{"energy_100g":1674}}}"""
        val food = OpenFoodFacts.parse(kjOnly)
        assertEquals(400.0, food!!.kcalPer100g, 0.5)
    }

    @Test
    fun `an unknown code, a nameless product and rubbish all read as nothing`() {
        assertNull(OpenFoodFacts.parse("""{"status":0,"status_verbose":"product not found"}"""))
        assertNull(OpenFoodFacts.parse("""{"status":1,"product":{"nutriments":{"energy-kcal_100g":100}}}"""))
        assertNull(OpenFoodFacts.parse("not json at all"))
        assertNull(OpenFoodFacts.parse("{}"))
    }

    @Test
    fun `only the first brand is kept and blanks are dropped`() {
        val multi = """{"status":1,"product":{"product_name":"Beans","brands":"Heinz, Kraft Heinz",
            "nutriments":{"energy-kcal_100g":80}}}"""
        assertEquals("Heinz", OpenFoodFacts.parse(multi)!!.brand)
        val blank = """{"status":1,"product":{"product_name":"Beans","brands":"  ","nutriments":{"energy-kcal_100g":80}}}"""
        assertNull(OpenFoodFacts.parse(blank)!!.brand)
    }
}
