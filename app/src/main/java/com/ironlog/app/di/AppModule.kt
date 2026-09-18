package com.ironlog.app.di

import android.content.Context
import androidx.room.Room
import com.ironlog.app.core.database.AppDatabase
import com.ironlog.app.core.security.KeystoreSigningSecretProvider
import com.ironlog.app.core.security.SigningSecretProvider
import com.ironlog.app.data.dao.BodyDao
import com.ironlog.app.data.dao.CardioDao
import com.ironlog.app.data.dao.ExerciseDao
import com.ironlog.app.data.dao.PersonalRecordDao
import com.ironlog.app.data.dao.RoutineDao
import com.ironlog.app.data.dao.UserDao
import com.ironlog.app.data.dao.WorkoutDao
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.ironlog.app.data.repository.AuthRepository
import com.ironlog.app.data.repository.AuthRepositoryImpl
import com.ironlog.app.data.repository.BodyRepository
import com.ironlog.app.data.repository.BodyRepositoryImpl
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            // No destructive fallback: silently wiping a user's training history
            // on a schema change would be a data-loss bug, not a convenience.
            // Migrations are added explicitly when the schema moves.
            .build()

    @Provides fun provideUserDao(db: AppDatabase): UserDao = db.userDao()
    @Provides fun provideExerciseDao(db: AppDatabase): ExerciseDao = db.exerciseDao()
    @Provides fun provideRoutineDao(db: AppDatabase): RoutineDao = db.routineDao()
    @Provides fun provideWorkoutDao(db: AppDatabase): WorkoutDao = db.workoutDao()
    @Provides fun providePersonalRecordDao(db: AppDatabase): PersonalRecordDao = db.personalRecordDao()
    @Provides fun provideBodyDao(db: AppDatabase): BodyDao = db.bodyDao()
    @Provides fun provideCardioDao(db: AppDatabase): CardioDao = db.cardioDao()

    /** Non-sensitive preferences only; session secrets live in SecureTokenStore. */
    @Provides
    @Singleton
    fun providePreferences(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("ironlog_prefs") }
}

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
    abstract fun bindCardioRepository(impl: CardioRepositoryImpl): CardioRepository

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
