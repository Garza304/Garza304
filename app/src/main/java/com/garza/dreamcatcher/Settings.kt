package com.garza.dreamcatcher

import android.content.Context
import com.garza.dreamcatcher.ui.theme.Palette
import com.garza.dreamcatcher.ui.theme.Palettes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    private const val KEY_PALETTE = "palette"

    private val paletteState = MutableStateFlow(Palettes.default)

    /** The color scheme in use. Observed by the theme so a change applies everywhere at once. */
    val palette: StateFlow<Palette> = paletteState.asStateFlow()

    /** Reads saved settings; called once when the app starts. */
    fun load(context: Context) {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PALETTE, null)
        paletteState.value = Palettes.byId(id)
    }

    fun setPalette(context: Context, palette: Palette) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_PALETTE, palette.id).apply()
        paletteState.value = palette
    }

    fun entryStyle(context: Context): EntryStyle {
        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ENTRY_STYLE, null)
        return EntryStyle.entries.firstOrNull { it.name == name } ?: EntryStyle.VOICE
    }

    fun setEntryStyle(context: Context, style: EntryStyle) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ENTRY_STYLE, style.name).apply()
    }
}
