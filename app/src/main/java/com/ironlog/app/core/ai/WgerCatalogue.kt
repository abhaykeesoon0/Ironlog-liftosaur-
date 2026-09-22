package com.ironlog.app.core.ai

import com.ironlog.app.data.entity.ExerciseCategory
import com.ironlog.app.data.entity.MuscleGroup
import com.ironlog.app.data.repository.UserFacingException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The wger online exercise database.
 *
 * wger is an open-source fitness project whose exercise data is public: read
 * access needs no account, no API key and no billing, which is the only reason
 * it is here. An API that required a key would mean either asking every user to
 * register for one or shipping a key inside the APK, and a key inside an APK is
 * a key anyone can read with a zip tool.
 *
 * The data is licensed, not free of obligations, so every entry carries its
 * licence and author through to the screen and into the imported exercise. That
 * is a condition of using it, not a nicety.
 *
 * Reached only when the user opens the online browser, so the app is untouched
 * by it until then.
 */
@Singleton
class WgerCatalogue @Inject constructor() {

    data class Entry(
        val id: Int,
        val name: String,
        val description: String,
        val muscle: MuscleGroup,
        val category: ExerciseCategory,
        val equipment: String,
        val licence: String,
        val author: String
    )

    data class Page(val entries: List<Entry>, val total: Int, val hasMore: Boolean)

    /**
     * One page of exercises.
     *
     * The list is filtered to entries with an English name: the database is
     * multilingual and an exercise with only a German translation would import
     * as a name the user cannot search for.
     */
    suspend fun page(offset: Int = 0, limit: Int = PAGE_SIZE): Page = withContext(Dispatchers.IO) {
        val payload = fetch("$BASE?format=json&limit=$limit&offset=$offset")
        val root = Json.parseToJsonElement(payload).jsonObject
        val total = root["count"]?.jsonPrimitive?.intOrNull ?: 0

        val entries = root["results"]?.jsonArray.orEmpty().mapNotNull { element ->
            runCatching { entryFrom(element.jsonObject) }.getOrNull()
        }

        Page(
            entries = entries,
            total = total,
            hasMore = root["next"]?.jsonPrimitive?.contentOrNull != null
        )
    }

    private fun entryFrom(item: JsonObject): Entry? {
        val english = item["translations"]?.jsonArray.orEmpty()
            .map { it.jsonObject }
            .firstOrNull { it["language"]?.jsonPrimitive?.intOrNull == ENGLISH }
            ?: return null

        val name = english["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (name.isBlank()) return null

        val equipment = item["equipment"]?.jsonArray.orEmpty()
            .mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }

        return Entry(
            id = item["id"]?.jsonPrimitive?.intOrNull ?: return null,
            name = name,
            // The descriptions carry HTML from the website's editor.
            description = stripHtml(english["description"]?.jsonPrimitive?.contentOrNull.orEmpty()),
            muscle = muscleFor(
                item["category"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull,
                item["muscles"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
            ),
            category = categoryFor(equipment),
            equipment = equipment.joinToString(", ").ifBlank { "Bodyweight" },
            licence = item["license"]?.jsonObject?.get("short_name")?.jsonPrimitive?.contentOrNull
                ?: "CC-BY-SA",
            author = item["license_author"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: "wger contributors"
        )
    }

    private fun fetch(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("accept", "application/json")
            // Identifies the app to wger's servers, which is the polite minimum
            // when reading somebody else's open database.
            setRequestProperty("user-agent", USER_AGENT)
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw UserFacingException("wger returned an error ($status).")
            return connection.inputStream.bufferedReader().use(BufferedReader::readText)
        } catch (e: UserFacingException) {
            throw e
        } catch (e: Exception) {
            throw UserFacingException("Could not reach wger. Your own library still works offline.")
        } finally {
            connection.disconnect()
        }
    }

    /**
     * wger's muscle names are anatomical -- "Biceps femoris", "Latissimus
     * dorsi" -- so they are mapped to the groups this app programmes around,
     * falling back to the exercise's category when no muscle is listed.
     */
    private fun muscleFor(category: String?, muscles: List<String>): MuscleGroup {
        muscles.forEach { name ->
            ANATOMY.entries.firstOrNull { name.contains(it.key, ignoreCase = true) }?.let { return it.value }
        }
        return when (category?.lowercase()) {
            "abs" -> MuscleGroup.ABS
            "arms" -> MuscleGroup.BICEPS
            "back" -> MuscleGroup.BACK
            "calves" -> MuscleGroup.CALVES
            "chest" -> MuscleGroup.CHEST
            "legs" -> MuscleGroup.QUADS
            "shoulders" -> MuscleGroup.SHOULDERS
            else -> MuscleGroup.FULL_BODY
        }
    }

    private fun categoryFor(equipment: List<String>): ExerciseCategory = when {
        equipment.isEmpty() -> ExerciseCategory.BODYWEIGHT
        equipment.any { it.contains("Barbell", true) || it.contains("SZ-Bar", true) } -> ExerciseCategory.BARBELL
        equipment.any { it.contains("Dumbbell", true) || it.contains("Kettlebell", true) } -> ExerciseCategory.DUMBBELL
        equipment.any { it.contains("Machine", true) || it.contains("Gym mat", true) } -> ExerciseCategory.MACHINE
        equipment.any { it.contains("Pull-up bar", true) || it.contains("Bench", true) } -> ExerciseCategory.BODYWEIGHT
        else -> ExerciseCategory.MACHINE
    }

    private fun stripHtml(raw: String): String = raw
        .replace(Regex("<[^>]*>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace(Regex("\\s+"), " ")
        .trim()

    private companion object {
        const val BASE = "https://wger.de/api/v2/exerciseinfo/"
        const val PAGE_SIZE = 20
        const val ENGLISH = 2
        const val TIMEOUT_MS = 20_000
        const val USER_AGENT = "IronLog/1.0 (coursework project; offline strength tracker)"

        val ANATOMY = mapOf(
            "Biceps brachii" to MuscleGroup.BICEPS,
            "Triceps brachii" to MuscleGroup.TRICEPS,
            "Pectoralis" to MuscleGroup.CHEST,
            "Latissimus" to MuscleGroup.LATS,
            "Trapezius" to MuscleGroup.TRAPS,
            "Deltoideus" to MuscleGroup.SHOULDERS,
            "Rectus abdominis" to MuscleGroup.ABS,
            "Obliquus" to MuscleGroup.ABS,
            "Quadriceps" to MuscleGroup.QUADS,
            "Biceps femoris" to MuscleGroup.HAMSTRINGS,
            "Gluteus" to MuscleGroup.GLUTES,
            "Gastrocnemius" to MuscleGroup.CALVES,
            "Soleus" to MuscleGroup.CALVES,
            "Brachialis" to MuscleGroup.FOREARMS
        )
    }
}
