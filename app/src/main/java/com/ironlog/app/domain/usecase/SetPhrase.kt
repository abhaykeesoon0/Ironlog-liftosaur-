package com.ironlog.app.domain.usecase

import com.ironlog.app.data.entity.UnitSystem

/**
 * A set as somebody would say it out loud: "sixty kilos for eight", "one
 * hundred and ten pounds, ten reps", "bodyweight twelve".
 *
 * The recogniser hands back words; this turns them into a weight and a rep
 * count. Numbers may arrive as digits or as words, so both are read. A unit
 * said aloud wins; otherwise the user's own unit is assumed, which is what
 * they would mean. Pure, so every phrasing can be tested without a microphone.
 */
object SetPhrase {

    data class Parsed(val weightKg: Double?, val reps: Int?) {
        val isEmpty: Boolean get() = weightKg == null && reps == null
    }

    private val UNITS = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen")
    private val TENS = mapOf("twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
        "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90)
    private val KILOS = setOf("kg", "kilo", "kilos", "kilogram", "kilograms", "kgs")
    private val POUNDS = setOf("lb", "lbs", "pound", "pounds")
    private val REPS = setOf("rep", "reps", "repetition", "repetitions", "times")
    private val BODYWEIGHT = setOf("bodyweight", "body")

    fun parse(text: String, unit: UnitSystem): Parsed {
        val words = text.lowercase()
            .replace("-", " ")
            .replace(Regex("[^a-z0-9. ]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        // Walk the words, folding number-words into numbers as they come, and
        // note what each number was followed by.
        data class Num(val value: Double, var tag: String? = null)
        val numbers = ArrayList<Num>()
        var building: Double? = null
        var bodyweight = false
        // After "point", number words are decimal digits: "sixty two point five".
        var decimal = 0

        fun close() {
            building?.let { numbers += Num(it) }
            building = null
            decimal = 0
        }

        fun addWord(value: Int) {
            building = if (decimal > 0) {
                val scale = Math.pow(10.0, decimal.toDouble())
                decimal++
                (building ?: 0.0) + value / scale
            } else {
                (building ?: 0.0) + value
            }
        }

        for (word in words) {
            val digit = word.toDoubleOrNull()
            when {
                digit != null -> { close(); building = digit }
                word in UNITS -> addWord(UNITS.indexOf(word))
                word in TENS -> addWord(TENS.getValue(word))
                word == "hundred" -> building = (if (building == null || building == 0.0) 1.0 else building!!) * 100
                // Filler inside a spoken number: "one hundred and ten", "sixty two and a half".
                word == "and" || word == "a" -> Unit
                word == "point" -> decimal = 1
                word == "half" -> building = (building ?: 0.0) + 0.5
                word in KILOS -> { close(); numbers.lastOrNull()?.tag = "kg" }
                word in POUNDS -> { close(); numbers.lastOrNull()?.tag = "lb" }
                word in REPS -> { close(); numbers.lastOrNull()?.tag = "reps" }
                word in BODYWEIGHT -> { close(); bodyweight = true }
                else -> close()
            }
        }
        close()

        var weight: Double? = null
        var weightUnit: String? = null
        var reps: Int? = null

        numbers.firstOrNull { it.tag == "kg" || it.tag == "lb" }?.let { weight = it.value; weightUnit = it.tag }
        numbers.firstOrNull { it.tag == "reps" }?.let { reps = it.value.toInt() }

        // No units said: the first number is the weight and the second the reps,
        // unless the lifter said bodyweight, in which case the one number is reps.
        val untagged = numbers.filter { it.tag == null }
        if (weight == null && !bodyweight && untagged.isNotEmpty() && (reps == null || untagged.size >= 1)) {
            if (reps == null && untagged.size >= 2) { weight = untagged[0].value; reps = untagged[1].value.toInt() }
            else if (reps != null) weight = untagged[0].value
            else if (untagged.size == 1) weight = untagged[0].value
        }
        if (reps == null && untagged.isNotEmpty() && (bodyweight || weight != null)) {
            val candidate = untagged.lastOrNull { it.value != weight }
            if (candidate != null && candidate.value <= 100) reps = candidate.value.toInt()
        }

        val kg = weight?.let { w ->
            when (weightUnit ?: if (unit == UnitSystem.IMPERIAL) "lb" else "kg") {
                "lb" -> TrainingMath.lbToKg(w)
                else -> w
            }
        }
        return Parsed(
            weightKg = kg?.takeIf { it in 0.5..1_000.0 },
            reps = reps?.takeIf { it in 1..200 }
        )
    }
}
