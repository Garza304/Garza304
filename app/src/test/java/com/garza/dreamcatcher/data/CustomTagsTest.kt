package com.garza.dreamcatcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomTagsTest {

    @Test
    fun normalizeTrimsCollapsesAndCapitalizes() {
        assertEquals("Flying over water", CustomTags.normalize("  flying   over\nwater "))
    }

    @Test
    fun normalizeRejectsBlankAndBuiltInNames() {
        assertNull(CustomTags.normalize("   "))
        assertNull(CustomTags.normalize("lucid"))
        assertNull(CustomTags.normalize("No Dream"))
    }

    @Test
    fun normalizeCapsLength() {
        assertEquals(CustomTags.MAX_LENGTH, CustomTags.normalize("a".repeat(50))!!.length)
    }

    @Test
    fun encodeDecodeRoundTripsInOrder() {
        val tags = linkedSetOf("Melatonin", "Anxious", "Flying")
        assertEquals(tags.toList(), CustomTags.decode(CustomTags.encode(tags)).toList())
        assertEquals(emptySet<String>(), CustomTags.decode(""))
    }

    @Test
    fun rankOrdersByUseThenAlphabetically() {
        val rows = listOf("Flying\nAnxious", "anchor\nAnxious", "Flying\nAnxious", "Zoo")
        assertEquals(listOf("Anxious", "Flying", "anchor", "Zoo"), CustomTags.rank(rows))
    }

    @Test
    fun toggleIgnoresCase() {
        assertEquals(setOf("Calm"), CustomTags.toggle(setOf("Calm", "Anxious"), "anxious"))
        assertEquals(setOf("Calm", "Joyful"), CustomTags.toggle(setOf("Calm"), "Joyful"))
    }
}
