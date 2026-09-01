package com.inventoria.app.widget.collection

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.inventoria.app.R
import com.inventoria.app.data.model.InventoryCollectionReadiness
import com.inventoria.app.data.model.InventoryCollectionWithItems
import com.inventoria.app.data.repository.CollectionRepository
import com.inventoria.app.widget.WidgetActionReceiver
import com.inventoria.app.widget.WidgetNav
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Collection home-screen widget: one chosen collection, its readiness, and its items.
 *
 * Which collection is per instance ([CollectionWidgetPrefs]), picked in
 * [CollectionWidgetConfigureActivity] when the widget is placed. A collection that has since
 * been deleted shows a "choose another" state that reopens that picker for this instance.
 */
@AndroidEntryPoint
class CollectionWidgetProvider : AppWidgetProvider() {

    @Inject
    lateinit var collectionRepository: CollectionRepository

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                appWidgetIds.forEach { id ->
                    val collectionId = CollectionWidgetPrefs.collectionId(context, id)
                    val withItems = collectionId?.let { collectionRepository.getCollectionWithItems(it).first() }
                    val readiness = collectionId?.let { collectionRepository.getCollectionReadiness(it).first() }
                    appWidgetManager.updateAppWidget(id, build(context, id, withItems, readiness))
                }
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_collection_list)
            } catch (e: Exception) {
                Log.e(TAG, "Updating the collection widget failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        CollectionWidgetPrefs.remove(context, appWidgetIds)
    }

    companion object {
        private const val TAG = "CollectionWidgetProvider"

        /** Redraw the given instances, or every placed one when [appWidgetIds] is null. */
        fun requestUpdate(context: Context, appWidgetIds: IntArray? = null) {
            val ids = appWidgetIds ?: AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, CollectionWidgetProvider::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, CollectionWidgetProvider::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            )
        }

        private fun build(
            context: Context,
            appWidgetId: Int,
            withItems: InventoryCollectionWithItems?,
            readiness: InventoryCollectionReadiness?
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_collection)

            if (withItems == null) {
                // Not configured yet, or the collection is gone: whole widget reopens the picker.
                val configure = Intent(context, CollectionWidgetConfigureActivity::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    data = Uri.parse("inventoria://widget-collection/configure/$appWidgetId")
                }
                val pending = PendingIntent.getActivity(
                    context, appWidgetId, configure, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setTextViewText(R.id.widget_collection_icon, "📦")
                views.setTextViewText(R.id.widget_collection_name, context.getString(R.string.widget_collection_label))
                views.setTextViewText(R.id.widget_collection_ready, "")
                views.setViewVisibility(R.id.widget_collection_dot, View.GONE)
                views.setViewVisibility(R.id.widget_collection_list, View.GONE)
                views.setTextViewText(R.id.widget_collection_empty, context.getString(R.string.widget_collection_removed))
                views.setViewVisibility(R.id.widget_collection_empty, View.VISIBLE)
                views.setOnClickPendingIntent(R.id.widget_collection_header, pending)
                views.setOnClickPendingIntent(R.id.widget_collection_empty, pending)
                return views
            }

            val collection = withItems.collection
            views.setTextViewText(R.id.widget_collection_icon, collection.icon ?: "📦")
            views.setTextViewText(R.id.widget_collection_name, collection.name.ifBlank { context.getString(R.string.widget_collection_label) })
            views.setTextViewText(
                R.id.widget_collection_ready,
                readiness?.let { context.getString(R.string.widget_collection_ready, it.availableItems, it.totalItems) } ?: ""
            )
            // A collection colour with no alpha would vanish; fall back to the header's own tint.
            val color = if (collection.color ushr 24 == 0) 0xFFE9D5FF.toInt() else collection.color
            views.setInt(R.id.widget_collection_dot, "setColorFilter", color)
            views.setViewVisibility(R.id.widget_collection_dot, View.VISIBLE)
            views.setOnClickPendingIntent(
                R.id.widget_collection_header,
                WidgetNav.openPendingIntent(context, WidgetNav.collectionRoute(collection.id))
            )

            val adapterIntent = Intent(context, CollectionWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse("inventoria://widget-collection/$appWidgetId")
            }
            views.setRemoteAdapter(R.id.widget_collection_list, adapterIntent)
            views.setViewVisibility(R.id.widget_collection_list, View.VISIBLE)
            views.setTextViewText(R.id.widget_collection_empty, context.getString(R.string.widget_collection_empty))
            views.setViewVisibility(R.id.widget_collection_empty, View.GONE)
            views.setEmptyView(R.id.widget_collection_list, R.id.widget_collection_empty)
            views.setPendingIntentTemplate(R.id.widget_collection_list, WidgetActionReceiver.rowTemplate(context, "collection"))
            return views
        }
    }
}
