package com.inventoria.app.widget.collection

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.inventoria.app.R
import com.inventoria.app.data.model.InventoryCollectionItem
import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.data.repository.CollectionRepository
import com.inventoria.app.widget.WidgetActionReceiver
import com.inventoria.app.widget.WidgetNav
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/** Supplies the Collection widget's rows; see TodoWidgetService for the shape. */
@AndroidEntryPoint
class CollectionWidgetService : RemoteViewsService() {

    @Inject
    lateinit var collectionRepository: CollectionRepository

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        return CollectionWidgetFactory(applicationContext, collectionRepository, appWidgetId)
    }
}

private class CollectionWidgetFactory(
    private val context: Context,
    private val collectionRepository: CollectionRepository,
    private val appWidgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

    private class Row(val item: InventoryItem, val membership: InventoryCollectionItem)

    private var rows: List<Row> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        val collectionId = CollectionWidgetPrefs.collectionId(context, appWidgetId)
        if (collectionId == null) {
            rows = emptyList()
            return
        }
        val withItems = runBlocking { collectionRepository.getCollectionWithItems(collectionId).first() }
        if (withItems == null) {
            rows = emptyList()
            return
        }
        val itemsById = withItems.items.associateBy { it.id }
        rows = withItems.collectionItems
            .sortedWith(compareBy({ it.sortOrder }, { it.addedAt }))
            .mapNotNull { membership -> itemsById[membership.itemId]?.let { Row(it, membership) } }
    }

    override fun onDestroy() {
        rows = emptyList()
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        val row = rows[position]
        val item = row.item
        val views = RemoteViews(context.packageName, R.layout.widget_collection_row)

        views.setTextViewText(R.id.widget_collection_item_name, item.name.ifBlank { "Item" })

        val location = item.getDisplayLocation()
        views.setTextViewText(R.id.widget_collection_item_location, location)
        views.setViewVisibility(R.id.widget_collection_item_location, if (location.isBlank()) View.GONE else View.VISIBLE)

        val short = item.quantity < row.membership.requiredQuantity
        val quantityText = if (short) {
            "${item.quantity}/${row.membership.requiredQuantity} ${context.getString(R.string.widget_short)}"
        } else {
            "×${item.quantity}"
        }
        views.setTextViewText(R.id.widget_collection_item_quantity, quantityText)
        views.setTextColor(
            R.id.widget_collection_item_quantity,
            context.getColor(if (short) R.color.error else R.color.widget_on_surface)
        )

        views.setOnClickFillInIntent(
            R.id.widget_collection_row,
            WidgetActionReceiver.rowFillIn(WidgetActionReceiver.ACTION_OPEN_ROUTE) {
                putExtra(WidgetActionReceiver.EXTRA_ROUTE, WidgetNav.itemDetailRoute(item.id))
            }
        )
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = rows[position].item.id

    override fun hasStableIds(): Boolean = true
}
