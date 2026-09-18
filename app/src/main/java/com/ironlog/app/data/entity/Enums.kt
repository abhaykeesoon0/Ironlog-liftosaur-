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
