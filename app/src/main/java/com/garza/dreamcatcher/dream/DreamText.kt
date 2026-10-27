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

    fun matches(dream: Dream, query: String): Boolean =
        query.isBlank() || query.trim().split(Regex("\\s+")).all { dream.text.contains(it, ignoreCase = true) }

    /** The whole journal as plain text, oldest first, for sharing or backing up. */
    fun export(dreams: List<Dream>, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
        dreams.sortedBy { it.createdAt }.joinToString(separator = "\n\n") { dream ->
            buildString {
                append(formatDate(dream.createdAt, zone, locale))
                append(" · ")
                append(formatTime(dream.createdAt, zone, locale))
                if (dream.tags.isNotEmpty()) {
                    append(" · ")
                    append(DreamTag.entries.filter { it in dream.tags }.joinToString(", ") { it.label })
                }
                if (dream.text.isNotBlank()) {
                    append('\n')
                    append(dream.text.trim())
                }
            }
        }
}
