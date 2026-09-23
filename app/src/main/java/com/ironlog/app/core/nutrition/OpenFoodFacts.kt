package com.ironlog.app.core.nutrition

import com.ironlog.app.data.repository.UserFacingException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What a barcode resolves to: a product and its figures per hundred grams.
 *
 * Kept as a value rather than written to the library straight away, so the
 * user sees what was found and confirms it. A database of crowd-sourced
 * labels is right far more often than it is wrong, but "far more often" is
 * not "always", and a wrong entry logged silently is worse than a prompt.
 */
data class ScannedFood(
    val barcode: String,
    val name: String,
    val brand: String?,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val servingGrams: Double?
)

/**
 * Open Food Facts, the open product database, reached by barcode.
 *
 * It is here for the same reason wger is: read access needs no account and no
 * key, so nothing has to be shipped inside the APK for it to work, and the
 * data is openly licensed (ODbL), which the app credits on screen. It is
 * reached only when the user scans a code the library does not already know,
 * and a scanned product is stored locally so the same code never needs the
 * network twice. The rest of the app stays offline.
 */
@Singleton
class OpenFoodFacts @Inject constructor() {

    suspend fun lookup(barcode: String): ScannedFood = withContext(Dispatchers.IO) {
        val clean = barcode.filter { it.isDigit() }
        if (clean.length !in 8..14) throw UserFacingException("That does not look like a product barcode")
        val payload = fetch("$BASE/$clean.json?fields=$FIELDS")
        parse(payload) ?: throw UserFacingException("No product found for that barcode. You can add it by hand.")
    }

    private fun fetch(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("accept", "application/json")
            // Open Food Facts asks callers to identify themselves; it is how
            // they tell an app from a scraper.
            setRequestProperty("user-agent", USER_AGENT)
        }
        try {
            val status = connection.responseCode
            if (status == 404) return "{}"
            if (status !in 200..299) throw UserFacingException("Open Food Facts returned an error ($status).")
            return connection.inputStream.bufferedReader().use(BufferedReader::readText)
        } catch (e: UserFacingException) {
            throw e
        } catch (e: Exception) {
            throw UserFacingException("Could not reach Open Food Facts. You can add the food by hand.")
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val BASE = "https://world.openfoodfacts.org/api/v2/product"
        private const val FIELDS = "product_name,brands,nutriments,serving_quantity,code"
        private const val TIMEOUT_MS = 15_000
        private const val USER_AGENT = "IronLog/1.0 (coursework project; offline strength tracker)"

        /**
         * Reads a product out of the API's JSON, or nothing if the entry has
         * no usable label. Kept separate from the fetch so it can be tested
         * against a saved response rather than the live service.
         *
         * Energy is taken in kilocalories where the label gives them and
         * converted from kilojoules where it does not, since a European label
         * often carries only the latter.
         */
        fun parse(payload: String): ScannedFood? {
            val root = runCatching { Json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return null
            if (root["status"]?.jsonPrimitive?.intOrNull == 0) return null
            val product = root["product"]?.jsonObject ?: return null
            val nutriments = product["nutriments"]?.jsonObject ?: return null

            fun number(key: String): Double? = nutriments[key]?.jsonPrimitive?.doubleOrNull
            val kcal = number("energy-kcal_100g") ?: number("energy_100g")?.let { it / KJ_PER_KCAL } ?: return null

            val name = product["product_name"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                ?: return null
            return ScannedFood(
                barcode = product["code"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                name = name,
                brand = product["brands"]?.jsonPrimitive?.contentOrNull?.split(",")?.firstOrNull()?.trim()
                    ?.takeIf { it.isNotEmpty() },
                kcalPer100g = kcal,
                proteinPer100g = number("proteins_100g") ?: 0.0,
                carbsPer100g = number("carbohydrates_100g") ?: 0.0,
                fatPer100g = number("fat_100g") ?: 0.0,
                servingGrams = product["serving_quantity"]?.jsonPrimitive?.doubleOrNull?.takeIf { it > 0.0 }
            )
        }

        private const val KJ_PER_KCAL = 4.184
    }
}
