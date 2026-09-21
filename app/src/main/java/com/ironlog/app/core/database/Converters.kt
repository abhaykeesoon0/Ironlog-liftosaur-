package com.ironlog.app.core.database

import androidx.room.TypeConverter
import com.ironlog.app.data.entity.CardioType
import com.ironlog.app.data.entity.ExerciseCategory
import com.ironlog.app.data.entity.GoalMetric
import com.ironlog.app.data.entity.MuscleGroup
import com.ironlog.app.data.entity.PhotoPose
import com.ironlog.app.data.entity.RecordType
import com.ironlog.app.data.entity.SetType
import com.ironlog.app.data.entity.UnitSystem

/**
 * Enums are stored by name rather than ordinal so that reordering an enum
 * later cannot silently corrupt existing rows.
 */
class Converters {
    @TypeConverter fun muscleToString(v: MuscleGroup?): String? = v?.name
    @TypeConverter fun stringToMuscle(v: String?): MuscleGroup? =
        v?.let { runCatching { MuscleGroup.valueOf(it) }.getOrNull() }

    @TypeConverter fun categoryToString(v: ExerciseCategory?): String? = v?.name
    @TypeConverter fun stringToCategory(v: String?): ExerciseCategory? =
        v?.let { runCatching { ExerciseCategory.valueOf(it) }.getOrNull() }

    @TypeConverter fun goalMetricToString(v: GoalMetric?): String? = v?.name
    @TypeConverter fun stringToGoalMetric(v: String?): GoalMetric? =
        v?.let { runCatching { GoalMetric.valueOf(it) }.getOrNull() }

    @TypeConverter fun setTypeToString(v: SetType?): String? = v?.name
    @TypeConverter fun stringToSetType(v: String?): SetType? =
        v?.let { runCatching { SetType.valueOf(it) }.getOrNull() }

    @TypeConverter fun recordTypeToString(v: RecordType?): String? = v?.name
    @TypeConverter fun stringToRecordType(v: String?): RecordType? =
        v?.let { runCatching { RecordType.valueOf(it) }.getOrNull() }

    @TypeConverter fun unitToString(v: UnitSystem?): String? = v?.name
    @TypeConverter fun stringToUnit(v: String?): UnitSystem? =
        v?.let { runCatching { UnitSystem.valueOf(it) }.getOrNull() }

    @TypeConverter fun cardioTypeToString(v: CardioType?): String? = v?.name
    @TypeConverter fun stringToCardioType(v: String?): CardioType? =
        v?.let { runCatching { CardioType.valueOf(it) }.getOrNull() }

    @TypeConverter fun poseToString(v: PhotoPose?): String? = v?.name
    @TypeConverter fun stringToPose(v: String?): PhotoPose? =
        v?.let { runCatching { PhotoPose.valueOf(it) }.getOrNull() }
}
