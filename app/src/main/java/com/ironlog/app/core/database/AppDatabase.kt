package com.ironlog.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ironlog.app.data.dao.BodyDao
import com.ironlog.app.data.dao.CardioDao
import com.ironlog.app.data.dao.ExerciseDao
import com.ironlog.app.data.dao.PersonalRecordDao
import com.ironlog.app.data.dao.RewardsDao
import com.ironlog.app.data.dao.RoutineDao
import com.ironlog.app.data.dao.StrengthGoalDao
import com.ironlog.app.data.dao.UserDao
import com.ironlog.app.data.dao.WorkoutDao
import com.ironlog.app.data.entity.BodyMeasurementEntity
import com.ironlog.app.data.entity.CardioSessionEntity
import com.ironlog.app.data.entity.DailyActivityEntity
import com.ironlog.app.data.entity.ExerciseEntity
import com.ironlog.app.data.entity.PersonalRecordEntity
import com.ironlog.app.data.entity.ProgressPhotoEntity
import com.ironlog.app.data.entity.QuestClaimEntity
import com.ironlog.app.data.entity.RoutineEntity
import com.ironlog.app.data.entity.RoutineExerciseEntity
import com.ironlog.app.data.entity.StorePurchaseEntity
import com.ironlog.app.data.entity.StrengthGoalEntity
import com.ironlog.app.data.entity.UserEntity
import com.ironlog.app.data.entity.WorkoutEntity
import com.ironlog.app.data.entity.WorkoutExerciseEntity
import com.ironlog.app.data.entity.WorkoutSetEntity
import com.ironlog.app.data.dao.NutritionDao
import com.ironlog.app.data.entity.FoodEntity
import com.ironlog.app.data.entity.FoodLogEntity
import com.ironlog.app.data.entity.NutritionGoalEntity

/**
 * The database file lives in internal app storage, which the Android sandbox
 * keeps private to this application's UID. Cloud backup and device transfer are
 * both disabled in the manifest so training and body-composition data -- which
 * is health data -- never leaves the handset.
 */
@Database(
    entities = [
        UserEntity::class,
        ExerciseEntity::class,
        RoutineEntity::class,
        RoutineExerciseEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        WorkoutSetEntity::class,
        PersonalRecordEntity::class,
        BodyMeasurementEntity::class,
        ProgressPhotoEntity::class,
        CardioSessionEntity::class,
        DailyActivityEntity::class,
        StrengthGoalEntity::class,
        QuestClaimEntity::class,
        StorePurchaseEntity::class,
        FoodEntity::class,
        FoodLogEntity::class,
        NutritionGoalEntity::class
    ],
    version = 5,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun routineDao(): RoutineDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun personalRecordDao(): PersonalRecordDao
    abstract fun bodyDao(): BodyDao
    abstract fun cardioDao(): CardioDao
    abstract fun strengthGoalDao(): StrengthGoalDao
    abstract fun rewardsDao(): RewardsDao
    abstract fun nutritionDao(): NutritionDao

    companion object {
        const val NAME = "ironlog.db"
    }
}
