package com.garza.dreamcatcher.data

import com.garza.dreamcatcher.data.DreamTag.DREAM
import com.garza.dreamcatcher.data.DreamTag.LUCID
import com.garza.dreamcatcher.data.DreamTag.NIGHTMARE
import com.garza.dreamcatcher.data.DreamTag.NO_DREAM
import com.garza.dreamcatcher.data.DreamTag.RECURRING
import org.junit.Assert.assertEquals
import org.junit.Test

class DreamTagTest {

    @Test
    fun encodesInStableOrderAndDecodesBack() {
        val tags = setOf(RECURRING, NIGHTMARE, LUCID)
        assertEquals("nightmare,lucid,recurring", DreamTag.encode(tags))
        assertEquals(tags, DreamTag.decode("nightmare,lucid,recurring"))
    }

    @Test
    fun decodesEmptyAndUnknownValues() {
        assertEquals(emptySet<DreamTag>(), DreamTag.decode(""))
        assertEquals(setOf(LUCID), DreamTag.decode("lucid,something_removed"))
    }

    @Test
    fun togglingASelectedTagRemovesIt() {
        assertEquals(setOf(DREAM), DreamTag.toggle(setOf(DREAM, LUCID), LUCID))
    }

    @Test
    fun onlyOneKindAtATime() {
        assertEquals(setOf(NIGHTMARE, LUCID), DreamTag.toggle(setOf(DREAM, LUCID), NIGHTMARE))
    }

    @Test
    fun noDreamClearsLucidAndRecurring() {
        assertEquals(setOf(NO_DREAM), DreamTag.toggle(setOf(DREAM, LUCID, RECURRING), NO_DREAM))
    }

    @Test
    fun lucidOrRecurringClearsNoDream() {
        assertEquals(setOf(RECURRING), DreamTag.toggle(setOf(NO_DREAM), RECURRING))
    }

    @Test
    fun lucidAndRecurringCombine() {
        assertEquals(setOf(NIGHTMARE, LUCID, RECURRING), DreamTag.toggle(setOf(NIGHTMARE, LUCID), RECURRING))
    }
}
