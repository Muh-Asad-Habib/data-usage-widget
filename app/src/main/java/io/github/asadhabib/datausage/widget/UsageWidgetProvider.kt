package io.github.asadhabib.datausage.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle

class Widget2x1 : UsageWidgetProvider()
class Widget2x2 : UsageWidgetProvider()
class Widget4x1 : UsageWidgetProvider()

open class UsageWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = updateAsync(ctx, ids)

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, opts: Bundle) =
        updateAsync(ctx, intArrayOf(id))

    override fun onDeleted(ctx: Context, ids: IntArray) {
        ids.forEach { WidgetPrefs.delete(ctx, it) }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == WidgetRenderer.ACTION_REFRESH) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            updateAsync(ctx, if (id == AppWidgetManager.INVALID_APPWIDGET_ID) WidgetUpdater.ids(ctx) else intArrayOf(id))
        } else super.onReceive(ctx, intent)
    }

    private fun updateAsync(ctx: Context, ids: IntArray) {
        val pending = goAsync()
        Thread {
            try { ids.forEach { WidgetUpdater.update(ctx, it) } } finally { pending.finish() }
        }.start()
    }
}

object WidgetUpdater {
    private val providers = listOf(
        UsageWidgetProvider::class.java, Widget2x1::class.java, Widget2x2::class.java, Widget4x1::class.java,
    )

    fun ids(ctx: Context): IntArray {
        val mgr = AppWidgetManager.getInstance(ctx)
        return providers.flatMap { mgr.getAppWidgetIds(ComponentName(ctx, it)).asList() }.toIntArray()
    }

    fun size(ctx: Context, id: Int): Pair<Int, Int> {
        val mgr = AppWidgetManager.getInstance(ctx)
        val o = mgr.getAppWidgetOptions(id)
        val info = mgr.getAppWidgetInfo(id)
        val modern = android.os.Build.VERSION.SDK_INT >= 31
        val cw = if (modern) info?.targetCellWidth?.takeIf { it > 0 } ?: 4 else 4
        val ch = if (modern) info?.targetCellHeight?.takeIf { it > 0 } ?: 2 else 2
        val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).takeIf { it > 0 } ?: (cw * 85)
        val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0).takeIf { it > 0 } ?: (ch * 85)
        return w to h
    }

    fun update(ctx: Context, id: Int) {
        val cfg = WidgetPrefs.load(ctx, id)
        val (w, h) = size(ctx, id)
        val data = WidgetData.load(ctx, cfg)
        AppWidgetManager.getInstance(ctx).updateAppWidget(id, WidgetRenderer.build(ctx, id, cfg, data, w, h))
    }

    fun updateAll(ctx: Context) {
        val app = ctx.applicationContext
        Thread { ids(app).forEach { update(app, it) } }.start()
    }
}
