package com.inventoria.app.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.inventoria.app.ui.splash.SplashActivity

/**
 * How a home-screen widget opens the app on a particular screen.
 *
 * The app has one exported entry point, [SplashActivity], which forwards to MainActivity once an
 * account exists. A widget tap therefore aims at Splash and carries the destination as
 * [EXTRA_NAV_ROUTE]; Splash forwards the extra, MainActivity hands it to InventoriaApp, and that
 * navigates -- through switchToTab for a tab route, since a plain navigate() to a tab corrupts the
 * nav bar's save/restore state (see InventoriaApp.switchToTab).
 *
 * Route strings are the NavHost's own: "todos", "tasks", "inventory_hub", "collection/{id}",
 * "item_detail/{id}".
 */
object WidgetNav {
    const val EXTRA_NAV_ROUTE = "nav_route"

    const val ROUTE_TODOS = "todos"
    const val ROUTE_TASKS = "tasks"
    const val ROUTE_INVENTORY = "inventory_hub"

    fun collectionRoute(collectionId: Long) = "collection/$collectionId"
    fun itemDetailRoute(itemId: Long) = "item_detail/$itemId"

    /** The explicit Splash intent for [route], usable directly or wrapped in a PendingIntent. */
    fun openIntent(context: Context, route: String): Intent =
        Intent(context, SplashActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            // Extras are not part of a PendingIntent's identity, so two routes would otherwise
            // collapse into one cached PendingIntent under FLAG_UPDATE_CURRENT; the data URI is.
            data = Uri.parse("inventoria://widget/$route")
            putExtra(EXTRA_NAV_ROUTE, route)
        }

    fun openPendingIntent(context: Context, route: String, requestCode: Int = route.hashCode()): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            openIntent(context, route),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
