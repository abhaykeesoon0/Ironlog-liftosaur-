package com.ironlog.app

import android.app.Application
import android.util.Log
import com.ironlog.app.core.notifications.NotificationHelper
import com.ironlog.app.data.dao.ExerciseDao
import com.ironlog.app.data.dao.NutritionDao
import com.ironlog.app.data.seed.ExerciseSeed
import com.ironlog.app.data.seed.FoodSeed
import com.ironlog.app.di.ApplicationScope
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.ironlog.app.core.widget.WidgetSync
import com.ironlog.app.core.widget.AppShortcuts

@HiltAndroidApp
class IronLogApplication : Application() {

    @Inject lateinit var exerciseDao: ExerciseDao
    @Inject lateinit var nutritionDao: NutritionDao
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope
    @Inject lateinit var widgetSync: WidgetSync

    override fun onCreate() {
        super.onCreate()

        // Constructing the helper creates the notification channels. Channels must
        // exist before any notification is posted, and doing it here means the
        // rest timer never races against channel creation.
        notificationHelper.hashCode()

        // The widget follows the log for the life of the process.
        widgetSync.start(applicationScope)
        AppShortcuts.publish(this)

        seedExerciseLibrary()
    }

    /**
     * The library is written once, off the main thread, and only when it is
     * missing. Upsert rather than insert so a future release can correct a seeded
     * exercise without duplicating it.
     */
    private fun seedExerciseLibrary() {
        applicationScope.launch {
            runCatching {
                if (exerciseDao.countSeeded() < ExerciseSeed.all.size) {
                    exerciseDao.upsertAll(ExerciseSeed.all)
                }
                if (nutritionDao.countSeeded() < FoodSeed.all.size) {
                    nutritionDao.upsertFoods(FoodSeed.all)
                }
            }.onFailure {
                // Not fatal: the check runs again on the next launch. Logged so a
                // persistent failure shows up in logcat rather than as an empty library.
                Log.e("IronLog", "Exercise library seeding failed", it)
            }
        }
    }
}
