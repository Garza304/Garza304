package com.garza.dreamcatcher.data

import java.util.Locale

/** Rules for the tags people make themselves (feelings, sleep notes, themes), kept free of Android classes. */
object CustomTags {

    const val MAX_LENGTH = 30

    /** Feelings offered by the guided questions; picking one adds it as a custom tag. */
    val FEELINGS = listOf("Calm", "Anxious", "Joyful", "Confused", "Scared")

    /**
     * Cleans up a typed tag name: trims it, collapses spaces, caps the length and capitalizes it.
     * Returns null when nothing usable is left or it would clash with a built-in tag.
     */
    fun normalize(raw: String): String? {
        val name = raw.replace(Regex("\\s+"), " ").trim().take(MAX_LENGTH).trim()
        if (name.isEmpty()) return null
        if (DreamTag.entries.any { it.label.equals(name, ignoreCase = true) }) return null
        return name.replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }

    fun encode(tags: Set<String>): String = tags.joinToString("\n")

    fun decode(value: String): Set<String> = value.split('\n').filterTo(LinkedHashSet()) { it.isNotBlank() }

    /** Every tag found in [encodedRows] (one encoded set per dream), most used first, then A to Z. */
    fun rank(encodedRows: List<String>): List<String> =
        encodedRows.flatMap(::decode)
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.lowercase() })
            .map { it.key }

    /** [name] added to or removed from [tags], matching case-insensitively so "anxious" and "Anxious" are one tag. */
    fun toggle(tags: Set<String>, name: String): Set<String> {
        val existing = tags.firstOrNull { it.equals(name, ignoreCase = true) }
        return if (existing != null) tags - existing else tags + name
    }
}
