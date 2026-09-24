package com.ironlog.app.core

import com.ironlog.app.core.media.CsvImport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class CsvImportTest {

    private val utc = ZoneId.of("UTC")

    @Test
    fun `a Strong export becomes workouts with sets, warm-ups marked and pounds converted`() {
        val csv = """
            Date,Workout Name,Duration,Exercise Name,Set Order,Weight (lbs),Reps,Distance,Seconds,Notes,Workout Notes,RPE
            2026-09-20 18:00:00,Push,1h 5m,Bench Press (Barbell),W,95,10,0,0,,,
            2026-09-20 18:00:00,Push,1h 5m,Bench Press (Barbell),1,135,8,0,0,,,
            2026-09-20 18:00:00,Push,1h 5m,Bench Press (Barbell),2,135,8,0,0,,,
            2026-09-20 18:00:00,Push,1h 5m,Bench Press (Barbell),Rest Timer,0,0,0,90,,,
            2026-09-22 07:30:00,Legs,45m,Squat (Barbell),1,225,5,0,0,,,
        """.trimIndent()
        val result = CsvImport.parse(csv, utc)
        assertEquals(CsvImport.Source.STRONG, result.source)
        assertEquals(2, result.backup.workouts.size)
        assertEquals(1, result.rowsSkipped)

        val push = result.backup.workouts[0]
        assertEquals("Push", push.name)
        assertEquals(65 * 60 * 1000L, push.finishedAt!! - push.startedAt)
        val bench = push.exercises.single()
        assertEquals("Bench Press (Barbell)", bench.name)
        assertEquals(3, bench.sets.size)
        assertEquals("WARMUP", bench.sets[0].type)
        assertEquals("NORMAL", bench.sets[1].type)
        assertEquals(61.235, bench.sets[1].weightKg!!, 0.01)
        assertEquals(8, bench.sets[1].reps)
    }

    @Test
    fun `a Hevy export keeps kilograms, set types and the end time`() {
        val csv = """
            "title","start_time","end_time","description","exercise_title","superset_id","exercise_notes","set_index","set_type","weight_kg","reps","distance_km","duration_seconds","rpe"
            "Pull day","21 Sep 2026, 17:00","21 Sep 2026, 18:10","","Lat Pulldown (Cable)","","",0,"warmup",30,12,"","",""
            "Pull day","21 Sep 2026, 17:00","21 Sep 2026, 18:10","","Lat Pulldown (Cable)","","",1,"normal",55,10,"","",8
            "Pull day","21 Sep 2026, 17:00","21 Sep 2026, 18:10","","Lat Pulldown (Cable)","","",2,"failure",55,9,"","",""
        """.trimIndent()
        val result = CsvImport.parse(csv, utc)
        assertEquals(CsvImport.Source.HEVY, result.source)
        val workout = result.backup.workouts.single()
        assertEquals("Pull day", workout.name)
        assertEquals(70 * 60 * 1000L, workout.finishedAt!! - workout.startedAt)
        val sets = workout.exercises.single().sets
        assertEquals(listOf("WARMUP", "NORMAL", "FAILURE"), sets.map { it.type })
        assertEquals(55.0, sets[1].weightKg!!, 1e-9)
        assertEquals(8.0, sets[1].rpe!!, 1e-9)
    }

    @Test
    fun `anything else is refused rather than guessed`() {
        assertThrows(CsvImport.UnrecognisedException::class.java) { CsvImport.parse("a,b,c\n1,2,3", utc) }
        assertThrows(CsvImport.UnrecognisedException::class.java) { CsvImport.parse("", utc) }
    }

    @Test
    fun `quoted fields with commas and newlines are read whole`() {
        val rows = CsvImport.readCsv("a,\"b, with comma\",\"line\nbreak\",\"say \"\"hi\"\"\"\n1,2,3,4")
        assertEquals(listOf("a", "b, with comma", "line\nbreak", "say \"hi\""), rows[0])
        assertEquals(listOf("1", "2", "3", "4"), rows[1])
    }

    @Test
    fun `durations and dates parse or fail cleanly`() {
        assertEquals(3900, CsvImport.parseDuration("1h 5m"))
        assertEquals(45 * 60, CsvImport.parseDuration("45m"))
        assertNull(CsvImport.parseDuration("soon"))
        assertTrue(CsvImport.parseDate("2026-09-20 18:00:00", utc)!! > 0)
        assertNull(CsvImport.parseDate("yesterday", utc))
    }
}
