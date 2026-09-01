package com.inventoria.app.widget.collection

import android.content.Context

/**
 * Which collection each Collection widget instance shows, keyed by app widget id.
 *
 * Plain SharedPreferences, deliberately excluded from backup (backup_rules.xml and
 * data_extraction_rules.xml): widget ids are assigned by the launcher on the device the widget
 * was placed on, so a restored mapping would point stale ids at collections.
 */
object CollectionWidgetPrefs {
    private const val FILE = "widget_prefs"
    private const val KEY_PREFIX = "collection_"

    fun collectionId(context: Context, appWidgetId: Int): Long? =
        prefs(context).getLong(KEY_PREFIX + appWidgetId, 0L).takeIf { it > 0L }

    fun save(context: Context, appWidgetId: Int, collectionId: Long) {
        prefs(context).edit().putLong(KEY_PREFIX + appWidgetId, collectionId).apply()
    }

    fun remove(context: Context, appWidgetIds: IntArray) {
        val editor = prefs(context).edit()
        appWidgetIds.forEach { editor.remove(KEY_PREFIX + it) }
        editor.apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
