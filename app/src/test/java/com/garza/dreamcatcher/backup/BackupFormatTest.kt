package com.garza.dreamcatcher.backup

import com.garza.dreamcatcher.data.Alarm
import com.garza.dreamcatcher.data.Dream
import com.garza.dreamcatcher.data.DreamTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupFormatTest {

    private val dreams = listOf(
        Dream(id = 7, text = "Flying over the sea", createdAt = 2_000, updatedAt = 2_500, tags = setOf(DreamTag.DREAM, DreamTag.LUCID), customTags = linkedSetOf("Calm", "Ocean"), fromAlarm = true),
        Dream(id = 3, text = "", createdAt = 1_000, tags = setOf(DreamTag.NO_DREAM)),
    )
    private val alarms = listOf(Alarm(id = 1, hour = 6, minute = 30, repeatDays = 0b11111, label = "Work"))

    private fun roundTrip(): BackupFile =
        BackupFormat.decode(BackupFormat.encode(BackupFormat.create(dreams, alarms, SettingsRecord("ocean", "GUIDED"), now = 9_000)))

    @Test
    fun everythingSurvivesARoundTripIntoAnEmptyApp() {
        val plan = BackupFormat.plan(roundTrip(), emptyList(), emptyList())
        // Ids are left for the database to assign; everything else comes back, oldest first.
        assertEquals(dreams.reversed().map { it.copy(id = 0) }, plan.newDreams)
        assertEquals(alarms.map { it.copy(id = 0) }, plan.newAlarms)
        assertTrue(plan.restoreSettings)
        assertEquals(SettingsRecord("ocean", "GUIDED"), plan.backup.settings)
    }

    @Test
    fun restoringTwiceAddsNothing() {
        val plan = BackupFormat.plan(roundTrip(), dreams, alarms)
        assertTrue(plan.newDreams.isEmpty())
        assertTrue(plan.newAlarms.isEmpty())
        // The journal isn't empty, so the current color scheme is kept.
        assertFalse(plan.restoreSettings)
    }

    @Test
    fun onlyMissingEntriesAreAdded() {
        val plan = BackupFormat.plan(roundTrip(), dreams.take(1), emptyList())
        assertEquals(listOf(1_000L), plan.newDreams.map { it.createdAt })
    }

    @Test
    fun unknownFieldsAndTagsFromNewerVersionsAreIgnored() {
        val text = """
            {"app":"dreamcatcher","version":1,"exportedAt":5,"mood":"new field",
             "dreams":[{"text":"Hi","createdAt":10,"tags":["dream","telepathic"],"customTags":["Two\nlines"," "]}],
             "alarms":[{"hour":25,"minute":0},{"hour":7,"minute":5,"repeatDays":255}]}
        """.trimIndent()
        val plan = BackupFormat.plan(BackupFormat.decode(text), emptyList(), emptyList())
        val dream = plan.newDreams.single()
        assertEquals(setOf(DreamTag.DREAM), dream.tags)
        assertEquals(setOf("Two lines"), dream.customTags)
        // The impossible 25:00 alarm is dropped and stray day bits are cleared.
        assertEquals(listOf(Alarm(hour = 7, minute = 5, repeatDays = 0x7F)), plan.newAlarms)
        assertFalse(plan.restoreSettings)
    }

    @Test
    fun otherFilesAreRejectedWithAClearMessage() {
        fun messageFor(text: String): String? = try {
            BackupFormat.decode(text)
            null
        } catch (e: BackupException) {
            e.message
        }
        assertEquals("This file isn't a Dreamcatcher backup.", messageFor("My dream journal\nOct 1: flying"))
        assertEquals("This file isn't a Dreamcatcher backup.", messageFor("""{"app":"other","exportedAt":1}"""))
        assertEquals("This file isn't a Dreamcatcher backup.", messageFor("""{"dreams":[]}"""))
        val newer = messageFor("""{"app":"dreamcatcher","version":99,"exportedAt":1}""") ?: fail("accepted a newer file")
        assertTrue(newer.toString().contains("newer version"))
    }
}
