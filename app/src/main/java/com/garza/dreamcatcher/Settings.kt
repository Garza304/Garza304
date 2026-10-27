package com.garza.dreamcatcher

import android.content.Context
import com.garza.dreamcatcher.backup.SettingsRecord
import com.garza.dreamcatcher.ui.theme.Palette
import com.garza.dreamcatcher.ui.theme.Palettes
import com.garza.dreamcatcher.widget.QuickMicWidgetBase
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

/** How the Insights tab is laid out. */
enum class InsightsStyle(val title: String, val description: String) {
    CALENDAR("Calendar first", "A month calendar, then totals and charts for a period you pick."),
    HEATMAP("Streak and heatmap", "Your streak up top, 20 weeks of mornings at a glance, and how you compare with before."),
    DIGEST("Weekly digest", "A plain-language summary of the week or month, what wakes you best, and your latest dream."),
    PATTERNS("Patterns", "How your recall is changing, what you remember, and when you write."),
}

/** Small user preferences, kept in SharedPreferences. */
object Settings {
    private const val PREFS = "settings"
    private const val KEY_ENTRY_STYLE = "entry_style"
    private const val KEY_PALETTE = "palette"
    private const val KEY_INSIGHTS_STYLE = "insights_style"

    private val paletteState = MutableStateFlow(Palettes.default)

    /** The color scheme in use. Observed by the theme so a change applies everywhere at once. */
    val palette: StateFlow<Palette> = paletteState.asStateFlow()

    private val insightsStyleState = MutableStateFlow(InsightsStyle.CALENDAR)

    /** The Insights tab layout, observed so a change in Settings shows straight away. */
    val insightsStyle: StateFlow<InsightsStyle> = insightsStyleState.asStateFlow()

    /** Reads saved settings; called once when the app starts. */
    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        paletteState.value = Palettes.byId(prefs.getString(KEY_PALETTE, null))
        val style = prefs.getString(KEY_INSIGHTS_STYLE, null)
        insightsStyleState.value = InsightsStyle.entries.firstOrNull { it.name == style } ?: InsightsStyle.CALENDAR
    }

    fun setInsightsStyle(context: Context, style: InsightsStyle) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_INSIGHTS_STYLE, style.name).apply()
        insightsStyleState.value = style
    }

    fun setPalette(context: Context, palette: Palette) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_PALETTE, palette.id).apply()
        paletteState.value = palette
        QuickMicWidgetBase.refreshAll(context)
    }

    fun entryStyle(context: Context): EntryStyle {
        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ENTRY_STYLE, null)
        return EntryStyle.entries.firstOrNull { it.name == name } ?: EntryStyle.VOICE
    }

    fun setEntryStyle(context: Context, style: EntryStyle) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ENTRY_STYLE, style.name).apply()
    }

    /** The settings worth carrying to a new phone. */
    fun toBackup(context: Context) =
        SettingsRecord(palette = palette.value.id, entryStyle = entryStyle(context).name, insightsStyle = insightsStyle.value.name)

    fun restore(context: Context, record: SettingsRecord) {
        record.palette?.let { id -> Palettes.all.firstOrNull { it.id == id }?.let { setPalette(context, it) } }
        record.entryStyle?.let { name -> EntryStyle.entries.firstOrNull { it.name == name }?.let { setEntryStyle(context, it) } }
        record.insightsStyle?.let { name -> InsightsStyle.entries.firstOrNull { it.name == name }?.let { setInsightsStyle(context, it) } }
    }
}
