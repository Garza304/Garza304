package com.garza.dreamcatcher.dream

import com.garza.dreamcatcher.data.Dream
import com.garza.dreamcatcher.data.DreamTag
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DreamTextTest {

    @Test
    fun firstPhraseIsCapitalized() {
        assertEquals("I was flying", DreamText.appendPhrase("", "i was flying"))
    }

    @Test
    fun laterPhrasesJoinWithASpace() {
        assertEquals("I was flying over the ocean", DreamText.appendPhrase("I was flying", " over the ocean "))
    }

    @Test
    fun phraseAfterSentenceEndIsCapitalized() {
        assertEquals("I was flying. Then I fell", DreamText.appendPhrase("I was flying.", "then I fell"))
    }

    @Test
    fun existingTrailingWhitespaceIsReused() {
        assertEquals("Line one\nThen two", DreamText.appendPhrase("Line one\n", "then two"))
    }

    @Test
    fun blankPhraseChangesNothing() {
        assertEquals("Same", DreamText.appendPhrase("Same", "   "))
    }

    @Test
    fun searchMatchesAllWordsIgnoringCase() {
        val dream = Dream(text = "A purple cat in my old school", createdAt = 0)
        assertTrue(DreamText.matches(dream, ""))
        assertTrue(DreamText.matches(dream, "CAT school"))
        assertFalse(DreamText.matches(dream, "cat dog"))
    }

    @Test
    fun searchAlsoMatchesTags() {
        val dream = Dream(text = "A long hallway", createdAt = 0, tags = setOf(DreamTag.NIGHTMARE), customTags = setOf("Falling"))
        assertTrue(DreamText.matches(dream, "falling"))
        assertTrue(DreamText.matches(dream, "nightmare hallway"))
        assertFalse(DreamText.matches(dream, "lucid"))
    }

    @Test
    fun composeGuidedJoinsAnswersAndSkipsBlanks() {
        assertEquals(
            "We missed the ferry.\n\nWho was there: My brother\nWhere: Grandmother's house",
            DreamText.composeGuided(" We missed the ferry. ", "My brother", "Grandmother's house"),
        )
        assertEquals("Where: A beach", DreamText.composeGuided("", " ", "A beach"))
        assertEquals("", DreamText.composeGuided("", "", ""))
    }

    @Test
    fun exportIsOldestFirstWithTags() {
        val zone = ZoneId.of("UTC")
        fun millis(day: Int) = ZonedDateTime.of(2026, 10, day, 7, 0, 0, 0, zone).toInstant().toEpochMilli()
        val dreams = listOf(
            Dream(
                text = "Second ",
                createdAt = millis(27),
                tags = setOf(DreamTag.RECURRING, DreamTag.DREAM),
                customTags = setOf("Flying"),
            ),
            Dream(text = "First", createdAt = millis(26)),
            Dream(text = "", createdAt = millis(28), tags = setOf(DreamTag.NO_DREAM)),
        )
        val expected = "Monday, October 26, 2026 · 7:00 AM\nFirst\n\n" +
            "Tuesday, October 27, 2026 · 7:00 AM · Dream, Recurring, Flying\nSecond\n\n" +
            "Wednesday, October 28, 2026 · 7:00 AM · No dream"
        val actual = DreamText.export(dreams, zone, Locale.US)
        // JDKs differ on whether the space before AM is narrow ( ); compare with it normalized.
        assertEquals(expected, actual.replace(' ', ' '))
    }
}
