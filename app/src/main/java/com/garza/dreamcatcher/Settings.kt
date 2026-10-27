package com.garza.dreamcatcher

import android.content.Context

/** How a new dream entry starts. */
enum class EntryStyle(val title: String, val description: String) {
    VOICE(
        "Voice first",
        "A big mic and your words in large type, so you can just talk. Tag the dream afterwards.",
    ),
    GUIDED(
        "Guided questions",
        "Short prompts: what happened, who was there, where you were and how it felt.",
    ),
}

/** Small user preferences, kept in SharedPreferences. */
object Settings {
    private const val PREFS = "settings"
    private const val KEY_ENTRY_STYLE = "entry_style"

    fun entryStyle(context: Context): EntryStyle {
        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ENTRY_STYLE, null)
        return EntryStyle.entries.firstOrNull { it.name == name } ?: EntryStyle.VOICE
    }

    fun setEntryStyle(context: Context, style: EntryStyle) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ENTRY_STYLE, style.name).apply()
    }
}
