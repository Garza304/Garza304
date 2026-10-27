package com.garza.dreamcatcher.dream

import com.garza.dreamcatcher.data.Dream
import com.garza.dreamcatcher.data.DreamTag
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Text helpers for dream entries, free of Android classes so they can be unit tested. */
object DreamText {

    /** Appends a dictated [phrase] to [existing] text, capitalizing the start of a new entry or sentence. */
    fun appendPhrase(existing: String, phrase: String): String {
        val words = phrase.trim()
        if (words.isEmpty()) return existing
        val trimmedEnd = existing.trimEnd()
        val startsSentence = trimmedEnd.isEmpty() || trimmedEnd.last() in ".!?" || existing.endsWith('\n')
        val piece = if (startsSentence) words.replaceFirstChar { it.titlecase(Locale.getDefault()) } else words
        return when {
            existing.isEmpty() -> piece
            existing.last().isWhitespace() -> existing + piece
            else -> "$existing $piece"
        }
    }

    fun formatDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
        val date = Instant.ofEpochMilli(epochMillis).atZone(zone)
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).format(date)
    }

    fun formatTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
        val date = Instant.ofEpochMilli(epochMillis).atZone(zone)
        return DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(date)
    }

    /** Built-in tag labels followed by custom tags, in display order. */
    fun tagLabels(dream: Dream): List<String> =
        DreamTag.entries.filter { it in dream.tags }.map { it.label } + dream.customTags

    /** True when every word of [query] appears in the dream's text or tags. */
    fun matches(dream: Dream, query: String): Boolean {
        if (query.isBlank()) return true
        val haystack = dream.text + "\n" + tagLabels(dream).joinToString("\n")
        return query.trim().split(Regex("\\s+")).all { haystack.contains(it, ignoreCase = true) }
    }

    /** Joins the guided questions' answers into one entry; blank answers are left out. */
    fun composeGuided(whatHappened: String, who: String, where: String): String =
        buildList {
            whatHappened.trim().takeIf { it.isNotEmpty() }?.let(::add)
            val details = buildList {
                who.trim().takeIf { it.isNotEmpty() }?.let { add("Who was there: $it") }
                where.trim().takeIf { it.isNotEmpty() }?.let { add("Where: $it") }
            }
            if (details.isNotEmpty()) add(details.joinToString("\n"))
        }.joinToString("\n\n")

    /** The whole journal as plain text, oldest first, for sharing or backing up. */
    fun export(dreams: List<Dream>, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
        dreams.sortedBy { it.createdAt }.joinToString(separator = "\n\n") { dream ->
            buildString {
                append(formatDate(dream.createdAt, zone, locale))
                append(" · ")
                append(formatTime(dream.createdAt, zone, locale))
                val labels = tagLabels(dream)
                if (labels.isNotEmpty()) {
                    append(" · ")
                    append(labels.joinToString(", "))
                }
                if (dream.text.isNotBlank()) {
                    append('\n')
                    append(dream.text.trim())
                }
            }
        }
}
