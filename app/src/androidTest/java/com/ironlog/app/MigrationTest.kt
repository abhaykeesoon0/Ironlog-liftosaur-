package com.ironlog.app

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironlog.app.core.database.AppDatabase
import com.ironlog.app.di.DatabaseModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Walks a version-1 database through every migration to the current schema
 * and checks that a workout logged on day one is still there at the end.
 *
 * This is the test that protects the promise in the database module: no
 * destructive fallback, no lost history on an upgrade. Room validates the
 * migrated schema against the exported JSON for the target version, so a
 * migration that produces a column of the wrong type fails here rather than
 * on a user's phone.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun aWorkoutLoggedOnVersion1SurvivesEveryMigration() {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                """
                INSERT INTO users (id, email, displayName, passwordHash, passwordSalt, createdAt, unitSystem,
                    bodyweightKg, heightCm, avatarPath, biometricLockEnabled, restTimerDefaultSeconds,
                    autoRestTimerEnabled, weeklyGoalWorkouts)
                VALUES ('u1', 'day.one@example.com', 'Day One', 'hash', 'salt', 1000, 'METRIC',
                    80.0, NULL, NULL, 0, 120, 1, 4)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO workouts (id, userId, routineId, name, startedAt, finishedAt, notes, isActive,
                    totalVolumeKg, totalSets, totalReps, durationSeconds, prCount)
                VALUES ('w1', 'u1', NULL, 'First session', 2000, 5000, '', 0, 1234.5, 12, 96, 3000, 2)
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            dbName,
            AppDatabase.VERSION,
            true,
            DatabaseModule.MIGRATION_1_2,
            DatabaseModule.MIGRATION_2_3,
            DatabaseModule.MIGRATION_3_4,
            DatabaseModule.MIGRATION_4_5,
            DatabaseModule.MIGRATION_5_6,
            DatabaseModule.MIGRATION_6_7,
            DatabaseModule.MIGRATION_7_8
        )

        db.query("SELECT name, totalVolumeKg, prCount FROM workouts WHERE id = 'w1'").use { cursor ->
            assertTrue("the day-one workout is gone", cursor.moveToFirst())
            assertEquals("First session", cursor.getString(0))
            assertEquals(1234.5, cursor.getDouble(1), 1e-9)
            assertEquals(2, cursor.getInt(2))
        }
        // Tables added along the way exist and are empty, not missing.
        db.query("SELECT COUNT(*) FROM foods").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        db.query("SELECT targetWeightKg, progressionKg FROM routine_exercises LIMIT 0").use { cursor ->
            assertEquals(2, cursor.columnCount)
        }
        db.query("SELECT avgHeartRate, maxHeartRate FROM cardio_sessions LIMIT 0").use { cursor ->
            assertEquals(2, cursor.columnCount)
        }
    }
}
