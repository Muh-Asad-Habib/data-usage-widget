package io.github.asadhabib.datausage.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import io.github.asadhabib.datausage.R
import io.github.asadhabib.datausage.data.Fmt
import io.github.asadhabib.datausage.data.Net
import io.github.asadhabib.datausage.data.Periods
import io.github.asadhabib.datausage.data.UsageSummary
import io.github.asadhabib.datausage.data.UsageRepository
import io.github.asadhabib.datausage.data.UsageAccess
import io.github.asadhabib.datausage.ui.MainActivity
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

val CHART_COLORS = intArrayOf(
    0xFF3D7BFF.toInt(), 0xFF3BBFDB.toInt(), 0xFF5BD37F.toInt(), 0xFFFFA24C.toInt(), 0xFFA06BFF.toInt(),
)
const val OTHER_COLOR = 0xFFA3AAB6.toInt()

class WidgetData(val summary: UsageSummary?, val periodText: String) {
    companion object {
        fun load(ctx: Context, cfg: WidgetConfig): WidgetData {
            val r = Periods.range(cfg.period)
            val text = Periods.describe(cfg.period, r)
            if (!UsageAccess.granted(ctx)) return WidgetData(null, text)
            return WidgetData(UsageRepository(ctx).summary(r), text)
        }
    }
}

object WidgetRenderer {
    const val ACTION_REFRESH = "io.github.asadhabib.datausage.REFRESH"

    private val rows = intArrayOf(R.id.row1, R.id.row2, R.id.row3, R.id.row4, R.id.row5)
    private val dots = intArrayOf(R.id.dot1, R.id.dot2, R.id.dot3, R.id.dot4, R.id.dot5)
    private val names = intArrayOf(R.id.name1, R.id.name2, R.id.name3, R.id.name4, R.id.name5)
    private val vals = intArrayOf(R.id.val1, R.id.val2, R.id.val3, R.id.val4, R.id.val5)

    fun build(ctx: Context, id: Int, cfg: WidgetConfig, data: WidgetData, wDp: Int, hDp: Int, interactive: Boolean = true): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_usage)
        val density = min(ctx.resources.displayMetrics.density, 2.5f)
        val text = cfg.textColor
        val sub = (text and 0x00FFFFFF) or (0xB3 shl 24)

        v.setImageViewBitmap(R.id.bg, background(ctx, id, cfg, wDp, hDp, density))

        val net = cfg.net
        val title = cfg.title.ifBlank {
            if (net == Net.ALL) data.periodText else "${net.label} · ${data.periodText}"
        }
        v.setTextViewText(R.id.title, title)
        v.setTextColor(R.id.title, sub)
        v.setTextColor(R.id.total, text)
        v.setTextColor(R.id.split, sub)

        val s = data.summary
        if (s == null) {
            v.setTextViewText(R.id.total, "—")
            v.setTextViewText(R.id.split, "Buka aplikasi untuk memberi izin akses penggunaan")
            v.setViewVisibility(R.id.split, View.VISIBLE)
            v.setViewVisibility(R.id.apps, View.GONE)
            v.setViewVisibility(R.id.donut, View.GONE)
        } else {
            val t = s.of(net)
            v.setTextViewText(R.id.total, Fmt.bytes(t.total))
            val parts = buildList {
                if (cfg.showSplit && net == Net.ALL) add("Wi-Fi ${Fmt.bytes(s.wifi.total)} · Data ${Fmt.bytes(s.mobile.total)}")
                if (cfg.showUpDown) add("↑ ${Fmt.bytes(t.tx)}  ↓ ${Fmt.bytes(t.rx)}")
            }
            v.setTextViewText(R.id.split, parts.joinToString("\n"))
            v.setViewVisibility(R.id.split, if (parts.isEmpty()) View.GONE else View.VISIBLE)

            val apps = s.appsBy(net)
            val used = 24 + 20 + 40 + 8 + parts.size * 17
            val fit = ((hDp - used) / 22).coerceAtLeast(0)
            val n = if (cfg.showApps) minOf(cfg.appCount, fit, apps.size, 5) else 0
            v.setViewVisibility(R.id.apps, if (n > 0) View.VISIBLE else View.GONE)
            for (i in 0 until 5) {
                if (i < n) {
                    v.setViewVisibility(rows[i], View.VISIBLE)
                    v.setInt(dots[i], "setColorFilter", CHART_COLORS[i])
                    v.setTextViewText(names[i], apps[i].label)
                    v.setTextViewText(vals[i], Fmt.bytes(apps[i].of(net).total))
                    v.setTextColor(names[i], text)
                    v.setTextColor(vals[i], text)
                } else v.setViewVisibility(rows[i], View.GONE)
            }

            if (cfg.showDonut && wDp >= 200) {
                val dp = minOf(hDp - 40, (wDp * 0.34f).toInt(), 120).coerceAtLeast(56)
                val size = (dp * density).toInt()
                val k = cfg.appCount.coerceIn(1, 5)
                val values = apps.take(k).map { it.of(net).total }
                v.setImageViewBitmap(R.id.donut, donut(size, values, t.total, cfg.textColor))
                v.setViewVisibility(R.id.donut, View.VISIBLE)
            } else v.setViewVisibility(R.id.donut, View.GONE)
        }

        v.setViewVisibility(R.id.refresh, if (cfg.showRefresh) View.VISIBLE else View.GONE)
        v.setInt(R.id.refresh_bg, "setColorFilter", (text and 0x00FFFFFF) or (0x1F shl 24))
        v.setInt(R.id.refresh_icon, "setColorFilter", text)

        if (interactive) {
            val open = PendingIntent.getActivity(
                ctx, id, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            v.setOnClickPendingIntent(R.id.widget_root, open)
            val refresh = PendingIntent.getBroadcast(
                ctx, id,
                Intent(ctx, UsageWidgetProvider::class.java).setAction(ACTION_REFRESH)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            v.setOnClickPendingIntent(R.id.refresh, refresh)
        }
        return v
    }

    private var imgKey: String? = null
    private var imgCache: Bitmap? = null

    private fun image(ctx: Context, id: Int): Bitmap? {
        val f = WidgetPrefs.imageFile(ctx, id)
        if (!f.exists()) return null
        val key = "${f.path}:${f.lastModified()}"
        synchronized(this) {
            if (key != imgKey) {
                imgCache = BitmapFactory.decodeFile(f.path)
                imgKey = key
            }
            return imgCache
        }
    }

    fun background(ctx: Context, id: Int, cfg: WidgetConfig, wDp: Int, hDp: Int, density: Float): Bitmap {
        val w = (wDp * density).toInt().coerceAtLeast(1)
        val h = (hDp * density).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val r = cfg.radius * density
        c.clipPath(Path().apply { addRoundRect(rect, r, r, Path.Direction.CW) })
        c.saveLayerAlpha(rect, cfg.opacity.coerceIn(0, 100) * 255 / 100)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        when (cfg.bgType) {
            BgType.SOLID -> { p.color = cfg.color1; c.drawRect(rect, p) }
            BgType.GRADIENT -> {
                val a = Math.toRadians(cfg.gradientAngle.toDouble())
                val cx = w / 2f; val cy = h / 2f
                val dx = (cos(a) * w / 2).toFloat(); val dy = (sin(a) * h / 2).toFloat()
                p.shader = LinearGradient(cx - dx, cy - dy, cx + dx, cy + dy, cfg.color1, cfg.color2, Shader.TileMode.CLAMP)
                c.drawRect(rect, p)
            }
            BgType.IMAGE -> {
                val img = image(ctx, id)
                if (img == null) { p.color = cfg.color1; c.drawRect(rect, p) } else {
                    val scale = maxOf(w.toFloat() / img.width, h.toFloat() / img.height)
                    val sw = (w / scale).toInt(); val sh = (h / scale).toInt()
                    val sx = (img.width - sw) / 2; val sy = (img.height - sh) / 2
                    c.drawBitmap(img, Rect(sx, sy, sx + sw, sy + sh), rect, p)
                }
            }
        }
        if (cfg.dim > 0) c.drawColor(Color.argb(cfg.dim * 255 / 100, 0, 0, 0))
        c.restore()
        return bmp
    }

    fun donut(size: Int, values: List<Long>, total: Long, textColor: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val stroke = size * 0.16f
        val oval = RectF(stroke / 2, stroke / 2, size - stroke / 2, size - stroke / 2)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke }
        if (total <= 0) {
            p.color = (textColor and 0x00FFFFFF) or (0x26 shl 24)
            c.drawArc(oval, 0f, 360f, false, p)
            return bmp
        }
        val segs = values.mapIndexed { i, v -> v to CHART_COLORS[i] }.toMutableList()
        val rest = total - values.sum()
        if (rest > 0) segs += rest to OTHER_COLOR
        val gap = if (segs.size > 1) 3f else 0f
        var start = -90f
        for ((v, col) in segs) {
            val sweep = 360f * v / total
            if (sweep > gap) {
                p.color = col
                c.drawArc(oval, start + gap / 2, sweep - gap, false, p)
            }
            start += sweep
        }
        return bmp
    }

    fun dpToPx(ctx: Context, dp: Int) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), ctx.resources.displayMetrics).toInt()
}
