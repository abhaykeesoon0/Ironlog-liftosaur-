package com.ironlog.app.core

import com.ironlog.app.data.repository.Backup
import com.ironlog.app.data.repository.BackupAccount
import com.ironlog.app.data.repository.BackupFoodLog
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The backup file is the one artefact that outlives any single build, so
 * the format has to open old files and old builds have to survive new ones.
 */
class BackupFormatTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun aVersionOneFileStillOpensWithEverythingNewLeftEmpty() {
        val v1 = """
            {"format":"ironlog-backup","version":1,"exportedAt":1000,
             "workouts":[],"routines":[],"measurements":[],"cardio":[]}
        """.trimIndent()

        val backup = json.decodeFromString(Backup.serializer(), v1)

        assertEquals(1, backup.version)
        assertNull("no account in an old file", backup.account)
        assertTrue(backup.foods.isEmpty() && backup.foodLogs.isEmpty() && backup.strengthGoals.isEmpty())
        assertTrue(backup.settings.isEmpty())
    }

    @Test
    fun aVersionTwoFileRoundTripsTheAccountAndItsSettings() {
        val backup = Backup(
            exportedAt = 2000, workouts = emptyList(), routines = emptyList(), measurements = emptyList(), cardio = emptyList(),
            account = BackupAccount(
                id = "u1", email = "me@example.com", displayName = "Me", passwordHash = "hash", passwordSalt = "salt",
                createdAt = 1, unitSystem = "IMPERIAL", bodyweightKg = 80.0, heightCm = null, biometricLockEnabled = true,
                restTimerDefaultSeconds = 90, autoRestTimerEnabled = false, weeklyGoalWorkouts = 3
            ),
            foodLogs = listOf(BackupFoodLog("f1", "Oats", 80.0, "BREAKFAST", 3000, "")),
            settings = mapOf("plate_set" to "KILOGRAMS", "reminder_hour" to "7")
        )

        val text = json.encodeToString(Backup.serializer(), backup)
        val back = json.decodeFromString(Backup.serializer(), text)

        assertEquals(2, back.version)
        assertEquals("me@example.com", back.account?.email)
        assertEquals("IMPERIAL", back.account?.unitSystem)
        assertEquals("Oats", back.foodLogs.single().foodName)
        assertEquals("7", back.settings["reminder_hour"])
        // The password is not in the file in any form; only its hash and salt are.
        assertFalse(text.contains("\"password\""))
        assertTrue(text.contains("\"passwordHash\""))
    }

    @Test
    fun unknownFieldsFromANewerBuildAreIgnoredRatherThanFatal() {
        val future = """
            {"format":"ironlog-backup","version":3,"exportedAt":1,"workouts":[],"routines":[],"measurements":[],"cardio":[],
             "somethingNew":{"a":1}}
        """.trimIndent()
        val backup = json.decodeFromString(Backup.serializer(), future)
        assertEquals(3, backup.version)
    }
}
