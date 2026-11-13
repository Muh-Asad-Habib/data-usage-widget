package io.github.asadhabib.datausage.widget

import android.content.Context
import io.github.asadhabib.datausage.data.Net
import io.github.asadhabib.datausage.data.Period
import java.io.File

enum class BgType { SOLID, GRADIENT, IMAGE }

data class WidgetConfig(
    val title: String = "",
    val period: Period = Period.TODAY,
    val net: Net = Net.ALL,
    val showDonut: Boolean = true,
    val showApps: Boolean = true,
    val appCount: Int = 3,
    val showSplit: Boolean = false,
    val showUpDown: Boolean = false,
    val showRefresh: Boolean = true,
    val bgType: BgType = BgType.SOLID,
    val color1: Int = 0xFFE4E5EA.toInt(),
    val color2: Int = 0xFFB9C8FF.toInt(),
    val gradientAngle: Int = 135,
    val opacity: Int = 85,
    val dim: Int = 0,
    val radius: Int = 28,
    val textColor: Int = 0xFF15171A.toInt(),
)

object WidgetPrefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    fun imageFile(ctx: Context, id: Int) = File(ctx.filesDir, "widget_bg_$id.jpg")

    fun load(ctx: Context, id: Int): WidgetConfig {
        val p = sp(ctx)
        val k = "w${id}_"
        val d = WidgetConfig()
        if (!p.contains(k + "period")) return d
        return WidgetConfig(
            title = p.getString(k + "title", d.title) ?: "",
            period = runCatching { Period.valueOf(p.getString(k + "period", null)!!) }.getOrDefault(d.period),
            net = runCatching { Net.valueOf(p.getString(k + "net", null)!!) }.getOrDefault(d.net),
            showDonut = p.getBoolean(k + "donut", d.showDonut),
            showApps = p.getBoolean(k + "apps", d.showApps),
            appCount = p.getInt(k + "count", d.appCount),
            showSplit = p.getBoolean(k + "split", d.showSplit),
            showUpDown = p.getBoolean(k + "updown", d.showUpDown),
            showRefresh = p.getBoolean(k + "refresh", d.showRefresh),
            bgType = runCatching { BgType.valueOf(p.getString(k + "bg", null)!!) }.getOrDefault(d.bgType),
            color1 = p.getInt(k + "c1", d.color1),
            color2 = p.getInt(k + "c2", d.color2),
            gradientAngle = p.getInt(k + "angle", d.gradientAngle),
            opacity = p.getInt(k + "opacity", d.opacity),
            dim = p.getInt(k + "dim", d.dim),
            radius = p.getInt(k + "radius", d.radius),
            textColor = p.getInt(k + "text", d.textColor),
        )
    }

    fun save(ctx: Context, id: Int, c: WidgetConfig) {
        val k = "w${id}_"
        sp(ctx).edit()
            .putString(k + "title", c.title)
            .putString(k + "period", c.period.name)
            .putString(k + "net", c.net.name)
            .putBoolean(k + "donut", c.showDonut)
            .putBoolean(k + "apps", c.showApps)
            .putInt(k + "count", c.appCount)
            .putBoolean(k + "split", c.showSplit)
            .putBoolean(k + "updown", c.showUpDown)
            .putBoolean(k + "refresh", c.showRefresh)
            .putString(k + "bg", c.bgType.name)
            .putInt(k + "c1", c.color1)
            .putInt(k + "c2", c.color2)
            .putInt(k + "angle", c.gradientAngle)
            .putInt(k + "opacity", c.opacity)
            .putInt(k + "dim", c.dim)
            .putInt(k + "radius", c.radius)
            .putInt(k + "text", c.textColor)
            .apply()
    }

    fun delete(ctx: Context, id: Int) {
        val k = "w${id}_"
        val p = sp(ctx)
        p.edit().apply { p.all.keys.filter { it.startsWith(k) }.forEach { remove(it) } }.apply()
        imageFile(ctx, id).delete()
    }
}
