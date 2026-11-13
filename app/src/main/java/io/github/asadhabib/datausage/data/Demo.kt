package io.github.asadhabib.datausage.data

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import io.github.asadhabib.datausage.BuildConfig
import java.io.File
import java.time.Instant
import java.time.LocalDate

/**
 * Sample data for store screenshots. Only available in debug builds and only when the file
 * `files/demo` exists (`adb shell run-as <pkg> touch files/demo`). Release builds strip it.
 */
object Demo {
    private var cached: Boolean? = null

    fun on(ctx: Context): Boolean {
        if (!BuildConfig.DEBUG) return false
        return cached ?: File(ctx.applicationContext.filesDir, "demo").exists().also { cached = it }
    }

    private class App(val label: String, val pkg: String, val wifi: Double, val mobile: Double, val fg: Double)

    private val apps = listOf(
        App("YouTube", "com.google.android.youtube", .34, .12, .93),
        App("TikTok", "com.zhiliaoapp.musically", .18, .27, .96),
        App("Instagram", "com.instagram.android", .13, .21, .90),
        App("Netflix", "com.netflix.mediaclient", .12, .0, .98),
        App("WhatsApp", "com.whatsapp", .05, .14, .62),
        App("Spotify", "com.spotify.music", .04, .09, .71),
        App("Chrome", "com.android.chrome", .05, .07, .97),
        App("Google Maps", "com.google.android.apps.maps", .01, .05, .88),
        App("Google Photos", "com.google.android.apps.photos", .05, .0, .08),
        App("Gmail", "com.google.android.gm", .01, .02, .35),
        App("Google Play Store", "com.android.vending", .02, .01, .40),
    )

    // Share of traffic per hour of day (sums to ~1), evenings are busiest.
    private val hours = doubleArrayOf(
        .012, .006, .003, .002, .002, .004, .014, .030, .042, .040, .038, .042,
        .050, .046, .040, .042, .048, .056, .068, .082, .096, .104, .094, .060,
    )
    // Mobile data is used away from home: mornings, lunch and commute.
    private val mobileHours = doubleArrayOf(
        .004, .002, .001, .001, .001, .006, .040, .095, .090, .050, .045, .060,
        .095, .070, .045, .045, .080, .110, .085, .030, .015, .012, .010, .008,
    )

    private fun noise(day: Long, salt: Int): Double {
        var x = day * 2654435761L + salt * 40503L
        x = (x xor (x ushr 13)) * 1274126177L
        return ((x ushr 8) and 0xFFFF).toDouble() / 0xFFFF
    }

    private fun daily(day: LocalDate, type: Int): Double {
        val d = day.toEpochDay()
        val weekend = day.dayOfWeek.value >= 6
        return if (type == ConnectivityManager.TYPE_WIFI) (1.4 + 1.6 * noise(d, 1) + if (weekend) 1.1 else 0.0) * 1e9
        else (0.28 + 0.55 * noise(d, 2) + if (weekend) -0.12 else 0.0) * 1e9
    }

    fun bytes(type: Int, start: Long, end: Long): Long {
        val stop = minOf(end, System.currentTimeMillis())
        var t = start
        var sum = 0.0
        while (t < stop) {
            val z = Instant.ofEpochMilli(t).atZone(Periods.zone)
            val hourStart = z.withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli()
            val hourEnd = hourStart + 3_600_000L
            val part = (minOf(hourEnd, stop) - t) / 3_600_000.0
            val w = if (type == ConnectivityManager.TYPE_WIFI) hours[z.hour] else mobileHours[z.hour]
            sum += daily(z.toLocalDate(), type) * w * part
            t = hourEnd
        }
        return sum.toLong()
    }

    fun traffic(type: Int, start: Long, end: Long): Traffic {
        val b = bytes(type, start, end)
        val tx = (b * .055).toLong()
        return Traffic(b - tx, tx)
    }

    private fun split(t: Traffic, share: Double) = Traffic((t.rx * share).toLong(), (t.tx * share).toLong())

    fun summary(r: Range): UsageSummary {
        val wifi = traffic(ConnectivityManager.TYPE_WIFI, r.start, r.end)
        val mobile = traffic(ConnectivityManager.TYPE_MOBILE, r.start, r.end)
        val list = apps.mapIndexed { i, a ->
            val w = split(wifi, a.wifi)
            val m = split(mobile, a.mobile)
            val all = w + m
            AppUsage(UID + i, a.label, a.pkg, w, m, split(all, a.fg), split(all, 1 - a.fg))
        }
        return UsageSummary(wifi, mobile, list.filter { it.all.total > 0 }.sortedByDescending { it.all.total })
    }

    fun dailyFor(uid: Int, days: List<DayUsage>): List<DayUsage> {
        val a = apps.getOrNull(uid - UID) ?: return days.map { DayUsage(it.start, 0, 0) }
        return days.map { DayUsage(it.start, (it.wifi * a.wifi).toLong(), (it.mobile * a.mobile).toLong()) }
    }

    fun ssids(r: Range): List<SsidUsage> {
        val wifi = bytes(ConnectivityManager.TYPE_WIFI, r.start, r.end)
        return listOf("Home_5G" to .64, "Office-Staff" to .21, "Coffee Shop Free WiFi" to .10, "Airport Free WiFi" to .05)
            .map { SsidUsage(it.first, (wifi * it.second).toLong()) }
            .filter { it.bytes > 0 }
    }

    fun since(): Long = Periods.startOf(LocalDate.now(Periods.zone).minusDays(120))

    /** Icons of sample apps that are not installed; kept in the git-ignored debug assets. */
    fun icon(ctx: Context, pkg: String?): Drawable? = try {
        ctx.assets.open("demo_icons/$pkg.png").use { BitmapDrawable(ctx.resources, BitmapFactory.decodeStream(it)) }
    } catch (_: Exception) { null }
    private const val UID = 990_000
}
