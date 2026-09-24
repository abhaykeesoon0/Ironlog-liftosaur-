package com.ironlog.app.data.entity

/** How a movement is loaded. Drives which input fields a set row shows. */
enum class ExerciseCategory(val label: String) {
    BARBELL("Barbell"),
    DUMBBELL("Dumbbell"),
    MACHINE("Machine"),
    CABLE("Cable"),
    BODYWEIGHT("Bodyweight"),
    WEIGHTED_BODYWEIGHT("Weighted bodyweight"),
    ASSISTED_BODYWEIGHT("Assisted bodyweight"),
    DURATION("Duration"),
    CARDIO("Cardio");

    /** Duration and cardio movements log time/distance instead of weight x reps. */
    val tracksWeight: Boolean
        get() = this != DURATION && this != CARDIO && this != BODYWEIGHT

    val tracksReps: Boolean
        get() = this != DURATION && this != CARDIO

    val tracksDistance: Boolean
        get() = this == CARDIO
}

enum class MuscleGroup(val label: String) {
    CHEST("Chest"),
    BACK("Back"),
    SHOULDERS("Shoulders"),
    BICEPS("Biceps"),
    TRICEPS("Triceps"),
    FOREARMS("Forearms"),
    QUADS("Quads"),
    HAMSTRINGS("Hamstrings"),
    GLUTES("Glutes"),
    CALVES("Calves"),
    ABS("Abs"),
    TRAPS("Traps"),
    LATS("Lats"),
    FULL_BODY("Full body"),
    CARDIO("Cardio")
}

/**
 * What a strength goal is measured on.
 *
 * Two metrics rather than one because they answer different questions: an
 * estimate counts progress made at any rep range, while a top weight is the
 * number a lifter can claim to have actually put on the bar.
 */
/**
 * Which plates the gym actually has.
 *
 * Separate from the display unit on purpose: plenty of lifters read in pounds
 * while loading a kilo bar, or the other way round, and guessing the equipment
 * from the reading preference produces a plate list that does not exist in the
 * room they are standing in.
 */
/**
 * Where today's step count comes from.
 *
 * A watch cannot be read over Bluetooth directly -- those protocols are the
 * vendor's -- but every watch app writes into Health Connect, so that is where
 * a watch's steps are collected from.
 */
enum class StepSource(val label: String, val detail: String) {
    PHONE("Phone", "The pedometer built into this handset."),
    HEALTH_CONNECT("Health Connect", "Steps from your watch or any app that writes them.")
}

enum class PlateSet(val label: String) {
    KILOGRAMS("Kilograms"),
    POUNDS("Pounds")
}

enum class GoalMetric(val label: String, val shortLabel: String) {
    ESTIMATED_1RM("Estimated 1RM", "1RM"),
    TOP_WEIGHT("Top weight", "Weight")
}

enum class SetType(val label: String, val shortLabel: String) {
    WARMUP("Warm-up", "W"),
    NORMAL("Working", ""),
    DROP("Drop set", "D"),
    FAILURE("To failure", "F");

    /** Warm-ups are excluded from volume totals and PR detection. */
    val countsTowardVolume: Boolean get() = this != WARMUP
}

enum class RecordType(val label: String) {
    HEAVIEST_WEIGHT("Heaviest weight"),
    BEST_ESTIMATED_1RM("Best estimated 1RM"),
    BEST_SET_VOLUME("Best set volume"),
    MOST_REPS("Most reps"),
    BEST_SESSION_VOLUME("Best session volume")
}

enum class UnitSystem(val label: String) {
    METRIC("Metric (kg, km)"),
    IMPERIAL("Imperial (lb, mi)");

    val weightSuffix: String get() = if (this == METRIC) "kg" else "lb"
    val distanceSuffix: String get() = if (this == METRIC) "km" else "mi"
    val lengthSuffix: String get() = if (this == METRIC) "cm" else "in"
}

enum class CardioType(val label: String) {
    RUN("Run"),
    WALK("Walk"),
    CYCLE("Cycle"),
    HIKE("Hike")
}

enum class PhotoPose(val label: String) {
    FRONT("Front"),
    SIDE("Side"),
    BACK("Back"),
    OTHER("Other")
}

/**
 * The colour the figure uses for a muscle that has been worked.
 *
 * A preference rather than a fixed colour because the figure is the one place
 * in the app a lifter looks at during a session, and what it is filled with is
 * theirs to choose. Stored as the colour itself so the data layer does not
 * need to know anything about Compose.
 */
/**
 * What the user is training for, which shapes every prediction: the rep
 * count a forecast is written for, and how many sets it suggests.
 */
enum class TrainingFocus(val label: String, val reps: Int, val sets: Int, val blurb: String) {
    STRENGTH("Strength", 5, 5, "Heavy fives: predictions lean towards the bar"),
    MUSCLE("Muscle", 8, 4, "Eights to twelves: predictions lean towards volume"),
    ENDURANCE("Endurance", 15, 3, "Fifteens: lighter, longer sets"),
    GENERAL("General", 8, 3, "A middle ground")
}

enum class MuscleTint(val label: String, val argb: Long) {
    PLUM("Plum", 0xFFA78BFA),
    CHALK("Chalk", 0xFFF2F5F7),
    EMBER("Ember", 0xFFE03131),
    OCEAN("Ocean", 0xFF1C7ED6),
    MOSS("Moss", 0xFF2F9E44),
    GOLD("Gold", 0xFFFFC94D)
}
