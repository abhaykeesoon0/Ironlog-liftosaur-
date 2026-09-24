package com.ironlog.app.domain.usecase

import com.ironlog.app.data.entity.MuscleGroup

/**
 * The offline coach: answers training questions from a built-in reference and
 * from the user's own log.
 *
 * Not a language model, and it does not pretend to be. It matches a question
 * against a set of topics, then answers with text written in advance, filled in
 * with figures taken from the training history. The trade-off is deliberate: it
 * works with no network, no API key and no data leaving the phone, it cannot
 * invent a number, and every answer can be traced to a rule and a row. What it
 * gives up is the ability to answer a question nobody anticipated -- which is
 * what the optional cloud assistant is for.
 *
 * Health questions have a floor under them: anything that sounds like pain or
 * illness is answered by saying to see a professional, not by guessing.
 */
object Coach {

    /** What the coach knows about the user, assembled from their log. */
    data class Context(
        val displayName: String = "",
        val workoutsThisWeek: Int = 0,
        val weeklyGoal: Int = 4,
        val streakWeeks: Int = 0,
        val recordsThisWeek: Int = 0,
        val volumeThisWeekKg: Double = 0.0,
        val recovering: List<Recovering> = emptyList(),
        val topLifts: List<Lift> = emptyList(),
        val bodyweightKg: Double? = null,
        val bodyweightChangeKg: Double? = null
    )

    data class Recovering(val muscle: MuscleGroup, val percent: Int, val hoursLeft: Int)
    data class Lift(val name: String, val bestKg: Double, val lastReps: Int)

    data class Answer(
        val text: String,
        /** Where the answer came from, shown under it so nothing looks oracular. */
        val source: String,
        /** True when the question looked medical and was deflected rather than answered. */
        val isReferral: Boolean = false,
        /** True when nothing matched and this is the "I only know so much" reply. */
        val isFallback: Boolean = false
    )

    /** Openers offered as chips, so the first question is never a blank page. */
    val SUGGESTIONS = listOf(
        "What should I train today?",
        "How do I get stronger?",
        "How much protein do I need?",
        "How many rest days?",
        "Why have I stopped progressing?",
        "How long should I rest between sets?"
    )

    fun answer(question: String, context: Context): Answer {
        val q = question.lowercase()

        // Safety first, before anything else can match: a question about pain is
        // not a training question, and a guess at one could hurt somebody.
        if (MEDICAL.any { q.contains(it) }) {
            return Answer(
                text = "That sounds like one for a doctor or a physiotherapist rather than an app. " +
                    "I can track what you lift, but I cannot examine you, and guessing about pain or " +
                    "illness is how people make an injury worse.\n\n" +
                    "If it is sharp, swelling, or it hurts at rest, stop training that area and get it " +
                    "looked at. General soreness for a day or two after a hard session is ordinary.",
                source = "Safety rule",
                isReferral = true
            )
        }

        // Each topic is scored by how many of its phrases the question contains,
        // matched on whole words: "creatine" must not become a diet answer
        // because it happens to contain "eat". Nothing matching is an honest
        // "I don't know", which is what lets the web lookup take over.
        val scored = TOPICS.map { topic -> topic to score(q, topic.needles) }
        val best = scored.filter { it.second > 0 }.maxByOrNull { it.second }
        return best?.first?.answer?.invoke(context) ?: fallback(context)
    }

    private class Topic(val needles: List<String>, val answer: (Context) -> Answer)

    private fun topic(vararg needles: String, answer: (Context) -> Answer) = Topic(needles.toList(), answer)

    private val TOPICS = listOf(
        topic("today", "what should i train", "what do i train", "next session") { trainToday(it) },
        topic("recover", "recovery", "sore", "rest day", "rest days", "how often", "frequency", "how many days") { recovery(it) },
        topic("stronger", "strength", "progress", "add weight", "progression", "plateau", "stuck", "stopped") { progression(it) },
        topic("protein", "diet", "eat", "eating", "nutrition", "calorie", "calories", "bulk", "cut") { nutrition(it) },
        topic("rest between", "rest time", "how long between", "rest period", "between sets") { restTimes() },
        topic("reps", "sets", "how many reps", "rep range", "volume") { setsAndReps() },
        topic("warm up", "warmup", "warm-up") { warmUp() },
        topic("sleep", "tired", "fatigue", "overtraining") { sleep() },
        topic("cardio", "running", "steps", "conditioning") { cardio() },
        topic("how am i", "doing", "progressing", "summary", "my week") { weekSummary(it) },
        topic("weight", "bodyweight", "heavier", "lighter", "lose", "gain") { bodyweight(it) }
    )

    /** Whole-word hits, a multi-word phrase counting double because it is the more specific claim. */
    private fun score(question: String, needles: List<String>): Int = needles.sumOf { needle ->
        val pattern = Regex("(?<![a-z])" + Regex.escape(needle) + "(?![a-z])")
        if (pattern.containsMatchIn(question)) (if (' ' in needle) 2 else 1) else 0
    }

    private val MEDICAL = listOf(
        "pain", "hurts", "hurting", "injur", "sprain", "strain my", "tear", "torn",
        "dizzy", "faint", "chest pain", "numb", "tingling", "swollen", "swelling",
        "doctor", "medication", "diabet", "blood pressure", "pregnan"
    )

    // ------------------------------------------------------------- the answers

    private fun trainToday(c: Context): Answer {
        val sore = c.recovering.filter { it.percent < 60 }
        return if (sore.isEmpty()) {
            Answer(
                "Everything you have trained recently has recovered, so today is open. " +
                    "You are ${c.workoutsThisWeek} of ${c.weeklyGoal} sessions into the week.\n\n" +
                    "If you have no plan, train the thing you trained longest ago — that is usually " +
                    "the one holding you back.",
                "Your recovery readout and this week's sessions"
            )
        } else {
            val names = sore.joinToString(", ") { "${it.muscle.label} (${it.percent}%, ${it.hoursLeft}h left)" }
            val fresh = MuscleGroup.entries
                .filter { it != MuscleGroup.CARDIO && it != MuscleGroup.FULL_BODY }
                .filterNot { muscle -> sore.any { it.muscle == muscle } }
                .take(3)
                .joinToString(", ") { it.label }
            Answer(
                "Still recovering: $names.\n\nTrain something else today — $fresh are all ready. " +
                    "Training a muscle again before it has recovered mostly adds fatigue rather than progress.",
                "Your recovery readout"
            )
        }
    }

    private fun recovery(c: Context): Answer = Answer(
        "A muscle needs somewhere between 36 and 72 hours before it is worth training hard again, " +
            "and the heavier the session the longer that takes. That is the window this app uses on " +
            "the recovery figures on Home.\n\n" +
            "Most people do well on three to five sessions a week with at least one full day off. " +
            "You are at ${c.workoutsThisWeek} this week against a goal of ${c.weeklyGoal}" +
            if (c.streakWeeks > 0) ", on a ${c.streakWeeks}-week streak." else ".",
        "Recovery model and your training log"
    )

    private fun progression(c: Context): Answer {
        val lift = c.topLifts.firstOrNull()
        val specific = if (lift != null) {
            "\n\nYour best ${lift.name} is ${format(lift.bestKg)} kg. When you hit the top of your rep " +
                "range on every set, add the smallest jump your plates allow — usually 2.5 kg — and work " +
                "back up. That is double progression, and it is what this app's suggested loads follow."
        } else {
            "\n\nLog a few sessions and I can tell you which lifts are moving and which are not."
        }
        return Answer(
            "Strength comes from adding a little at a time and not missing sessions. Three things do " +
                "most of the work: train each lift twice a week, keep most sets a rep or two short of " +
                "failure, and only add weight once you have earned it." + specific +
                "\n\nIf a lift has genuinely stalled for three weeks, take 10% off and build back up. " +
                "That usually beats grinding the same weight.",
            "Progression rules and your records"
        )
    }

    private fun nutrition(c: Context): Answer {
        val weight = c.bodyweightKg
        val protein = weight?.let { "About ${(it * 1.6).toInt()}–${(it * 2.2).toInt()} g a day at your bodyweight." }
            ?: "Log your bodyweight and I can put a number on it."
        return Answer(
            "For someone lifting, 1.6–2.2 g of protein per kilogram of bodyweight per day is the range " +
                "the research keeps landing on. $protein\n\n" +
                "Beyond that: eat roughly at maintenance to gain strength slowly, a little above it to " +
                "add size, a little below to lose fat. Large deficits cost strength.\n\n" +
                "This is general information, not dietary advice for you specifically — a dietitian can " +
                "give you that.",
            "General nutrition guidance"
        )
    }

    private fun restTimes() = Answer(
        "Rest by what the set was for:\n\n" +
            "• Heavy strength work, 3–5 reps: 3 to 5 minutes. You are resting the nervous system, not " +
            "just catching your breath.\n" +
            "• Muscle-building work, 6–12 reps: 90 seconds to 2 minutes.\n" +
            "• Light or isolation work, 12+ reps: 45 to 90 seconds.\n\n" +
            "The app's rest timer defaults to two minutes and can be set per exercise.",
        "Rest guidance"
    )

    private fun setsAndReps() = Answer(
        "Ten to twenty hard sets per muscle per week is where most of the evidence sits, split across " +
            "two sessions.\n\n" +
            "• Strength: 3–5 reps, heavy, long rests.\n" +
            "• Size: 6–12 reps, taken close to failure.\n" +
            "• Endurance and joints: 12–20 reps, lighter.\n\n" +
            "Warm-up sets do not count towards any of that, which is why this app excludes them from " +
            "your volume.",
        "Programming guidance"
    )

    private fun warmUp() = Answer(
        "Five minutes of something that raises your heart rate, then ramp the bar up to your working " +
            "weight in two or three sets — say 40%, 60%, 80% — for a few reps each.\n\n" +
            "Mark those sets as warm-ups in the app and they stay out of your volume and out of your " +
            "records.",
        "Warm-up guidance"
    )

    private fun sleep() = Answer(
        "Sleep is the training variable people ignore. Seven to nine hours, and strength, appetite and " +
            "mood all hold up; under six and they all slide together.\n\n" +
            "If you are tired, weaker than usual and unmotivated for more than a week, that is usually " +
            "under-recovery rather than a bad programme. Take a lighter week — half the sets, same " +
            "weights — and see what comes back.",
        "Recovery guidance"
    )

    private fun cardio() = Answer(
        "Cardio and lifting sit together fine as long as the hard cardio is not immediately before your " +
            "legs session. Two or three easy sessions a week, or a daily walk, is plenty for health.\n\n" +
            "The app records outdoor sessions with GPS, and counts steps from the phone or from your " +
            "watch through Health Connect.",
        "General guidance"
    )

    private fun weekSummary(c: Context): Answer = Answer(
        buildString {
            append("This week: ${c.workoutsThisWeek} of ${c.weeklyGoal} sessions")
            if (c.volumeThisWeekKg > 0) append(", ${TrainingMath.formatVolume(c.volumeThisWeekKg)} of volume")
            if (c.recordsThisWeek > 0) {
                append(", and ${c.recordsThisWeek} personal record")
                if (c.recordsThisWeek != 1) append("s")
            }
            append(".")
            if (c.streakWeeks == 1) append(" You have trained every week for a week.")
            else if (c.streakWeeks > 1) append(" You have trained every week for ${c.streakWeeks} weeks.")
            append("\n\n")
            append(
                if (c.workoutsThisWeek >= c.weeklyGoal) "You have hit your goal. Consistency is the part most people miss."
                else "That leaves ${c.weeklyGoal - c.workoutsThisWeek} to go. A short session counts for more than a skipped one."
            )
        },
        "Your training log"
    )

    private fun bodyweight(c: Context): Answer {
        val now = c.bodyweightKg
        val change = c.bodyweightChangeKg
        val body = when {
            now == null -> "Log your bodyweight under Body measurements and I can track the trend for you."
            change == null -> "You are ${format(now)} kg. Log it again in a week or two and I can show a trend."
            change > 0.2 -> "You are ${format(now)} kg, up ${format(change)} kg. Gaining 0.25–0.5% of bodyweight a week is about the rate where most of it is muscle."
            change < -0.2 -> "You are ${format(now)} kg, down ${format(-change)} kg. Losing 0.5–1% a week keeps most of your strength; faster than that and the bar usually starts moving down too."
            else -> "You are ${format(now)} kg and steady."
        }
        return Answer(body, "Your body measurements")
    }

    private fun fallback(c: Context): Answer = Answer(
        "I can answer questions about training, recovery, programming and your own numbers — what to " +
            "train today, how to add weight, how much to rest, how you are progressing.\n\n" +
            "I am a built-in coach rather than a language model, so I only know what I was written " +
            "with and what is in your log. For anything broader, turn on web lookups below, or the " +
            "cloud assistant in Settings.",
        "Coach",
        isFallback = true
    )

    private fun format(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
}
