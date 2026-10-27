package com.garza.dreamcatcher.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import com.garza.dreamcatcher.R
import com.garza.dreamcatcher.Settings
import com.garza.dreamcatcher.dream.DreamEntryActivity

/**
 * Home screen widget: a mic button that opens a new dream entry, already listening.
 * Drawn in the color scheme picked in Settings.
 */
abstract class QuickMicWidgetBase(private val layout: Int, private val hasLabel: Boolean) : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        manager.updateAppWidget(appWidgetIds, buildViews(context))
    }

    fun buildViews(context: Context): RemoteViews {
        val palette = Settings.palette.value
        val open = PendingIntent.getActivity(
            context, 0,
            DreamEntryActivity.wakeUpIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return RemoteViews(context.packageName, layout).apply {
            setInt(R.id.widget_bg, "setColorFilter", palette.surfaceContainer)
            setInt(R.id.widget_mic_bg, "setColorFilter", palette.primary)
            setInt(R.id.widget_mic, "setColorFilter", palette.onPrimary)
            if (hasLabel) {
                setTextColor(R.id.widget_title, palette.text)
                setTextColor(R.id.widget_subtitle, palette.onSurfaceVariant)
            }
            setOnClickPendingIntent(R.id.widget_root, open)
        }
    }

    companion object {
        /** Redraws every placed widget, e.g. after the color scheme changes. */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            for (provider in listOf(QuickMicWidget(), QuickMicWideWidget())) {
                val ids = manager.getAppWidgetIds(ComponentName(context, provider::class.java))
                if (ids.isNotEmpty()) manager.updateAppWidget(ids, provider.buildViews(context))
            }
        }
    }
}

/** 1×1: just the mic. */
class QuickMicWidget : QuickMicWidgetBase(R.layout.widget_quick_mic, hasLabel = false)

/** 2×1: the mic with a "Record your dream" label. */
class QuickMicWideWidget : QuickMicWidgetBase(R.layout.widget_quick_mic_wide, hasLabel = true)
