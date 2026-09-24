package com.ironlog.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ironlog.app.core.database.AppDatabase
import com.ironlog.app.core.security.KeystoreSigningSecretProvider
import com.ironlog.app.core.security.SigningSecretProvider
import com.ironlog.app.data.dao.BodyDao
import com.ironlog.app.data.dao.CardioDao
import com.ironlog.app.data.dao.ExerciseDao
import com.ironlog.app.data.dao.PersonalRecordDao
import com.ironlog.app.data.dao.RewardsDao
import com.ironlog.app.data.dao.RoutineDao
import com.ironlog.app.data.dao.StrengthGoalDao
import com.ironlog.app.data.dao.UserDao
import com.ironlog.app.data.dao.WorkoutDao
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.ironlog.app.data.repository.AuthRepository
import com.ironlog.app.data.repository.AuthRepositoryImpl
import com.ironlog.app.data.repository.BodyRepository
import com.ironlog.app.data.repository.BodyRepositoryImpl
import com.ironlog.app.data.repository.RewardsRepository
import com.ironlog.app.data.repository.RewardsRepositoryImpl
import com.ironlog.app.data.repository.StrengthGoalRepository
import com.ironlog.app.data.repository.StrengthGoalRepositoryImpl
import com.ironlog.app.data.repository.CardioRepository
import com.ironlog.app.data.repository.CardioRepositoryImpl
import com.ironlog.app.data.repository.ExerciseRepository
import com.ironlog.app.data.repository.ExerciseRepositoryImpl
import com.ironlog.app.data.repository.ExportRepository
import com.ironlog.app.data.repository.ExportRepositoryImpl
import com.ironlog.app.data.repository.ProfileRepository
import com.ironlog.app.data.repository.ProfileRepositoryImpl
import com.ironlog.app.data.repository.RoutineRepository
import com.ironlog.app.data.repository.RoutineRepositoryImpl
import com.ironlog.app.data.repository.WorkoutRepository
import com.ironlog.app.data.repository.WorkoutRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton
import com.ironlog.app.data.dao.NutritionDao
import com.ironlog.app.data.repository.NutritionRepository
import com.ironlog.app.data.repository.NutritionRepositoryImpl
import com.ironlog.app.data.repository.BackupRepository
import com.ironlog.app.data.repository.BackupRepositoryImpl
import com.ironlog.app.core.security.DatabaseEncryption
import com.ironlog.app.core.security.SecureTokenStore
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context, tokenStore: SecureTokenStore): AppDatabase {
        // Encrypted at rest. The library loads once; an install from before
        // encryption is converted in place before Room opens the file.
        System.loadLibrary("sqlcipher")
        val passphrase = DatabaseEncryption.passphrase(tokenStore)
        DatabaseEncryption.ensureEncrypted(context, AppDatabase.NAME, passphrase)
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            // No destructive fallback: silently wiping a user's training history
            // on a schema change would be a data-loss bug, not a convenience.
            // Migrations are added explicitly when the schema moves.
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()
    }

    /**
     * Adds the strength_goals table. Purely additive, so no existing row is
     * touched and an upgrade cannot lose training history.
     *
     * The column types and index names have to match what Room generates for
     * [com.ironlog.app.data.entity.StrengthGoalEntity] exactly, or Room's
     * identity check fails at open time on an upgraded install.
     */
    /**
     * Adds the food library, the food log and the nutrition goal. Additive,
     * like the two before it: nothing existing is read, rewritten or dropped.
     * The library itself is seeded by the application on launch, the same way
     * the exercise library is, so this only makes room for it.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `foods` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `group` TEXT NOT NULL,
                    `kcalPer100g` REAL NOT NULL,
                    `proteinPer100g` REAL NOT NULL,
                    `carbsPer100g` REAL NOT NULL,
                    `fatPer100g` REAL NOT NULL,
                    `servingGrams` REAL NOT NULL,
                    `servingLabel` TEXT NOT NULL,
                    `ownerUserId` TEXT,
                    `isCustom` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `food_logs` (
                    `id` TEXT NOT NULL,
                    `userId` TEXT NOT NULL,
                    `foodId` TEXT NOT NULL,
                    `grams` REAL NOT NULL,
                    `meal` TEXT NOT NULL,
                    `loggedAt` INTEGER NOT NULL,
                    `note` TEXT NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`userId`) REFERENCES `users`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_logs_userId_loggedAt` ON `food_logs` (`userId`, `loggedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_logs_foodId` ON `food_logs` (`foodId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `nutrition_goals` (
                    `userId` TEXT NOT NULL,
                    `goal` TEXT NOT NULL,
                    `sex` TEXT NOT NULL,
                    `ageYears` INTEGER NOT NULL,
                    `activity` TEXT NOT NULL,
                    `targetKcal` INTEGER NOT NULL,
                    `proteinG` INTEGER NOT NULL,
                    `carbsG` INTEGER NOT NULL,
                    `fatG` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`userId`),
                    FOREIGN KEY(`userId`) REFERENCES `users`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
        }
    }

    /** Adds the barcode a scanned food came from. One nullable column, nothing else. */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `foods` ADD COLUMN `barcode` TEXT")
        }
    }

    /** Routine targets for the progression engine: the weight to prescribe and the step to move it by. */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `routine_exercises` ADD COLUMN `targetWeightKg` REAL")
            db.execSQL("ALTER TABLE `routine_exercises` ADD COLUMN `progressionKg` REAL NOT NULL DEFAULT 0.0")
        }
    }

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `strength_goals` (
                    `id` TEXT NOT NULL,
                    `userId` TEXT NOT NULL,
                    `exerciseId` TEXT NOT NULL,
                    `metric` TEXT NOT NULL,
                    `targetKg` REAL NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `achievedAt` INTEGER,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`userId`) REFERENCES `users`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS
                `index_strength_goals_userId_exerciseId_metric`
                ON `strength_goals` (`userId`, `exerciseId`, `metric`)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_strength_goals_exerciseId`
                ON `strength_goals` (`exerciseId`)
                """.trimIndent()
            )
        }
    }

    /**
     * Adds the quest and store tables. Additive again: nothing existing is
     * read, rewritten or dropped, so an upgrade cannot cost anyone their log.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `quest_claims` (
                    `id` TEXT NOT NULL,
                    `userId` TEXT NOT NULL,
                    `questId` TEXT NOT NULL,
                    `periodKey` TEXT NOT NULL,
                    `coins` INTEGER NOT NULL,
                    `claimedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`userId`) REFERENCES `users`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS
                `index_quest_claims_userId_questId_periodKey`
                ON `quest_claims` (`userId`, `questId`, `periodKey`)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `store_purchases` (
                    `id` TEXT NOT NULL,
                    `userId` TEXT NOT NULL,
                    `itemId` TEXT NOT NULL,
                    `coinsSpent` INTEGER NOT NULL,
                    `purchasedAt` INTEGER NOT NULL,
                    `isEquipped` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`userId`) REFERENCES `users`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS `index_store_purchases_userId_itemId`
                ON `store_purchases` (`userId`, `itemId`)
                """.trimIndent()
            )
        }
    }

    @Provides fun provideUserDao(db: AppDatabase): UserDao = db.userDao()
    @Provides fun provideExerciseDao(db: AppDatabase): ExerciseDao = db.exerciseDao()
    @Provides fun provideRoutineDao(db: AppDatabase): RoutineDao = db.routineDao()
    @Provides fun provideWorkoutDao(db: AppDatabase): WorkoutDao = db.workoutDao()
    @Provides fun providePersonalRecordDao(db: AppDatabase): PersonalRecordDao = db.personalRecordDao()
    @Provides fun provideBodyDao(db: AppDatabase): BodyDao = db.bodyDao()
    @Provides fun provideCardioDao(db: AppDatabase): CardioDao = db.cardioDao()
    @Provides fun provideStrengthGoalDao(db: AppDatabase): StrengthGoalDao = db.strengthGoalDao()
    @Provides fun provideRewardsDao(db: AppDatabase): RewardsDao = db.rewardsDao()
    @Provides fun provideNutritionDao(db: AppDatabase): NutritionDao = db.nutritionDao()

    /**
     * Non-sensitive preferences only; session secrets live in SecureTokenStore.
     *
     * Read through the process-wide delegate rather than created here, because
     * DataStore allows exactly one instance per file per process: a second Hilt
     * graph in the same process -- which is what every instrumented test makes
     * -- would otherwise open the file twice and crash.
     */
    @Provides
    @Singleton
    fun providePreferences(@ApplicationContext context: Context): DataStore<Preferences> = context.preferences
}

private val Context.preferences: DataStore<Preferences> by preferencesDataStore(name = "ironlog_prefs")

@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    /**
     * A scope that outlives any one screen, for work that must finish even if the
     * user navigates away -- seeding the exercise library, for instance.
     * SupervisorJob so one failure does not cancel unrelated work.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds @Singleton
    abstract fun bindExerciseRepository(impl: ExerciseRepositoryImpl): ExerciseRepository

    @Binds @Singleton
    abstract fun bindRoutineRepository(impl: RoutineRepositoryImpl): RoutineRepository

    @Binds @Singleton
    abstract fun bindWorkoutRepository(impl: WorkoutRepositoryImpl): WorkoutRepository

    @Binds @Singleton
    abstract fun bindBodyRepository(impl: BodyRepositoryImpl): BodyRepository

    @Binds @Singleton
    abstract fun bindStrengthGoalRepository(impl: StrengthGoalRepositoryImpl): StrengthGoalRepository

    @Binds @Singleton
    abstract fun bindRewardsRepository(impl: RewardsRepositoryImpl): RewardsRepository

    @Binds @Singleton
    abstract fun bindCardioRepository(impl: CardioRepositoryImpl): CardioRepository

    @Binds @Singleton
    abstract fun bindNutritionRepository(impl: NutritionRepositoryImpl): NutritionRepository

    @Binds @Singleton
    abstract fun bindBackupRepository(impl: BackupRepositoryImpl): BackupRepository

    @Binds @Singleton
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository

    @Binds @Singleton
    abstract fun bindExportRepository(impl: ExportRepositoryImpl): ExportRepository

    @Binds
    @Singleton
    abstract fun bindSigningSecretProvider(
        impl: KeystoreSigningSecretProvider
    ): SigningSecretProvider
}
